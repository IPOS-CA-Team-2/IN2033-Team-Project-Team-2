package model;

// represents one stock item in the local pharmacy inventory
// bulk cost is what we pay infopharma — unit price is derived via markup rate
public class StockItem {

    private final int itemId;
    private final String itemCode;         // spec item id e.g. "100 00001"
    private final String name;
    private final String packageType;      // e.g. Box, Bottle
    private final String unit;             // e.g. Caps, Ml
    private final int unitsPerPack;        // number of units in one pack
    private final int quantity;
    private final double bulkCost;         // cost from infopharma (package cost)
    private final double markupRate;       // e.g. 0.30 = 30% markup on bulk cost
    private final double vatRate;          // vat applied on top of unit price
    private final int lowStockThreshold;

    // full constructor
    public StockItem(int itemId, String itemCode, String name, String packageType, String unit,
                     int unitsPerPack, int quantity, double bulkCost,
                     double markupRate, double vatRate, int lowStockThreshold) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("item name cannot be blank");
        if (quantity < 0) throw new IllegalArgumentException("quantity cannot be negative");
        if (bulkCost < 0) throw new IllegalArgumentException("bulk cost cannot be negative");
        if (markupRate < 0) throw new IllegalArgumentException("markup rate cannot be negative");
        if (vatRate < 0) throw new IllegalArgumentException("vat rate cannot be negative");
        if (lowStockThreshold < 0) throw new IllegalArgumentException("low stock threshold cannot be negative");

        this.itemId = itemId;
        this.itemCode = itemCode == null ? "" : itemCode;
        this.name = name;
        this.packageType = packageType == null ? "" : packageType;
        this.unit = unit == null ? "" : unit;
        this.unitsPerPack = unitsPerPack;
        this.quantity = quantity;
        this.bulkCost = bulkCost;
        this.markupRate = markupRate;
        this.vatRate = vatRate;
        this.lowStockThreshold = lowStockThreshold;
    }

    // backwards-compatible constructor — new fields default to empty/0
    public StockItem(int itemId, String name, int quantity, double bulkCost,
                     double markupRate, double vatRate, int lowStockThreshold) {
        this(itemId, "", name, "", "", 0, quantity, bulkCost, markupRate, vatRate, lowStockThreshold);
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

    public int getItemId()            { return itemId; }
    public String getItemCode()       { return itemCode; }
    public String getName()           { return name; }
    public String getPackageType()    { return packageType; }
    public String getUnit()           { return unit; }
    public int getUnitsPerPack()      { return unitsPerPack; }
    public int getQuantity()          { return quantity; }
    public double getBulkCost()       { return bulkCost; }
    public double getMarkupRate()     { return markupRate; }
    public double getVatRate()        { return vatRate; }
    public int getLowStockThreshold() { return lowStockThreshold; }

    @Override
    public String toString() {
        return "StockItem{itemId=" + itemId + ", itemCode='" + itemCode + "', name='" + name
                + "', packageType='" + packageType + "', unit='" + unit
                + "', unitsPerPack=" + unitsPerPack + ", quantity=" + quantity
                + ", bulkCost=" + bulkCost + ", markupRate=" + markupRate
                + ", unitPrice=" + String.format("%.2f", getUnitPrice())
                + ", lowStockThreshold=" + lowStockThreshold + "}";
    }
}
