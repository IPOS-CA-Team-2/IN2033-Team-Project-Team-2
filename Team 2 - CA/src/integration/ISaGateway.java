package integration;

import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;

import java.time.LocalDate;
import java.util.List;

// the interface through which ipos-ca communicates with ipos-sa (infopharma wholesale)
// currently backed by MockSaGateway (local sqlite)
// when sa team is ready: swap MockSaGateway for HttpSaGateway — nothing else changes
public interface ISaGateway {

    // submit a new wholesale order to sa — returns the order with its assigned id
    WholesaleOrder submitOrder(List<OrderLine> lines);

    // get a single order by its local id
    WholesaleOrder getOrderById(int orderId);

    // full order history, newest first
    List<WholesaleOrder> getOrderHistory();

    // simulate or receive a status update from sa (dispatched, accepted, etc.)
    // courier info is only relevant when status = DISPATCHED
    boolean updateOrderStatus(int orderId, OrderStatus status,
                              String courier, String courierRef,
                              LocalDate dispatchDate, LocalDate expectedDelivery);
}
