package model;

// card payment details captured at point of sale per brief spec
// stores card type, first 4 and last 4 digits, and expiry date
public class CardDetails {

    private final String cardType;        // e.g. "Visa", "Mastercard"
    private final String firstFourDigits;
    private final String lastFourDigits;
    private final String expiryDate;      // e.g. "12/27"

    public CardDetails(String cardType, String firstFourDigits, String lastFourDigits, String expiryDate) {
        if (cardType == null || cardType.isBlank()) throw new IllegalArgumentException("card type cannot be blank");
        if (firstFourDigits == null || firstFourDigits.length() != 4) throw new IllegalArgumentException("first four digits must be exactly 4 characters");
        if (lastFourDigits == null || lastFourDigits.length() != 4) throw new IllegalArgumentException("last four digits must be exactly 4 characters");
        if (expiryDate == null || expiryDate.isBlank()) throw new IllegalArgumentException("expiry date cannot be blank");

        this.cardType = cardType;
        this.firstFourDigits = firstFourDigits;
        this.lastFourDigits = lastFourDigits;
        this.expiryDate = expiryDate;
    }

    public String getCardType()         { return cardType; }
    public String getFirstFourDigits()  { return firstFourDigits; }
    public String getLastFourDigits()   { return lastFourDigits; }
    public String getExpiryDate()       { return expiryDate; }

    // masked display string e.g. "Visa **** **** **** 5678"
    public String getMasked() {
        return cardType + " " + firstFourDigits + "** **** **" + lastFourDigits;
    }

    @Override
    public String toString() {
        return "CardDetails{type='" + cardType + "', first=" + firstFourDigits
                + ", last=" + lastFourDigits + ", expiry=" + expiryDate + "}";
    }
}
