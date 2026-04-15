package integration;

import model.CardDetails;
import model.OnlineSale;
import model.StockItem;

// interface for CA's outbound PU communication
// backed by MockPuAdapter in demo mode and HttpPuAdapter in live HTTP mode
public interface IPuStockUpdater {

    // apply an online sale: deduct quantities from ca stock
    // returns true if all items were fully applied, false if any were skipped (insufficient stock)
    boolean applyOnlineSale(OnlineSale sale);

    // send card details to the PU payment processor for clearance
    // returns approved/declined status and a transaction reference if approved
    CardClearanceResult clearCardPayment(CardDetails card, double amount);

    // notify PU that a stock item has been deleted from CA
    // PU removes the product from its catalogue so it no longer appears online
    void notifyProductDeleted(int caItemId);

    // notify PU that a stock item has been added or edited in CA
    // PU upserts the product in its catalogue with the updated name and price
    void notifyStockUpdated(StockItem item);

    // notify PU that a CA online order's status has changed
    // PU updates the member's "My Orders" view accordingly
    void notifyOrderStatusUpdate(String puOrderId, String caStatus);
}
