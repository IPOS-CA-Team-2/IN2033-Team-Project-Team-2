package integration;

import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;
import repository.WholesaleOrderRepositoryImpl;

import java.time.LocalDate;
import java.util.List;

// mock implementation of ISaGateway — stores orders locally in sqlite
// simulates what ipos-sa would do when we submit or query orders
// to integrate with the real sa system: implement ISaGateway using their api and inject it instead
public class MockSaGateway implements ISaGateway {

    private final WholesaleOrderRepositoryImpl repo;

    public MockSaGateway() {
        this.repo = new WholesaleOrderRepositoryImpl();
    }

    @Override
    public WholesaleOrder submitOrder(List<OrderLine> lines) {
        // create a new pending order and persist it
        WholesaleOrder order = new WholesaleOrder(lines);
        int id = repo.save(order);
        if (id == -1) return null;
        return repo.findById(id);
    }

    @Override
    public WholesaleOrder getOrderById(int orderId) {
        return repo.findById(orderId);
    }

    @Override
    public List<WholesaleOrder> getOrderHistory() {
        return repo.findAll();
    }

    @Override
    public boolean updateOrderStatus(int orderId, OrderStatus status,
                                     String courier, String courierRef,
                                     LocalDate dispatchDate, LocalDate expectedDelivery) {
        return repo.updateStatus(orderId, status, courier, courierRef, dispatchDate, expectedDelivery);
    }
}
