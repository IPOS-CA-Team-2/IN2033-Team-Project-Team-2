package exception;

// thrown by LoginService when authentication fails
// carries a reason so the UI can show the right message
public class AuthException extends Exception {

    public enum Reason {
        INVALID_CREDENTIALS,   // username not found OR wrong password
        ACCOUNT_SUSPENDED,     // user exists but account is suspended
        BLANK_INPUT            // username or password was null/blank
    }

    private final Reason reason;

    public AuthException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
