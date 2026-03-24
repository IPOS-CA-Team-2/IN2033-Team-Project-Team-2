package model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

// represents a complete sale transaction
// customerId of 0 means a walk-in cash customer with no account
public class Sale {

    private final int saleId;
    private final int customerId;
    private final List<SaleLine> lines;
    private final LocalDateTime saleDate;
    private final double discountPercent;  // e.g. 0.10 for 10% off
    private final String paymentMethod;    // "CASH" or "ACCOUNT"

    public Sale(int saleId, int customerId, List<SaleLine> lines,
                LocalDateTime saleDate, double discountPercent, String paymentMethod) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("sale must have at least one line");
        if (saleDate == null) throw new IllegalArgumentException("sale date cannot be null");
        if (discountPercent < 0 || discountPercent > 1) throw new IllegalArgumentException("discount must be between 0 and 1");
        if (paymentMethod == null || paymentMethod.isBlank()) throw new IllegalArgumentException("payment method cannot be blank");

        this.saleId = saleId;
        this.customerId = customerId;
        this.lines = Collections.unmodifiableList(lines);
        this.saleDate = saleDate;
        this.discountPercent = discountPercent;
        this.paymentMethod = paymentMethod;
    }

    // sum of all line totals before discount and vat
    public double getSubtotal() {
        return lines.stream().mapToDouble(SaleLine::getLineTotal).sum();
    }

    // the pound value being knocked off
    public double getDiscountAmount() {
        return getSubtotal() * discountPercent;
    }

    // final amount the customer pays (after discount, before vat)
    public double getTotal() {
        return getSubtotal() - getDiscountAmount();
    }

    // final amount including vat on each line
    public double getTotalIncVat() {
        double subtotalIncVat = lines.stream().mapToDouble(SaleLine::getLineTotalIncVat).sum();
        return subtotalIncVat - (subtotalIncVat * discountPercent);
    }

    public int getSaleId()              { return saleId; }
    public int getCustomerId()          { return customerId; }
    public List<SaleLine> getLines()    { return lines; }
    public LocalDateTime getSaleDate()  { return saleDate; }
    public double getDiscountPercent()  { return discountPercent; }
    public String getPaymentMethod()    { return paymentMethod; }

    @Override
    public String toString() {
        return "Sale{saleId=" + saleId + ", customerId=" + customerId
                + ", lines=" + lines.size() + ", total=" + getTotal()
                + ", method='" + paymentMethod + "', date=" + saleDate + "}";
    }
}
