package model;

// one line item in an online sale received from ipos-pu
// simple dto — just item id and quantity, price is handled by ca's stock data
public class OnlineSaleItem {

    private final int itemId;
    private final int quantity;

    public OnlineSaleItem(int itemId, int quantity) {
        if (itemId  <= 0) throw new IllegalArgumentException("item id must be positive");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        this.itemId   = itemId;
        this.quantity = quantity;
    }

    public int getItemId()   { return itemId; }
    public int getQuantity() { return quantity; }
}
