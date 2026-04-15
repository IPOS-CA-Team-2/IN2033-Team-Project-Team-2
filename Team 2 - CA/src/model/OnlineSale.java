package model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

// represents an online sale event sent from ipos-pu to ipos-ca
// when pu completes a customer order, ca receives this and deducts stock
public class OnlineSale {

    private final String               puOrderId;       // the order id assigned by ipos-pu
    private final LocalDate            receivedDate;
    private final String               customerEmail;   // pu customer, nullable
    private final String               deliveryAddress; // physical address for dispatch
    private final List<OnlineSaleItem> items;

    // full constructor — includes deliveryAddress
    public OnlineSale(String puOrderId, LocalDate receivedDate,
                      String customerEmail, String deliveryAddress,
                      List<OnlineSaleItem> items) {
        if (puOrderId == null || puOrderId.isBlank()) throw new IllegalArgumentException("pu order id required");
        if (items == null || items.isEmpty())          throw new IllegalArgumentException("sale must have at least one item");

        this.puOrderId       = puOrderId;
        this.receivedDate    = receivedDate != null ? receivedDate : LocalDate.now();
        this.customerEmail   = customerEmail;
        this.deliveryAddress = deliveryAddress != null ? deliveryAddress : "";
        this.items           = Collections.unmodifiableList(items);
    }

    // backward-compatible constructor — deliveryAddress defaults to empty string
    public OnlineSale(String puOrderId, LocalDate receivedDate,
                      String customerEmail, List<OnlineSaleItem> items) {
        this(puOrderId, receivedDate, customerEmail, "", items);
    }

    public String               getPuOrderId()       { return puOrderId; }
    public LocalDate            getReceivedDate()    { return receivedDate; }
    public String               getCustomerEmail()   { return customerEmail; }
    public String               getDeliveryAddress() { return deliveryAddress; }
    public List<OnlineSaleItem> getItems()           { return items; }
}
