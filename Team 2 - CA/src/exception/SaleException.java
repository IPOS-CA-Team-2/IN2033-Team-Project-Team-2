package exception;

// thrown by SaleService when a sale cannot be completed
public class SaleException extends Exception {

    public enum Reason {
        INSUFFICIENT_STOCK,   // not enough stock to fulfil one or more lines
        ITEM_NOT_FOUND,       // a line references an item that doesnt exist
        EMPTY_SALE,           // no items in the sale
        INVALID_PAYMENT,      // card details missing for card payment
        SAVE_FAILED           // db error when persisting the sale
    }

    private final Reason reason;

    public SaleException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
