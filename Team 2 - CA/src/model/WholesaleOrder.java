package model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

// represents a wholesale order placed by the merchant with infopharma (sa)
public class WholesaleOrder {

    private final int             orderId;
    private final LocalDate       orderDate;
    private final OrderStatus     status;
    private final List<OrderLine> lines;

    // dispatch info — populated once sa marks the order as dispatched
    private final LocalDate dispatchDate;
    private final String    courier;
    private final String    courierRef;
    private final LocalDate expectedDelivery;

    // sa-assigned order id — stored after successful submission to real sa system
    // 0 means not yet submitted to sa (mock mode or submission failed)
    private int saOrderId = 0;

    // full constructor — used when loading from db or receiving a status update
    public WholesaleOrder(int orderId, LocalDate orderDate, OrderStatus status,
                          List<OrderLine> lines,
                          LocalDate dispatchDate, String courier,
                          String courierRef, LocalDate expectedDelivery) {
        this.orderId          = orderId;
        this.orderDate        = orderDate;
        this.status           = status;
        this.lines            = lines != null ? Collections.unmodifiableList(lines) : Collections.emptyList();
        this.dispatchDate     = dispatchDate;
        this.courier          = courier;
        this.courierRef       = courierRef;
        this.expectedDelivery = expectedDelivery;
    }

    // convenience constructor for a new order being placed (no dispatch info yet)
    public WholesaleOrder(List<OrderLine> lines) {
        this(0, LocalDate.now(), OrderStatus.PENDING, lines, null, null, null, null);
    }

    // sum of all line totals
    public double getTotalValue() {
        return lines.stream().mapToDouble(OrderLine::getLineTotal).sum();
    }

    public int             getOrderId()         { return orderId; }
    public LocalDate       getOrderDate()        { return orderDate; }
    public OrderStatus     getStatus()           { return status; }
    public List<OrderLine> getLines()            { return lines; }
    public LocalDate       getDispatchDate()     { return dispatchDate; }
    public String          getCourier()          { return courier; }
    public String          getCourierRef()       { return courierRef; }
    public LocalDate       getExpectedDelivery() { return expectedDelivery; }
    public int             getSaOrderId()        { return saOrderId; }
    public void            setSaOrderId(int id)  { this.saOrderId = id; }
}
