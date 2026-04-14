package integration;

public class CardClearanceResult {

    private final boolean approved;
    private final String transactionRef;
    private final String message;

    public CardClearanceResult(boolean approved, String transactionRef, String message) {
        this.approved = approved;
        this.transactionRef = transactionRef;
        this.message = message;
    }

    public boolean isApproved() { return approved; }
    public String getTransactionRef() { return transactionRef; }
    public String getMessage() { return message; }
}
