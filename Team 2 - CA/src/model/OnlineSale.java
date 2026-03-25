package model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

// represents an online sale event sent from ipos-pu to ipos-ca
// when pu completes a customer order, ca receives this and deducts stock
public class OnlineSale {

    private final String            puOrderId;     // the order id assigned by ipos-pu
    private final LocalDate         receivedDate;
    private final String            customerEmail; // pu customer, nullable
    private final List<OnlineSaleItem> items;

    public OnlineSale(String puOrderId, LocalDate receivedDate,
                      String customerEmail, List<OnlineSaleItem> items) {
        if (puOrderId == null || puOrderId.isBlank()) throw new IllegalArgumentException("pu order id required");
        if (items == null || items.isEmpty())          throw new IllegalArgumentException("sale must have at least one item");

        this.puOrderId     = puOrderId;
        this.receivedDate  = receivedDate != null ? receivedDate : LocalDate.now();
        this.customerEmail = customerEmail;
        this.items         = Collections.unmodifiableList(items);
    }

    public String               getPuOrderId()     { return puOrderId; }
    public LocalDate            getReceivedDate()  { return receivedDate; }
    public String               getCustomerEmail() { return customerEmail; }
    public List<OnlineSaleItem> getItems()         { return items; }
}
