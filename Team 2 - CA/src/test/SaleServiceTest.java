package test;

import exception.SaleException;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.CustomerRepository;
import repository.SaleRepository;
import repository.StockRepository;
import service.AccountService;
import service.SaleService;
import service.StockService;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class SaleServiceTest {

    // stub stock repo
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

    // stub sale repo
    class FakeSaleRepo implements SaleRepository {
        int counter = 1;
        List<Sale> sales = new ArrayList<>();
        public int save(Sale s) { sales.add(s); return counter++; }
        public Sale findById(int id) { return null; }
        public List<Sale> findAll() { return sales; }
        public List<Sale> findByCustomerId(int id) { return new ArrayList<>(); }
        public List<Sale> findByDateRange(LocalDate f, LocalDate t) { return new ArrayList<>(); }
        public List<Sale> findUnpaidAccountSales() { return new ArrayList<>(); }
        public boolean markAsPaid(int id) { return true; }
    }

    // stub customer repo
    class FakeCustomerRepo implements CustomerRepository {
        public List<Customer> findAll() { return new ArrayList<>(); }
        public Customer findById(int id) { return null; }
        public Customer findByAccountNumber(String n) { return null; }
        public int save(Customer c) { return 1; }
        public boolean update(Customer c) { return true; }
        public boolean updateBalance(int id, double b, double s) { return true; }
        public boolean updateStatus(int id, AccountStatus s, String r1, String r2, LocalDate d1, LocalDate d2, LocalDate stmt) { return true; }
        public boolean delete(int id) { return true; }
        public String generateAccountNumber() { return "CSM000001"; }
    }

    FakeStockRepo stockRepo;
    FakeSaleRepo saleRepo;
    SaleService service;
    SaleService serviceWithAccount;

    @BeforeEach
    void setup() {
        stockRepo = new FakeStockRepo();
        saleRepo = new FakeSaleRepo();

        stockRepo.save(new StockItem(1, "Paracetamol 500mg", 100, 1.00, 0.20, 0.05, 10));
        stockRepo.save(new StockItem(2, "Ibuprofen 200mg", 3, 1.50, 0.20, 0.05, 10));

        StockService ss = new StockService(stockRepo);
        AccountService as = new AccountService(new FakeCustomerRepo());

        service = new SaleService(ss, saleRepo);
        serviceWithAccount = new SaleService(ss, saleRepo, as);
    }

    // helper to make a sale line for item 1
    List<SaleLine> makeLine(int itemId, int qty) {
        return List.of(new SaleLine(itemId, "Paracetamol 500mg", qty, 1.20, 0.05));
    }

    CardDetails makeCard() {
        return new CardDetails("Visa", "1234", "5678", "12/28");
    }

    @Test
    void testNullStockService() {
        assertThrows(IllegalArgumentException.class, () -> new SaleService(null, saleRepo));
    }

    @Test
    void testNullSaleRepo() {
        assertThrows(IllegalArgumentException.class, () -> new SaleService(new StockService(stockRepo), null));
    }

    @Test
    void testCashSaleReturnsReceipt() throws SaleException {
        Receipt r = service.processSale(0, makeLine(1, 5), 0, PaymentMethod.CASH, null, "Alice");
        assertNotNull(r);
    }

    @Test
    void testReceiptNumberFormat() throws SaleException {
        Receipt r = service.processSale(0, makeLine(1, 5), 0, PaymentMethod.CASH, null, "Alice");
        assertTrue(r.getReceiptNumber().startsWith("RCP-"));
    }

    @Test
    void testStockDeductedAfterSale() throws SaleException {
        service.processSale(0, makeLine(1, 10), 0, PaymentMethod.CASH, null, "Alice");
        StockItem s = stockRepo.findById(1);
        assertEquals(90, s.getQuantity());
    }

    @Test
    void testEmptyLinesthrowsError() {
        // empty lines should not work
        SaleException e = assertThrows(SaleException.class,
            () -> service.processSale(0, new ArrayList<>(), 0, PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.EMPTY_SALE, e.getReason());
    }

    @Test
    void testNullLinesThrows() {
        SaleException e = assertThrows(SaleException.class,
            () -> service.processSale(0, null, 0, PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.EMPTY_SALE, e.getReason());
    }

    @Test
    void testNotEnoughStock() {
        // item 2 only has 3 in stock
        List<SaleLine> lines = List.of(new SaleLine(2, "Ibuprofen 200mg", 10, 1.80, 0.05));
        SaleException e = assertThrows(SaleException.class,
            () -> service.processSale(0, lines, 0, PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.INSUFFICIENT_STOCK, e.getReason());
    }

    @Test
    void testCreditCardSaleWorks() throws SaleException {
        Receipt r = service.processSale(0, makeLine(1, 2), 0, PaymentMethod.CREDIT_CARD, makeCard(), "Bob");
        assertNotNull(r);
    }

    @Test
    void testCardWithNoDetailsFails() {
        SaleException e = assertThrows(SaleException.class,
            () -> service.processSale(0, makeLine(1, 2), 0, PaymentMethod.CREDIT_CARD, null, "Bob"));
        assertEquals(SaleException.Reason.INVALID_PAYMENT, e.getReason());
    }

    @Test
    void testDebitCardNoDetailsFails() {
        SaleException e = assertThrows(SaleException.class,
            () -> service.processSale(0, makeLine(1, 2), 0, PaymentMethod.DEBIT_CARD, null, "Bob"));
        assertEquals(SaleException.Reason.INVALID_PAYMENT, e.getReason());
    }

    @Test
    void testAccountSaleWithinLimit() throws SaleException {
        Customer c = makeCustomer(AccountStatus.NORMAL, 500.0, 0.0);
        Receipt r = serviceWithAccount.processSaleForAccount(c, makeLine(1, 5), PaymentMethod.CASH, null, "Alice");
        assertNotNull(r);
    }

    @Test
    void testAccountSaleExceedsLimit() {
        Customer c = makeCustomer(AccountStatus.NORMAL, 5.0, 4.50);
        SaleException e = assertThrows(SaleException.class, () ->
            serviceWithAccount.processSaleForAccount(c, makeLine(1, 5), PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.CREDIT_LIMIT_EXCEEDED, e.getReason());
    }

    @Test
    void testSuspendedAccountCantBuy() {
        Customer c = makeCustomer(AccountStatus.SUSPENDED, 500.0, 0.0);
        SaleException e = assertThrows(SaleException.class, () ->
            serviceWithAccount.processSaleForAccount(c, makeLine(1, 1), PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.CREDIT_LIMIT_EXCEEDED, e.getReason());
    }

    @Test
    void testDefaultAccountCantBuy() {
        Customer c = makeCustomer(AccountStatus.IN_DEFAULT, 500.0, 0.0);
        SaleException e = assertThrows(SaleException.class, () ->
            serviceWithAccount.processSaleForAccount(c, makeLine(1, 1), PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.CREDIT_LIMIT_EXCEEDED, e.getReason());
    }

    @Test
    void testFixedDiscountApplied() throws SaleException {
        // 10% fixed discount on £12 = £1.20 off so total should be £10.80
        Customer c = new Customer(1, "Test User", "Test User", "", "123 St", "ACC0001", 500.0, 0.0, 0.0,
            DiscountType.FIXED, 0.10, AccountStatus.NORMAL, "no_need", "no_need", null, null, null);
        Receipt r = serviceWithAccount.processSaleForAccount(c, makeLine(1, 10), PaymentMethod.CASH, null, "Alice");
        assertEquals(10.80, r.getSale().getTotal(), 0.01);
    }

    @Test
    void testNullCustomerThrows() {
        SaleException e = assertThrows(SaleException.class, () ->
            serviceWithAccount.processSaleForAccount(null, makeLine(1,1), PaymentMethod.CASH, null, "Alice"));
        assertEquals(SaleException.Reason.SAVE_FAILED, e.getReason());
    }

    // helper
    Customer makeCustomer(AccountStatus status, double limit, double balance) {
        return new Customer(1, "Test Customer", "Test Customer", "", "1 Test St", "ACC0001", limit, balance, 0.0,
            DiscountType.NONE, 0.0, status, "no_need", "no_need", null, null, null);
    }
}
