package integration;

import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;
import repository.WholesaleOrderRepositoryImpl;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public Map<String, Object> getInvoiceByOrderId(int saOrderId) {
        // stub — returns mock invoice data so the View Invoice dialog works in mock mode
        Map<String, Object> inv = new HashMap<>();
        inv.put("invoiceNumber",         "INV-MOCK-" + saOrderId);
        inv.put("issuedAt",              LocalDate.now().toString());
        inv.put("dueDate",               LocalDate.now().plusDays(30).toString());
        inv.put("grossTotal",            500.00);
        inv.put("fixedDiscountAmount",   0.00);
        inv.put("flexibleCreditApplied", 0.00);
        inv.put("totalDue",              500.00);
        inv.put("lines",                 java.util.Collections.emptyList());
        return inv;
    }

    @Override
    public Map<String, Object> getOutstandingBalance() {
        // stub — returns zero balance so Check Balance dialog works in mock mode
        Map<String, Object> balance = new HashMap<>();
        balance.put("outstandingTotal",    0.0);
        balance.put("currency",            "GBP");
        balance.put("oldestUnpaidDueDate", null);
        balance.put("daysElapsedSinceDue", 0L);
        return balance;
    }
}
