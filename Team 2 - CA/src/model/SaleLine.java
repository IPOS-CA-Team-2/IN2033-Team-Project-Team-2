package model;

// represents one line item in a sale: one product, a quantity, and the price at time of sale
// price is captured at sale time so historical records stay accurate if prices change later
public class SaleLine {

    private final int itemId;
    private final String itemName;
    private final int quantity;
    private final double unitPrice;
    private final double vatRate;

    public SaleLine(int itemId, String itemName, int quantity, double unitPrice, double vatRate) {
        if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("item name cannot be blank");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (unitPrice < 0) throw new IllegalArgumentException("unit price cannot be negative");
        if (vatRate < 0) throw new IllegalArgumentException("vat rate cannot be negative");

        this.itemId = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
    }

    // total for this line before vat
    public double getLineTotal() {
        return quantity * unitPrice;
    }

    // total for this line including vat
    public double getLineTotalIncVat() {
        return getLineTotal() * (1 + vatRate);
    }

    public int getItemId()      { return itemId; }
    public String getItemName() { return itemName; }
    public int getQuantity()    { return quantity; }
    public double getUnitPrice(){ return unitPrice; }
    public double getVatRate()  { return vatRate; }

    @Override
    public String toString() {
        return "SaleLine{itemId=" + itemId + ", name='" + itemName + "', qty=" + quantity
                + ", unitPrice=" + unitPrice + ", lineTotal=" + getLineTotal() + "}";
    }
}
