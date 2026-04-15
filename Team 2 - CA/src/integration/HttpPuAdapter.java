package integration;

import model.CardDetails;
import model.OnlineSale;
import model.StockItem;
import service.OnlineSaleService;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

// live HTTP implementation of IPuStockUpdater — used when CA is started with -Dipos.http=true
// all calls are non-fatal: if PU is offline the CA operation completes locally
public class HttpPuAdapter implements IPuStockUpdater {

    private static final String PU_BASE = "http://localhost:8082";

    private final OnlineSaleService onlineSaleService;

    public HttpPuAdapter(OnlineSaleService onlineSaleService) {
        this.onlineSaleService = onlineSaleService;
    }

    @Override
    public boolean applyOnlineSale(OnlineSale sale) {
        return onlineSaleService.processOnlineSale(sale);
    }

    @Override
    public CardClearanceResult clearCardPayment(CardDetails card, double amount) {
        try {
            String body = "{"
                + "\"cardholderName\":\"\","
                + "\"firstFourDigits\":\"" + card.getFirstFourDigits() + "\","
                + "\"lastFourDigits\":\""  + card.getLastFourDigits()  + "\","
                + "\"expiryDate\":\""      + card.getExpiryDate()      + "\","
                + "\"amount\":"            + String.format("%.2f", amount)
                + "}";

            String response = post(PU_BASE + "/api/payments/ca-clearance", body);

            boolean approved = response.contains("\"approved\":true");
            String txRef = null;
            if (approved) {
                int start = response.indexOf("\"transactionRef\":\"");
                if (start >= 0) {
                    start += "\"transactionRef\":\"".length();
                    int end = response.indexOf("\"", start);
                    if (end > start) txRef = response.substring(start, end);
                }
            }
            String message = approved ? "Payment approved" : "Card declined by payment processor";
            System.out.println("[CA->PU] Card clearance: " + (approved ? "APPROVED" : "DECLINED")
                    + " £" + String.format("%.2f", amount));
            return new CardClearanceResult(approved, txRef, message);

        } catch (Exception e) {
            System.err.println("[CA->PU] /api/payments/ca-clearance failed (non-fatal): " + e.getMessage());
            if ("0000".equals(card.getFirstFourDigits())) {
                return new CardClearanceResult(false, null, "Card declined by payment processor");
            }
            return new CardClearanceResult(true, "PU-OFFLINE-FALLBACK", "Payment approved (PU offline)");
        }
    }

    @Override
    public void notifyProductDeleted(int caItemId) {
        try {
            delete(PU_BASE + "/api/products/ca/" + caItemId);
            System.out.println("[CA->PU] Deletion notified for caItemId=" + caItemId);
        } catch (Exception e) {
            System.err.println("[CA->PU] /api/products/ca delete failed (non-fatal): " + e.getMessage());
        }
    }

    @Override
    public void notifyStockUpdated(StockItem item) {
        try {
            String body = "{"
                + "\"caItemId\":"  + item.getItemId()  + ","
                + "\"name\":\""    + item.getName().replace("\"", "\\\"") + "\","
                + "\"price\":"     + String.format("%.2f", item.getPriceIncVat())
                + "}";
            post(PU_BASE + "/api/products/ca-sync", body);
            System.out.println("[CA->PU] Stock sync notified for caItemId=" + item.getItemId());
        } catch (Exception e) {
            System.err.println("[CA->PU] /api/products/ca-sync failed (non-fatal): " + e.getMessage());
        }
    }

    @Override
    public void notifyOrderStatusUpdate(String puOrderId, String caStatus) {
        try {
            // extract numeric id from "PU-42" -> "42"
            String numericId = puOrderId.startsWith("PU-") ? puOrderId.substring(3) : puOrderId;

            String puStatus = switch (caStatus) {
                case "DISPATCHED" -> "DISPATCHED";
                case "DELIVERED"  -> "DELIVERED";
                default           -> "RECEIVED";
            };

            String body = "{\"status\":\"" + puStatus + "\"}";
            post(PU_BASE + "/api/orders/" + numericId + "/status", body);
            System.out.println("[CA->PU] Order status update: " + puOrderId + " -> " + puStatus);
        } catch (Exception e) {
            System.err.println("[CA->PU] order status update failed (non-fatal): " + e.getMessage());
        }
    }

    private String post(String urlStr, String jsonBody) throws Exception {
        HttpURLConnection conn = open(urlStr, "POST");
        byte[] bytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }
        return readResponse(conn);
    }

    private void delete(String urlStr) throws Exception {
        HttpURLConnection conn = open(urlStr, "DELETE");
        conn.getResponseCode();
        conn.disconnect();
    }

    private HttpURLConnection open(String urlStr, String method) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(!method.equals("DELETE") && !method.equals("GET"));
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        return conn;
    }

    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        var stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8)) {
            return sc.useDelimiter("\\A").hasNext() ? sc.next() : "";
        } finally {
            conn.disconnect();
        }
    }
}
