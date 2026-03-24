package model;

// represents one stock item in the local pharmacy inventory
// bulk cost is what we pay infopharma — unit price is derived via markup rate
public class StockItem {

    private final int itemId;
    private final String name;
    private final int quantity;
    private final double bulkCost;         // cost from infopharma
    private final double markupRate;       // e.g. 0.30 = 30% markup on bulk cost
    private final double vatRate;          // vat applied on top of unit price
    private final int lowStockThreshold;

    public StockItem(int itemId, String name, int quantity, double bulkCost,
                     double markupRate, double vatRate, int lowStockThreshold) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("item name cannot be blank");
        if (quantity < 0) throw new IllegalArgumentException("quantity cannot be negative");
        if (bulkCost < 0) throw new IllegalArgumentException("bulk cost cannot be negative");
        if (markupRate < 0) throw new IllegalArgumentException("markup rate cannot be negative");
        if (vatRate < 0) throw new IllegalArgumentException("vat rate cannot be negative");
        if (lowStockThreshold < 0) throw new IllegalArgumentException("low stock threshold cannot be negative");

        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
        this.bulkCost = bulkCost;
        this.markupRate = markupRate;
        this.vatRate = vatRate;
        this.lowStockThreshold = lowStockThreshold;
    }

    // retail price before vat — bulk cost with markup applied
    public double getUnitPrice() {
        return bulkCost * (1 + markupRate);
    }

    // retail price including vat
    public double getPriceIncVat() {
        return getUnitPrice() * (1 + vatRate);
    }

    public boolean isLowStock() {
        return quantity <= lowStockThreshold;
    }

    public int getItemId()           { return itemId; }
    public String getName()          { return name; }
    public int getQuantity()         { return quantity; }
    public double getBulkCost()      { return bulkCost; }
    public double getMarkupRate()    { return markupRate; }
    public double getVatRate()       { return vatRate; }
    public int getLowStockThreshold(){ return lowStockThreshold; }

    @Override
    public String toString() {
        return "StockItem{itemId=" + itemId + ", name='" + name + "', quantity=" + quantity
                + ", bulkCost=" + bulkCost + ", markupRate=" + markupRate
                + ", unitPrice=" + String.format("%.2f", getUnitPrice())
                + ", lowStockThreshold=" + lowStockThreshold + "}";
    }
}
