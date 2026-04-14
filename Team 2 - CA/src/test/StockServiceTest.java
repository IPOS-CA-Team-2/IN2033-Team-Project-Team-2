package test;

import exception.StockException;
import model.StockItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.StockRepository;
import service.StockService;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class StockServiceTest {

    // fake repo for testing (no database)
    static class FakeRepo implements StockRepository {
        Map<Integer, StockItem> data = new HashMap<>();
        int nextId = 1;

        public List<StockItem> findAll() { return new ArrayList<>(data.values()); }
        public StockItem findById(int id) { return data.get(id); }

        public boolean updateQuantity(int id, int qty) {
            StockItem s = data.get(id);
            if(s == null) return false;
            data.put(id, new StockItem(id, s.getName(), qty, s.getBulkCost(), s.getMarkupRate(), s.getVatRate(), s.getLowStockThreshold()));
            return true;
        }

        public List<StockItem> findLowStock() {
            List<StockItem> result = new ArrayList<>();
            for(StockItem i : data.values()) {
                if(i.isLowStock()) result.add(i);
            }
            return result;
        }

        public boolean save(StockItem item) {
            int id = item.getItemId() == 0 ? nextId++ : item.getItemId();
            data.put(id, new StockItem(id, item.getName(), item.getQuantity(), item.getBulkCost(), item.getMarkupRate(), item.getVatRate(), item.getLowStockThreshold()));
            return true;
        }

        public boolean delete(int id) {
            if(data.containsKey(id)) { data.remove(id); return true; }
            return false;
        }
    }

    FakeRepo repo;
    StockService stockService;

    @BeforeEach
    void setup() {
        repo = new FakeRepo();
        stockService = new StockService(repo);
        // add some test items
        repo.save(new StockItem(1, "Paracetamol 500mg", 100, 1.00, 0.20, 0.05, 10));
        repo.save(new StockItem(2, "Ibuprofen 200mg", 5, 1.50, 0.20, 0.05, 10));
    }

    @Test
    void test_null_repo_throws_exception() {
        assertThrows(IllegalArgumentException.class, () -> new StockService(null));
    }

    @Test
    void test_get_all_returns_correct_size() {
        List<StockItem> items = stockService.getAllStock();
        assertEquals(2, items.size());
    }

    @Test
    void test_empty_stock_returns_empty_list() {
        StockService s2 = new StockService(new FakeRepo());
        assertTrue(s2.getAllStock().isEmpty());
    }

    @Test
    void testGetItemById() throws StockException {
        StockItem item = stockService.getStockItem(1);
        assertNotNull(item);
        assertEquals("Paracetamol 500mg", item.getName());
    }

    @Test
    void testGetItem_negative_id() {
        // should throw exception
        StockException e = assertThrows(StockException.class, () -> stockService.getStockItem(-1));
        assertEquals(StockException.Reason.INVALID_ITEM_ID, e.getReason());
    }

    @Test
    void testGetItem_zero_id() {
        StockException e = assertThrows(StockException.class, () -> stockService.getStockItem(0));
        assertEquals(StockException.Reason.INVALID_ITEM_ID, e.getReason());
    }

    @Test
    void testGetItem_not_found() {
        StockException e = assertThrows(StockException.class, () -> stockService.getStockItem(99));
        assertEquals(StockException.Reason.ITEM_NOT_FOUND, e.getReason());
    }

    @Test
    void test_increase_stock_works() throws StockException {
        stockService.increaseStock(1, 50);
        StockItem updated = stockService.getStockItem(1);
        assertEquals(150, updated.getQuantity());
        //System.out.println("new qty: " + updated.getQuantity());
    }

    @Test
    void test_increase_zero_should_fail() {
        StockException e = assertThrows(StockException.class, () -> stockService.increaseStock(1, 0));
        assertEquals(StockException.Reason.INVALID_QUANTITY, e.getReason());
    }

    @Test
    void test_increase_negative_should_fail() {
        StockException e = assertThrows(StockException.class, () -> stockService.increaseStock(1, -5));
        assertEquals(StockException.Reason.INVALID_QUANTITY, e.getReason());
    }

    @Test
    void testDecreaseStock() throws StockException {
        stockService.decreaseStock(1, 30);
        assertEquals(70, stockService.getStockItem(1).getQuantity());
    }

    @Test
    void testDecreaseStockToZero() throws StockException {
        stockService.decreaseStock(1, 100);
        assertEquals(0, stockService.getStockItem(1).getQuantity());
    }

    @Test
    void testDecreaseTooMuch() {
        StockException e = assertThrows(StockException.class, () -> stockService.decreaseStock(1, 999));
        assertEquals(StockException.Reason.INSUFFICIENT_STOCK, e.getReason());
    }

    @Test
    void test_decrease_zero_throws() {
        StockException e = assertThrows(StockException.class, () -> stockService.decreaseStock(1, 0));
        assertEquals(StockException.Reason.INVALID_QUANTITY, e.getReason());
    }

    @Test
    void test_low_stock_ibuprofen_shows_up() {
        // ibuprofen has qty 5 and threshold 10 so should appear
        List<StockItem> lowItems = stockService.getLowStock();
        boolean found = false;
        for(StockItem i : lowItems) {
            if(i.getItemId() == 2) found = true;
        }
        assertTrue(found);
    }

    @Test
    void test_paracetamol_not_low_stock() {
        List<StockItem> lowItems = stockService.getLowStock();
        for(StockItem i : lowItems) {
            assertNotEquals(1, i.getItemId());
        }
    }

    @Test
    void testAddNewItem() throws StockException {
        stockService.addStockItem(new StockItem(3, "Amoxicillin 250mg", 200, 3.00, 0.25, 0.05, 20));
        assertEquals(3, stockService.getAllStock().size());
    }

    @Test
    void testAddNullItem() {
        StockException e = assertThrows(StockException.class, () -> stockService.addStockItem(null));
        assertEquals(StockException.Reason.NULL_ITEM, e.getReason());
    }

    @Test
    void testRemoveItem() throws StockException {
        stockService.removeStockItem(1);
        assertEquals(1, stockService.getAllStock().size());
    }

    @Test
    void testRemoveItemNotFound() {
        assertThrows(StockException.class, () -> stockService.removeStockItem(99));
    }

    @Test
    void test_unit_price_calculation() throws StockException {
        StockItem item = stockService.getStockItem(1);
        assertEquals(1.20, item.getUnitPrice(), 0.001);
    }

    @Test
    void test_price_inc_vat() throws StockException {
        StockItem item = stockService.getStockItem(1);
        assertEquals(1.26, item.getPriceIncVat(), 0.001);
    }
}
