package model;

// one line item in a wholesale order sent to infopharma (sa)
public class OrderLine {

    private final int    itemId;
    private final String itemName;
    private final int    quantity;
    private final double unitCost; // bulk cost per unit charged by infopharma

    public OrderLine(int itemId, String itemName, int quantity, double unitCost) {
        if (itemId <= 0)                    throw new IllegalArgumentException("item id must be positive");
        if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("item name required");
        if (quantity <= 0)                  throw new IllegalArgumentException("quantity must be positive");
        if (unitCost < 0)                   throw new IllegalArgumentException("unit cost cannot be negative");

        this.itemId   = itemId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    // total cost for this line before any discount
    public double getLineTotal() {
        return quantity * unitCost;
    }

    public int    getItemId()   { return itemId; }
    public String getItemName() { return itemName; }
    public int    getQuantity() { return quantity; }
    public double getUnitCost() { return unitCost; }

    @Override
    public String toString() {
        return itemName + " x" + quantity + " @ £" + String.format("%.2f", unitCost);
    }
}
