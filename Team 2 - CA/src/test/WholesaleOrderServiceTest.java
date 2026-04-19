package test;

import exception.StockException;
import integration.ISaGateway;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.StockRepository;
import service.StockService;
import service.WholesaleOrderService;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class WholesaleOrderServiceTest {

    // in-memory gateway so tests don't need a real database or SA connection
    static class FakeGateway implements ISaGateway {
        Map<Integer, WholesaleOrder> orders = new LinkedHashMap<>();
        int nextId = 1;

        @Override
        public WholesaleOrder submitOrder(List<OrderLine> lines) {
            int id = nextId++;
            WholesaleOrder order = new WholesaleOrder(id, LocalDate.now(), OrderStatus.PENDING,
                    lines, null, null, null, null);
            orders.put(id, order);
            return order;
        }

        @Override
        public WholesaleOrder getOrderById(int orderId) {
            return orders.get(orderId);
        }

        @Override
        public List<WholesaleOrder> getOrderHistory() {
            return new ArrayList<>(orders.values());
        }

        @Override
        public boolean updateOrderStatus(int orderId, OrderStatus status,
                                         String courier, String courierRef,
                                         LocalDate dispatchDate, LocalDate expectedDelivery) {
            WholesaleOrder old = orders.get(orderId);
            if (old == null) return false;
            WholesaleOrder updated = new WholesaleOrder(old.getOrderId(), old.getOrderDate(),
                    status, new ArrayList<>(old.getLines()),
                    dispatchDate, courier, courierRef, expectedDelivery);
            orders.put(orderId, updated);
            return true;
        }

        @Override
        public Map<String, Object> getInvoiceByOrderId(int saOrderId) {
            return null;
        }

        @Override
        public Map<String, Object> getOutstandingBalance() {
            return null;
        }
    }

    // in-memory stock repo so stock checks work without hitting the database
    static class FakeStockRepo implements StockRepository {
        Map<Integer, StockItem> data = new HashMap<>();
        int nextId = 1;

        @Override
        public List<StockItem> findAll() { return new ArrayList<>(data.values()); }

        @Override
        public StockItem findById(int id) { return data.get(id); }

        @Override
        public boolean updateQuantity(int id, int qty) {
            StockItem s = data.get(id);
            if (s == null) return false;
            data.put(id, new StockItem(id, s.getName(), qty, s.getBulkCost(),
                    s.getMarkupRate(), s.getVatRate(), s.getLowStockThreshold()));
            return true;
        }

        @Override
        public List<StockItem> findLowStock() {
            List<StockItem> result = new ArrayList<>();
            for (StockItem i : data.values()) {
                if (i.isLowStock()) result.add(i);
            }
            return result;
        }

        @Override
        public boolean save(StockItem item) {
            int id = item.getItemId() == 0 ? nextId++ : item.getItemId();
            data.put(id, new StockItem(id, item.getName(), item.getQuantity(), item.getBulkCost(),
                    item.getMarkupRate(), item.getVatRate(), item.getLowStockThreshold()));
            return true;
        }

        @Override
        public boolean delete(int id) {
            if (data.containsKey(id)) { data.remove(id); return true; }
            return false;
        }
    }

    FakeGateway gateway;
    FakeStockRepo stockRepo;
    StockService stockService;
    WholesaleOrderService orderService;

    @BeforeEach
    void setup() {
        gateway = new FakeGateway();
        stockRepo = new FakeStockRepo();
        stockService = new StockService(stockRepo);
        // seed two items with enough stock for the tests
        stockRepo.save(new StockItem(1, "Paracetamol 500mg", 200, 1.00, 1.0, 0.0, 10));
        stockRepo.save(new StockItem(2, "Ibuprofen 200mg", 150, 1.50, 1.0, 0.0, 10));
        orderService = new WholesaleOrderService(gateway, stockService);
    }

    @Test
    void test_place_order_returns_order_with_id() {
        List<OrderLine> lines = List.of(
            new OrderLine(1, "Paracetamol 500mg", 50, 1.92),
            new OrderLine(2, "Ibuprofen 200mg", 30, 3.07)
        );
        WholesaleOrder order = orderService.placeOrder(lines);
        assertNotNull(order);
        assertTrue(order.getOrderId() > 0);
    }

    @Test
    void test_new_order_starts_as_pending() {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    void test_order_total_value_calculated_correctly() {
        List<OrderLine> lines = List.of(
            new OrderLine(1, "Paracetamol 500mg", 50, 1.92),
            new OrderLine(2, "Ibuprofen 200mg", 30, 3.07)
        );
        WholesaleOrder order = orderService.placeOrder(lines);
        double expected = (50 * 1.92) + (30 * 3.07);
        assertEquals(expected, order.getTotalValue(), 0.001);
    }

    @Test
    void test_empty_order_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(List.of()));
    }

    @Test
    void test_null_order_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(null));
    }

    @Test
    void test_status_update_to_accepted() {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        orderService.simulateStatusUpdate(order.getOrderId(), OrderStatus.ACCEPTED, null, null, null, null);
        assertEquals(OrderStatus.ACCEPTED, orderService.getOrder(order.getOrderId()).getStatus());
    }

    @Test
    void test_dispatch_stores_courier_info() {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        LocalDate dispatch = LocalDate.now();
        orderService.simulateStatusUpdate(order.getOrderId(), OrderStatus.DISPATCHED,
                "DHL", "DHL123", dispatch, dispatch.plusDays(3));
        WholesaleOrder updated = orderService.getOrder(order.getOrderId());
        assertEquals(OrderStatus.DISPATCHED, updated.getStatus());
        assertEquals("DHL", updated.getCourier());
        assertEquals("DHL123", updated.getCourierRef());
    }

    @Test
    void test_mark_delivered_increases_stock() throws StockException {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 50, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        int before = stockService.getStockItem(1).getQuantity();
        orderService.markDelivered(order.getOrderId());
        int after = stockService.getStockItem(1).getQuantity();
        assertEquals(before + 50, after);
    }

    @Test
    void test_mark_delivered_sets_status_to_delivered() throws StockException {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        orderService.markDelivered(order.getOrderId());
        assertEquals(OrderStatus.DELIVERED, orderService.getOrder(order.getOrderId()).getStatus());
    }

    @Test
    void test_mark_delivered_twice_is_a_no_op() throws StockException {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder order = orderService.placeOrder(lines);
        orderService.markDelivered(order.getOrderId());
        int stockAfterFirst = stockService.getStockItem(1).getQuantity();
        // calling it again should not add stock a second time
        orderService.markDelivered(order.getOrderId());
        int stockAfterSecond = stockService.getStockItem(1).getQuantity();
        assertEquals(stockAfterFirst, stockAfterSecond);
    }

    @Test
    void test_mark_delivered_invalid_id_throws() {
        assertThrows(IllegalArgumentException.class, () -> orderService.markDelivered(9999));
    }

    @Test
    void test_get_all_orders_returns_history() {
        orderService.placeOrder(List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92)));
        orderService.placeOrder(List.of(new OrderLine(2, "Ibuprofen 200mg", 5, 3.07)));
        List<WholesaleOrder> history = orderService.getAllOrders();
        assertEquals(2, history.size());
    }

    @Test
    void test_get_order_by_id_returns_correct_order() {
        List<OrderLine> lines = List.of(new OrderLine(1, "Paracetamol 500mg", 10, 1.92));
        WholesaleOrder placed = orderService.placeOrder(lines);
        WholesaleOrder fetched = orderService.getOrder(placed.getOrderId());
        assertNotNull(fetched);
        assertEquals(placed.getOrderId(), fetched.getOrderId());
    }

    @Test
    void test_get_order_unknown_id_returns_null() {
        assertNull(orderService.getOrder(9999));
    }

    @Test
    void test_order_contains_correct_lines() {
        List<OrderLine> lines = List.of(
            new OrderLine(1, "Paracetamol 500mg", 20, 1.92),
            new OrderLine(2, "Ibuprofen 200mg", 10, 3.07)
        );
        WholesaleOrder order = orderService.placeOrder(lines);
        assertEquals(2, order.getLines().size());
    }
}
