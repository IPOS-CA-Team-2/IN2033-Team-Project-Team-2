package integration;

import model.CardDetails;
import model.OnlineSale;

// the interface ipos-pu uses to notify ipos-ca when an online sale completes
// ca exposes this so pu can trigger stock deductions
// currently backed by MockPuAdapter
// when pu team is ready: they call applyOnlineSale() directly, or we wrap it in an http listener
public interface IPuStockUpdater {

    // apply an online sale: deduct quantities from ca stock
    // returns true if all items were fully applied, false if any were skipped (insufficient stock)
    boolean applyOnlineSale(OnlineSale sale);

    // send card details to the PU payment processor for clearance
    // returns approved/declined status and a transaction reference if approved
    CardClearanceResult clearCardPayment(CardDetails card, double amount);
}