package model;

public class StockItem {

    private final int itemId;
    private final String name;
    private final int quantity;
    private final double unitPrice;
    private final double vatRate;
    private final int lowStockThreshold;

    public StockItem(int itemId, String name, int quantity, double unitPrice, double vatRate, int lowStockThreshold) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be null or blank.");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative.");
        }
        if (unitPrice < 0) {
            throw new IllegalArgumentException("Unit price cannot be negative.");
        }
        if (vatRate < 0) {
            throw new IllegalArgumentException("VAT rate cannot be negative.");
        }
        if (lowStockThreshold < 0) {
            throw new IllegalArgumentException("Low stock threshold cannot be negative.");
        }

        this.itemId = itemId;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.vatRate = vatRate;
        this.lowStockThreshold = lowStockThreshold;
    }

    public int getItemId() { return itemId; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public double getUnitPrice() { return unitPrice; }
    public double getVatRate() { return vatRate; }
    public int getLowStockThreshold() { return lowStockThreshold; }

    public double getPriceIncVat() {
        return unitPrice * (1 + vatRate);
    }

    public boolean isLowStock() {
        return quantity <= lowStockThreshold;
    }

    @Override
    public String toString() {
        return "StockItem{" +
                "itemId=" + itemId +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                ", vatRate=" + vatRate +
                ", lowStockThreshold=" + lowStockThreshold +
                '}';
    }
}
