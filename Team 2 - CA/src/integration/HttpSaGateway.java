package integration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;
import repository.WholesaleOrderRepositoryImpl;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// real HTTP implementation of ISaGateway — talks to the live IPOS-SA Spring Boot backend
// swaps in for MockSaGateway when running with -Dipos.http=true
// all local order tracking still goes through sqlite so the ca ui works even if sa is offline
public class HttpSaGateway implements ISaGateway {

    private static final String SA_BASE     = "http://localhost:8080";
    private static final String CA_USERNAME = "ca_merchant";
    private static final String CA_PASSWORD = "ca_pass";

    private final HttpClient                  httpClient;
    private final Gson                        gson;
    private final WholesaleOrderRepositoryImpl repo;
    private boolean                           loggedIn = false;

    public HttpSaGateway() {
        // CookieManager stores JSESSIONID automatically after login
        // subsequent requests include the session cookie without any extra work
        CookieManager cookieManager = new CookieManager();
        this.httpClient = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
        this.repo = new WholesaleOrderRepositoryImpl();
    }

    // authenticate with sa using the ca_merchant account seeded in sa's DataBootstrap
    // must be called once before any other method — Main.main() calls this on startup
    public boolean login() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("username", CA_USERNAME);
            body.addProperty("password", CA_PASSWORD);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SA_BASE + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            loggedIn = resp.statusCode() == 200;

            if (loggedIn) System.out.println("[HttpSaGateway] Logged in to SA successfully");
            else          System.err.println("[HttpSaGateway] SA login failed — status " + resp.statusCode());

            return loggedIn;
        } catch (Exception e) {
            System.err.println("[HttpSaGateway] SA login error: " + e.getMessage());
            return false;
        }
    }

    // submit a wholesale order to sa and store it locally
    // if sa is unreachable, the local record is still saved for audit purposes
    @Override
    public WholesaleOrder submitOrder(List<OrderLine> lines) {
        // save locally first so we always have a record
        WholesaleOrder localOrder = new WholesaleOrder(lines);
        int localId = repo.save(localOrder);
        if (localId == -1) return null;

        WholesaleOrder saved = repo.findById(localId);
        if (saved == null) return null;

        if (!loggedIn) {
            System.err.println("[HttpSaGateway] Not logged in — order saved locally only");
            return saved;
        }

        // build sa request body:
        // merchantId = 0 is fine because sa enforces ORD-US1 and uses the authenticated user's id
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("merchantId", 0);

        JsonArray items = new JsonArray();
        for (OrderLine line : lines) {
            JsonObject item = new JsonObject();
            // productId matches itemId by agreement with sa team (ids 1–5 are the same items)
            item.addProperty("productId", line.getItemId());
            item.addProperty("quantity", line.getQuantity());
            items.add(item);
        }
        requestBody.add("items", items);

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SA_BASE + "/api/orders"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() == 200) {
                // extract sa's assigned order id and store it against our local record
                JsonObject saOrder = JsonParser.parseString(resp.body()).getAsJsonObject();
                int saOrderId = saOrder.get("id").getAsInt();
                repo.updateSaOrderId(localId, saOrderId);
                saved.setSaOrderId(saOrderId);
                System.out.println("[HttpSaGateway] Order submitted to SA, SA id=" + saOrderId);
            } else {
                System.err.println("[HttpSaGateway] SA rejected order — " + resp.statusCode() + ": " + resp.body());
            }
        } catch (Exception e) {
            System.err.println("[HttpSaGateway] SA submission failed (saved locally): " + e.getMessage());
        }

        return saved;
    }

    // load from local sqlite — source of truth for the ca side
    @Override
    public WholesaleOrder getOrderById(int orderId) {
        return repo.findById(orderId);
    }

    // load from local sqlite — newest first
    @Override
    public List<WholesaleOrder> getOrderHistory() {
        return repo.findAll();
    }

    // sa pushes status updates inbound to CaApiServer — outbound calls from ca are not needed
    // WholesaleOrderService.receiveStatusUpdate() handles the inbound push instead
    @Override
    public boolean updateOrderStatus(int orderId, OrderStatus status,
                                     String courier, String courierRef,
                                     LocalDate dispatchDate, LocalDate expectedDelivery) {
        // still update locally so mock-mode simulate buttons work in demo
        return repo.updateStatus(orderId, status, courier, courierRef, dispatchDate, expectedDelivery);
    }

    // GET /api/invoices/by-order/{saOrderId} — fetch invoice for a specific SA order
    // returns a map of invoice fields, or null if SA is unreachable / order has no invoice
    @Override
    public Map<String, Object> getInvoiceByOrderId(int saOrderId) {
        if (!loggedIn) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SA_BASE + "/api/invoices/by-order/" + saOrderId))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("[HttpSaGateway] Invoice fetch failed — " + resp.statusCode() + ": " + resp.body());
                return null;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            Map<String, Object> result = new HashMap<>();
            result.put("invoiceNumber",        json.has("invoiceNumber")        ? json.get("invoiceNumber").getAsString()        : "—");
            result.put("issuedAt",             json.has("issuedAt")             ? json.get("issuedAt").getAsString()             : "—");
            result.put("dueDate",              json.has("dueDate")              ? json.get("dueDate").getAsString()              : "—");
            result.put("grossTotal",           json.has("grossTotal")           ? json.get("grossTotal").getAsDouble()           : 0.0);
            result.put("fixedDiscountAmount",  json.has("fixedDiscountAmount")  ? json.get("fixedDiscountAmount").getAsDouble()  : 0.0);
            result.put("flexibleCreditApplied",json.has("flexibleCreditApplied")? json.get("flexibleCreditApplied").getAsDouble(): 0.0);
            result.put("totalDue",             json.has("totalDue")             ? json.get("totalDue").getAsDouble()             : 0.0);

            // parse line items
            List<Map<String, Object>> lines = new ArrayList<>();
            if (json.has("lines") && json.get("lines").isJsonArray()) {
                for (JsonElement el : json.getAsJsonArray("lines")) {
                    JsonObject line = el.getAsJsonObject();
                    Map<String, Object> lineMap = new HashMap<>();
                    lineMap.put("description", line.has("description") ? line.get("description").getAsString() : "");
                    lineMap.put("quantity",    line.has("quantity")    ? line.get("quantity").getAsInt()    : 0);
                    lineMap.put("unitPrice",   line.has("unitPrice")   ? line.get("unitPrice").getAsDouble()  : 0.0);
                    lineMap.put("lineTotal",   line.has("lineTotal")   ? line.get("lineTotal").getAsDouble()  : 0.0);
                    lines.add(lineMap);
                }
            }
            result.put("lines", lines);
            return result;

        } catch (Exception e) {
            System.err.println("[HttpSaGateway] Invoice fetch error (non-fatal): " + e.getMessage());
            return null;
        }
    }

    // GET /api/merchant-financials/balance — query outstanding balance for ca_merchant
    // returns a map with outstandingTotal, currency, oldestUnpaidDueDate, daysElapsedSinceDue
    @Override
    public Map<String, Object> getOutstandingBalance() {
        if (!loggedIn) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SA_BASE + "/api/merchant-financials/balance"))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                System.err.println("[HttpSaGateway] Balance fetch failed — " + resp.statusCode() + ": " + resp.body());
                return null;
            }

            JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
            Map<String, Object> result = new HashMap<>();
            result.put("outstandingTotal",     json.has("outstandingTotal")     ? json.get("outstandingTotal").getAsDouble()  : 0.0);
            result.put("currency",             json.has("currency")             ? json.get("currency").getAsString()          : "GBP");
            result.put("oldestUnpaidDueDate",  json.has("oldestUnpaidDueDate") && !json.get("oldestUnpaidDueDate").isJsonNull()
                                                                                 ? json.get("oldestUnpaidDueDate").getAsString() : null);
            result.put("daysElapsedSinceDue",  json.has("daysElapsedSinceDue") ? json.get("daysElapsedSinceDue").getAsLong()  : 0L);
            return result;

        } catch (Exception e) {
            System.err.println("[HttpSaGateway] Balance fetch error (non-fatal): " + e.getMessage());
            return null;
        }
    }
}
