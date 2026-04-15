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

    // fake gateway - stores orders in memory instead of hitting SA api
    class FakeGateway implements ISaGateway {
        Map<Integer, WholesaleOrder> orders = new LinkedHashMap<>();
        int idCount = 1;

        public WholesaleOrder submitOrder(List<OrderLine> lines) {
            WholesaleOrder o = new WholesaleOrder(idCount++, LocalDate.now(), OrderStatus.PENDING, lines, null, null, null, null);
            orders.put(o.getOrderId(), o);
            return o;
        }

        public WholesaleOrder getOrderById(int id) {
            return orders.get(id);
        }

        public List<WholesaleOrder> getOrderHistory() {
            List<WholesaleOrder> list = new ArrayList<>(orders.values());
            Collections.reverse(list);
            return list;
        }

        public boolean updateOrderStatus(int id, OrderStatus status, String courier, String courierRef, LocalDate dispatch, LocalDate expected) {
            WholesaleOrder old = orders.get(id);
            if(old == null) return false;
            orders.put(id, new WholesaleOrder(id, old.getOrderDate(), status, old.getLines(), dispatch, courier, courierRef, expected));
            return true;
        }
    }

    // fake stock repo
    class FakeStockRepo implements StockRepository {
        Map<Integer, StockItem> items = new HashMap<>();
        public List<StockItem> findAll() { return new ArrayList<>(items.values()); }
        public StockItem findById(int id) { return items.get(id); }
        public boolean updateQuantity(int id, int qty) {
            StockItem old = items.get(id);
            if(old == null) return false;
            items.put(id, new StockItem(id, old.getName(), qty, old.getBulkCost(), old.getMarkupRate(), old.getVatRate(), old.getLowStockThreshold()));
            return true;
        }
        public List<StockItem> findLowStock() { return new ArrayList<>(); }
        public boolean save(StockItem i) { items.put(i.getItemId(), i); return true; }
        public boolean delete(int id) { return items.remove(id) != null; }
    }

    FakeGateway gateway;
    FakeStockRepo stockRepo;
    StockService stockSvc;
    WholesaleOrderService orderService;

    @BeforeEach
    void setup() {
        gateway = new FakeGateway();
        stockRepo = new FakeStockRepo();
        stockSvc = new StockService(stockRepo);
        orderService = new WholesaleOrderService(gateway, stockSvc);

        stockRepo.save(new StockItem(1, "Paracetamol 500mg", 50, 1.00, 0.20, 0.05, 10));
        stockRepo.save(new StockItem(2, "Ibuprofen 200mg", 20, 1.50, 0.20, 0.05, 10));
    }

    List<OrderLine> makeLines(int itemId, int qty) {
        return List.of(new OrderLine(itemId, "Paracetamol 500mg", qty, 1.00));
    }

    @Test
    void testPlaceOrderReturnsOrder() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 10));
        assertNotNull(o);
    }

    @Test
    void testPlaceOrderHasId() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 10));
        assertTrue(o.getOrderId() > 0);
    }

    @Test
    void testNewOrderIsPending() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 5));
        assertEquals(OrderStatus.PENDING, o.getStatus());
    }

    @Test
    void testOrderHasCorrectLines() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 10));
        assertEquals(1, o.getLines().size());
        assertEquals(10, o.getLines().get(0).getQuantity());
    }

    @Test
    void testEmptyLinesThrows() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(new ArrayList<>()));
    }

    @Test
    void testNullLinesThrows() {
        assertThrows(IllegalArgumentException.class, () -> orderService.placeOrder(null));
    }

    @Test
    void testTwoOrdersHaveDifferentIds() {
        WholesaleOrder o1 = orderService.placeOrder(makeLines(1, 5));
        WholesaleOrder o2 = orderService.placeOrder(makeLines(2, 3));
        assertNotEquals(o1.getOrderId(), o2.getOrderId());
    }

    @Test
    void testGetAllOrdersEmpty() {
        assertTrue(orderService.getAllOrders().isEmpty());
    }

    @Test
    void testGetAllOrdersAfterPlacing() {
        orderService.placeOrder(makeLines(1, 5));
        orderService.placeOrder(makeLines(2, 3));
        assertEquals(2, orderService.getAllOrders().size());
    }

    @Test
    void testGetOrderById() {
        WholesaleOrder placed = orderService.placeOrder(makeLines(1, 5));
        WholesaleOrder found = orderService.getOrder(placed.getOrderId());
        assertEquals(placed.getOrderId(), found.getOrderId());
    }

    @Test
    void testGetOrderUnknownIdReturnsNull() {
        assertNull(orderService.getOrder(999));
    }

    @Test
    void testMarkDeliveredIncreasesStock() throws StockException {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 20));
        orderService.markDelivered(o.getOrderId());
        // started at 50, added 20 = 70
        assertEquals(70, stockSvc.getStockItem(1).getQuantity());
    }

    @Test
    void testMarkDeliveredChangesStatus() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 5));
        orderService.markDelivered(o.getOrderId());
        assertEquals(OrderStatus.DELIVERED, orderService.getOrder(o.getOrderId()).getStatus());
    }

    @Test
    void testMarkDeliveredTwiceDoesNotDoubleStock() throws StockException {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 10));
        orderService.markDelivered(o.getOrderId());
        orderService.markDelivered(o.getOrderId()); // calling again shouldnt add stock again
        assertEquals(60, stockSvc.getStockItem(1).getQuantity());
    }

    @Test
    void testMarkDeliveredUnknownOrderThrows() {
        assertThrows(IllegalArgumentException.class, () -> orderService.markDelivered(999));
    }

    @Test
    void testSimulateStatusUpdateAccepted() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 5));
        orderService.simulateStatusUpdate(o.getOrderId(), OrderStatus.ACCEPTED, null, null, null, null);
        assertEquals(OrderStatus.ACCEPTED, orderService.getOrder(o.getOrderId()).getStatus());
    }

    @Test
    void testSimulateStatusUpdateDispatched() {
        WholesaleOrder o = orderService.placeOrder(makeLines(1, 5));
        orderService.simulateStatusUpdate(o.getOrderId(), OrderStatus.DISPATCHED, "DHL", "REF123", LocalDate.now(), LocalDate.now().plusDays(2));
        assertEquals(OrderStatus.DISPATCHED, orderService.getOrder(o.getOrderId()).getStatus());
    }

    @Test
    void testSimulateStatusUpdateUnknownOrderReturnsFalse() {
        boolean result = orderService.simulateStatusUpdate(999, OrderStatus.ACCEPTED, null, null, null, null);
        assertFalse(result);
    }
}
