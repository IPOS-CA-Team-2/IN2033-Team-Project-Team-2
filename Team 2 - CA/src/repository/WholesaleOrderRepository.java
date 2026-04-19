package repository;

import model.OrderStatus;
import model.WholesaleOrder;

import java.time.LocalDate;
import java.util.List;

// data access for wholesale orders placed with infopharma (sa)
public interface WholesaleOrderRepository {

    // save a new order and its lines, returns the generated local order id
    int save(WholesaleOrder order);

    // load a single order with its lines by local id
    WholesaleOrder findById(int orderId);

    // find an order by the sa-assigned id (used when sa pushes a status update)
    // returns null if no matching order found
    WholesaleOrder findBySaOrderId(int saOrderId);

    // all orders, newest first
    List<WholesaleOrder> findAll();

    // update the status and optional dispatch info when sa progresses the order
    boolean updateStatus(int orderId, OrderStatus status,
                         String courier, String courierRef,
                         LocalDate dispatchDate, LocalDate expectedDelivery);

    // store the sa-assigned order id against a local order after successful submission
    boolean updateSaOrderId(int localOrderId, int saOrderId);
}
