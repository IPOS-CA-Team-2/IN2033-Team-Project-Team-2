package exception;

// thrown by StockService when a stock operation fails
// carries a reason so the ui can show the right message
public class StockException extends Exception {

    public enum Reason {
        ITEM_NOT_FOUND,      // no item with that id in the db
        INVALID_QUANTITY,    // quantity arg was zero or negative
        INSUFFICIENT_STOCK,  // trying to decrease more than whats available
        NULL_ITEM,           // null stock item passed to save
        INVALID_ITEM_ID      // item id was zero or negative
    }

    private final Reason reason;

    public StockException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
