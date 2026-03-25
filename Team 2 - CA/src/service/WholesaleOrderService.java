package service;

import exception.StockException;
import integration.ISaGateway;
import model.OrderLine;
import model.OrderStatus;
import model.WholesaleOrder;

import java.time.LocalDate;
import java.util.List;

// orchestrates wholesale ordering with infopharma (sa)
// uses ISaGateway so the real sa integration is a drop-in swap
public class WholesaleOrderService {

    private final ISaGateway   gateway;
    private final StockService stockService;

    public WholesaleOrderService(ISaGateway gateway, StockService stockService) {
        this.gateway      = gateway;
        this.stockService = stockService;
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

    // get a specific order by id
    public WholesaleOrder getOrder(int orderId) {
        return gateway.getOrderById(orderId);
    }

    // mark a dispatched order as delivered and increase local stock accordingly
    // this is the key integration point: sa delivers, ca stock goes up
    public void markDelivered(int orderId) {
        WholesaleOrder order = gateway.getOrderById(orderId);
        if (order == null) throw new IllegalArgumentException("order not found: " + orderId);
        if (order.getStatus() == OrderStatus.DELIVERED) return; // already done

        // increase stock for every line in the delivery
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

    // simulate sa progressing an order's status (for demo without real sa)
    // for DISPATCHED status, courier info and dates should be supplied
    public boolean simulateStatusUpdate(int orderId, OrderStatus status,
                                        String courier, String courierRef,
                                        LocalDate dispatchDate, LocalDate expectedDelivery) {
        return gateway.updateOrderStatus(orderId, status, courier, courierRef, dispatchDate, expectedDelivery);
    }
}
