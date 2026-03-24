package model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

// represents a complete sale transaction
// customerId of 0 means a walk-in occasional customer with no account
public class Sale {

    private final int saleId;
    private final int customerId;
    private final List<SaleLine> lines;
    private final LocalDateTime saleDate;
    private final double discountPercent;   // e.g. 0.10 for 10% off
    private final PaymentMethod paymentMethod;
    private final CardDetails cardDetails;  // null for cash payments

    // constructor for card payments
    public Sale(int saleId, int customerId, List<SaleLine> lines,
                LocalDateTime saleDate, double discountPercent,
                PaymentMethod paymentMethod, CardDetails cardDetails) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("sale must have at least one line");
        if (saleDate == null) throw new IllegalArgumentException("sale date cannot be null");
        if (discountPercent < 0 || discountPercent > 1) throw new IllegalArgumentException("discount must be between 0 and 1");
        if (paymentMethod == null) throw new IllegalArgumentException("payment method cannot be null");
        if (paymentMethod != PaymentMethod.CASH && cardDetails == null)
            throw new IllegalArgumentException("card details required for card payments");

        this.saleId = saleId;
        this.customerId = customerId;
        this.lines = Collections.unmodifiableList(lines);
        this.saleDate = saleDate;
        this.discountPercent = discountPercent;
        this.paymentMethod = paymentMethod;
        this.cardDetails = cardDetails;
    }

    // constructor for cash payments — no card details needed
    public Sale(int saleId, int customerId, List<SaleLine> lines,
                LocalDateTime saleDate, double discountPercent) {
        this(saleId, customerId, lines, saleDate, discountPercent, PaymentMethod.CASH, null);
    }

    // sum of all line totals before discount and vat
    public double getSubtotal() {
        return lines.stream().mapToDouble(SaleLine::getLineTotal).sum();
    }

    // pound value knocked off
    public double getDiscountAmount() {
        return getSubtotal() * discountPercent;
    }

    // total after discount, before vat
    public double getTotal() {
        return getSubtotal() - getDiscountAmount();
    }

    // total including vat across all lines
    public double getTotalIncVat() {
        double subtotalIncVat = lines.stream().mapToDouble(SaleLine::getLineTotalIncVat).sum();
        return subtotalIncVat - (subtotalIncVat * discountPercent);
    }

    public int getSaleId()                  { return saleId; }
    public int getCustomerId()              { return customerId; }
    public List<SaleLine> getLines()        { return lines; }
    public LocalDateTime getSaleDate()      { return saleDate; }
    public double getDiscountPercent()      { return discountPercent; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public CardDetails getCardDetails()     { return cardDetails; }

    @Override
    public String toString() {
        return "Sale{saleId=" + saleId + ", customerId=" + customerId
                + ", lines=" + lines.size() + ", total=" + getTotal()
                + ", method='" + paymentMethod + "', date=" + saleDate + "}";
    }
}
