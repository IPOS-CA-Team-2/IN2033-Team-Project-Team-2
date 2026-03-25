package repository;

import model.OrderStatus;
import model.WholesaleOrder;

import java.time.LocalDate;
import java.util.List;

// data access for wholesale orders placed with infopharma (sa)
public interface WholesaleOrderRepository {

    // persist a new order and its lines — returns the generated order id
    int save(WholesaleOrder order);

    // load a single order with its lines
    WholesaleOrder findById(int orderId);

    // all orders, newest first
    List<WholesaleOrder> findAll();

    // update the status and optional dispatch info when sa progresses the order
    boolean updateStatus(int orderId, OrderStatus status,
                         String courier, String courierRef,
                         LocalDate dispatchDate, LocalDate expectedDelivery);
}
