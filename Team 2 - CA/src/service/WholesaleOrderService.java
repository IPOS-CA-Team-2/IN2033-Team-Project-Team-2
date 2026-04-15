package service;

import exception.StockException;
import integration.ISaGateway;
import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;
import repository.WholesaleOrderRepositoryImpl;

import java.time.LocalDate;
import java.util.List;

// orchestrates wholesale ordering with infopharma (sa)
// uses ISaGateway so the real sa integration is a drop-in swap
public class WholesaleOrderService {

    private final ISaGateway   gateway;
    private final StockService stockService;
    // direct repo access needed for findBySaOrderId — gateway doesn't expose this
    private final WholesaleOrderRepositoryImpl repo;

    public WholesaleOrderService(ISaGateway gateway, StockService stockService) {
        this.gateway      = gateway;
        this.stockService = stockService;
        this.repo         = new WholesaleOrderRepositoryImpl();
    }

    // submit a new wholesale order to sa — returns the saved order with its id
    public WholesaleOrder placeOrder(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) throw new IllegalArgumentException("order must have at least one line");
        return gateway.submitOrder(lines);
    }

    // all orders, newest first
    public List<WholesaleOrder> getAllOrders() {
        return gateway.getOrderHistory();
    }

    // get a specific order by local id
    public WholesaleOrder getOrder(int orderId) {
        return gateway.getOrderById(orderId);
    }

    // mark a dispatched order as delivered and increase local stock accordingly
    // this is the key integration point: sa delivers, ca stock goes up
    public void markDelivered(int orderId) {
        WholesaleOrder order = gateway.getOrderById(orderId);
        if (order == null) throw new IllegalArgumentException("order not found: " + orderId);
        if (order.getStatus() == OrderStatus.DELIVERED) return;

        for (OrderLine line : order.getLines()) {
            try {
                stockService.increaseStock(line.getItemId(), line.getQuantity());
            } catch (StockException e) {
                // log but don't fail the whole delivery — item may have been deleted
                System.err.println("could not increase stock for item "
                    + line.getItemId() + " on delivery: " + e.getMessage());
            }
        }

        gateway.updateOrderStatus(orderId, OrderStatus.DELIVERED, null, null, null, null);
    }

    // called by CaApiServer when sa pushes an order status update to /order-update
    // finds the local order by the sa-assigned id and updates its status
    // if the new status is DELIVERED, stock is increased automatically
    // shipping details are forwarded to the repo for DISPATCHED status
    public void receiveStatusUpdate(int saOrderId, OrderStatus newStatus,
                                    String courierName, String courierRef,
                                    LocalDate dispatchDate, LocalDate expectedDelivery) {
        WholesaleOrder order = repo.findBySaOrderId(saOrderId);
        if (order == null) {
            System.err.println("[WholesaleOrderService] No local order found for SA id=" + saOrderId);
            return;
        }

        if (newStatus == OrderStatus.DELIVERED) {
            markDelivered(order.getOrderId());
        } else {
            repo.updateStatus(order.getOrderId(), newStatus, courierName, courierRef, dispatchDate, expectedDelivery);
        }

        System.out.println("[WholesaleOrderService] Status updated for SA id=" + saOrderId + " → " + newStatus);
    }

    // convenience overload for callers that don't have shipping details (e.g. mock/test)
    public void receiveStatusUpdate(int saOrderId, OrderStatus newStatus) {
        receiveStatusUpdate(saOrderId, newStatus, null, null, null, null);
    }

    // simulate sa progressing an order's status (for demo without real sa)
    // for DISPATCHED status, courier info and dates should be supplied
    public boolean simulateStatusUpdate(int orderId, OrderStatus status,
                                        String courier, String courierRef,
                                        LocalDate dispatchDate, LocalDate expectedDelivery) {
        return gateway.updateOrderStatus(orderId, status, courier, courierRef, dispatchDate, expectedDelivery);
    }

    // fetch invoice from SA for a given SA order id — returns null if SA unreachable
    // keys: invoiceNumber, issuedAt, dueDate, grossTotal, fixedDiscountAmount,
    //       flexibleCreditApplied, totalDue, lines (List<Map>)
    public java.util.Map<String, Object> getInvoice(int saOrderId) {
        return gateway.getInvoiceByOrderId(saOrderId);
    }

    // query outstanding balance for the CA merchant account at SA — returns null if SA unreachable
    // keys: outstandingTotal, currency, oldestUnpaidDueDate, daysElapsedSinceDue
    public java.util.Map<String, Object> getOutstandingBalance() {
        return gateway.getOutstandingBalance();
    }
}
