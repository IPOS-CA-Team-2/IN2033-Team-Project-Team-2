package integration;

import model.CardDetails;
import model.OnlineSale;
import model.OnlineSaleItem;
import model.StockItem;
import service.OnlineSaleService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// mock implementation of IPuStockUpdater
// simulates ipos-pu notifying ipos-ca about a completed online sale
// also exposes simulateSale() for demo use without a live pu connection
// to integrate with real pu: call applyOnlineSale() from pu's system or wrap in an http endpoint
public class MockPuAdapter implements IPuStockUpdater {

    private final OnlineSaleService onlineSaleService;

    public MockPuAdapter(OnlineSaleService onlineSaleService) {
        this.onlineSaleService = onlineSaleService;
    }

    @Override
    public boolean applyOnlineSale(OnlineSale sale) {
        // processOnlineSale handles both stock deduction and saving to the database
        return onlineSaleService.processOnlineSale(sale);
    }

    // convenience method for demo, generates a fake PU order id and wraps the items
    public boolean simulateSale(List<OnlineSaleItem> items, String customerEmail) {
        String fakeOrderId = "PU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OnlineSale sale = new OnlineSale(fakeOrderId, LocalDate.now(), customerEmail, items);
        return applyOnlineSale(sale);
    }

    @Override
    public CardClearanceResult clearCardPayment(CardDetails card, double amount) {
        // simulate PU payment processor clearance
        // card starting with "0000" simulates a declined card for demo purposes
        if ("0000".equals(card.getFirstFourDigits())) {
            return new CardClearanceResult(false, null, "Card declined by payment processor");
        }
        String txRef = "PU-TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new CardClearanceResult(true, txRef, "Payment approved");
    }

    @Override
    public void notifyProductDeleted(int caItemId) {
        System.err.println("[MockPuAdapter] notifyProductDeleted caItemId=" + caItemId + " (no-op in mock mode)");
    }

    @Override
    public void notifyStockUpdated(StockItem item) {
        System.err.println("[MockPuAdapter] notifyStockUpdated caItemId=" + item.getItemId() + " (no-op in mock mode)");
    }

    @Override
    public void notifyOrderStatusUpdate(String puOrderId, String caStatus) {
        System.err.println("[MockPuAdapter] notifyOrderStatusUpdate " + puOrderId + " -> " + caStatus + " (no-op in mock mode)");
    }

}
