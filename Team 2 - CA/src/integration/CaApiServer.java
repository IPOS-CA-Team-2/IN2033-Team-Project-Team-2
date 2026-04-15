package integration;

import app.AppContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.OnlineSale;
import model.OnlineSaleItem;
import model.OrderStatus;
import model.StockItem;
import service.WholesaleOrderService;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

// embedded http server on port 8081 — listens for inbound pushes from sa and pu
// uses com.sun.net.httpserver which is built into the jdk — no extra dependencies
// runs on a single daemon thread so it doesn't prevent the jvm from exiting normally
public class CaApiServer {

    private final WholesaleOrderService orderService;
    private final IPuStockUpdater       puAdapter;
    private final Runnable              onOrderUpdated; // called after sa status push
    private final Runnable              onStockChanged; // called after pu sale push
    private final Gson                  gson;
    private HttpServer                  server;

    public CaApiServer(WholesaleOrderService orderService,
                       IPuStockUpdater       puAdapter,
                       Runnable              onOrderUpdated,
                       Runnable              onStockChanged) {
        this.orderService   = orderService;
        this.puAdapter      = puAdapter;
        this.onOrderUpdated = onOrderUpdated;
        this.onStockChanged = onStockChanged;
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();
    }

    // bind to port 8081 and start accepting requests
    // if port is already in use, logs a warning and continues in degraded mode
    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(8081), 0);
            server.createContext("/order-update", this::handleOrderUpdate);
            server.createContext("/online-sale",  this::handleOnlineSale);
            server.createContext("/stock",        this::handleGetStock);
            server.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ca-api-server");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            System.out.println("[CaApiServer] Listening on port 8081 (/order-update, /online-sale, /stock)");
        } catch (BindException e) {
            System.err.println("[CaApiServer] Port 8081 already in use — running without inbound server");
        } catch (IOException e) {
            System.err.println("[CaApiServer] Failed to start: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    // POST /order-update — SA calls this when an order status changes
    // body for most statuses: { "orderId": <SA_ORDER_ID>, "status": "ACCEPTED" }
    // body for DISPATCHED also includes: courierName, courierReference, dispatchDate, expectedDeliveryDate
    private void handleOrderUpdate(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            JsonObject  json      = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            int         saId      = json.get("orderId").getAsInt();
            String      statusStr = json.get("status").getAsString();

            // parse optional shipping fields — only present when status = DISPATCHED
            String    courierName  = json.has("courierName")          ? json.get("courierName").getAsString()          : null;
            String    courierRef   = json.has("courierReference")      ? json.get("courierReference").getAsString()      : null;
            LocalDate dispatchDate = json.has("dispatchDate")          ? LocalDate.parse(json.get("dispatchDate").getAsString())         : null;
            LocalDate expectedDel  = json.has("expectedDeliveryDate")  ? LocalDate.parse(json.get("expectedDeliveryDate").getAsString())  : null;

            OrderStatus status = mapSaStatus(statusStr);
            if (status != null) {
                orderService.receiveStatusUpdate(saId, status, courierName, courierRef, dispatchDate, expectedDel);
                SwingUtilities.invokeLater(onOrderUpdated);
            }

            sendJson(exchange, 200, "{\"ok\":true}");
        } catch (Exception e) {
            System.err.println("[CaApiServer] /order-update error: " + e.getMessage());
            sendJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // POST /online-sale — PU calls this when a member completes an order
    // expected body: { "puOrderId":"PU-1", "receivedDate":"2026-04-11",
    //                  "customerEmail":"x@x.com", "items":[{"itemId":1,"quantity":5}] }
    private void handleOnlineSale(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            JsonObject json           = JsonParser.parseString(readBody(exchange)).getAsJsonObject();
            String     puOrderId      = json.get("puOrderId").getAsString();
            LocalDate  received       = LocalDate.parse(json.get("receivedDate").getAsString());
            String     email          = json.has("customerEmail")   ? json.get("customerEmail").getAsString()   : null;
            String     deliveryAddress = json.has("deliveryAddress") ? json.get("deliveryAddress").getAsString() : "";

            List<OnlineSaleItem> items = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("items")) {
                JsonObject obj = el.getAsJsonObject();
                items.add(new OnlineSaleItem(obj.get("itemId").getAsInt(), obj.get("quantity").getAsInt()));
            }

            OnlineSale sale         = new OnlineSale(puOrderId, received, email, deliveryAddress, items);
            boolean    fullyApplied = puAdapter.applyOnlineSale(sale);

            SwingUtilities.invokeLater(onStockChanged);
            sendJson(exchange, 200, "{\"accepted\":true,\"fullyApplied\":" + fullyApplied + "}");
        } catch (Exception e) {
            System.err.println("[CaApiServer] /online-sale error: " + e.getMessage());
            sendJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // GET /stock — PU calls this on catalogue load to get CA's full product list with prices
    // returns a JSON array: [{"id":1,"name":"...","quantity":5,"price":2.00,"itemCode":"100 00001"},...]
    private void handleGetStock(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            java.util.List<StockItem> items = AppContext.getStockService().getAllStock();
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                StockItem item = items.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"id\":").append(item.getItemId())
                  .append(",\"name\":\"").append(item.getName().replace("\"", "\\\"")).append("\"")
                  .append(",\"quantity\":").append(item.getQuantity())
                  .append(",\"price\":").append(String.format("%.2f", item.getPriceIncVat()))
                  .append(",\"itemCode\":\"").append(item.getItemCode().replace("\"", "\\\"")).append("\"")
                  .append("}");
            }
            sb.append("]");
            sendJson(exchange, 200, sb.toString());
        } catch (Exception e) {
            System.err.println("[CaApiServer] /stock error: " + e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // maps sa status strings to ca's OrderStatus enum
    // sa sends ACCEPTED (not CONFIRMED) for newly placed orders — both mapped for safety
    // sa sends PROCESSING for picking/packing — maps to BEING_PROCESSED on ca side
    private OrderStatus mapSaStatus(String saStatus) {
        return switch (saStatus.toUpperCase()) {
            case "PENDING"    -> OrderStatus.PENDING;
            case "CONFIRMED"  -> OrderStatus.ACCEPTED;       // legacy — kept for safety
            case "ACCEPTED"   -> OrderStatus.ACCEPTED;       // sa's actual status for new orders
            case "PROCESSING" -> OrderStatus.BEING_PROCESSED; // sa picking/packing
            case "DISPATCHED" -> OrderStatus.DISPATCHED;
            case "DELIVERED"  -> OrderStatus.DELIVERED;
            case "CANCELLED"  -> OrderStatus.CANCELLED;
            default -> {
                System.err.println("[CaApiServer] Unknown SA status received: " + saStatus);
                yield null;
            }
        };
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void sendJson(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
