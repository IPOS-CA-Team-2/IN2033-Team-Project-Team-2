package test;

import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import repository.CustomerRepository;
import service.AccountService;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class AccountServiceTest {

    // fake customer repo - no db needed
    class FakeCustomerRepo implements CustomerRepository {
        Map<Integer, Customer> customers = new HashMap<>();
        int idCount = 1;
        AccountStatus lastStatus;
        double lastBalance;
        double lastSpend;

        public List<Customer> findAll() { return new ArrayList<>(customers.values()); }
        public Customer findById(int id) { return customers.get(id); }
        public Customer findByAccountNumber(String num) {
            for(Customer c : customers.values()) {
                if(c.getAccountNumber().equals(num)) return c;
            }
            return null;
        }
        public int save(Customer c) { customers.put(idCount, c); return idCount++; }
        public boolean update(Customer c) { customers.put(c.getCustomerId(), c); return true; }
        public boolean updateBalance(int id, double bal, double spend) {
            lastBalance = bal;
            lastSpend = spend;
            return true;
        }
        public boolean updateStatus(int id, AccountStatus s, String r1, String r2, LocalDate d1, LocalDate d2, LocalDate stmt) {
            lastStatus = s;
            return true;
        }
        public boolean delete(int id) { return customers.remove(id) != null; }
        public String generateAccountNumber() { return "CSM00000" + idCount; }
    }

    FakeCustomerRepo repo;
    AccountService accountService;

    @BeforeEach
    void init() {
        repo = new FakeCustomerRepo();
        accountService = new AccountService(repo);
    }

    // helper to build a customer quickly
    Customer buildCustomer(AccountStatus status, double limit, double balance, double spend, DiscountType dtype, double rate) {
        return new Customer(1, "Test Person", "123 Fake St", "CSM000001",
            limit, balance, spend, dtype, rate, status,
            "no_need", "no_need", null, null, null);
    }

    @Test
    void test_null_repo_throws() {
        assertThrows(IllegalArgumentException.class, () -> new AccountService(null));
    }

    @Test
    void testCanBuyNormalWithinLimit() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 100, 0, DiscountType.NONE, 0);
        assertTrue(accountService.canMakePurchase(c, 50));
    }

    @Test
    void testCannotBuyOverLimit() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 490, 0, DiscountType.NONE, 0);
        assertFalse(accountService.canMakePurchase(c, 20));
    }

    @Test
    void testExactlyAtLimitIsAllowed() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 450, 0, DiscountType.NONE, 0);
        assertTrue(accountService.canMakePurchase(c, 50.0));
    }

    @Test
    void testSuspendedCantBuy() {
        Customer c = buildCustomer(AccountStatus.SUSPENDED, 500, 10, 0, DiscountType.NONE, 0);
        // suspended accounts cant buy regardless of balance
        assertFalse(accountService.canMakePurchase(c, 5));
    }

    @Test
    void testInDefaultCantBuy() {
        Customer c = buildCustomer(AccountStatus.IN_DEFAULT, 500, 10, 0, DiscountType.NONE, 0);
        assertFalse(accountService.canMakePurchase(c, 5));
    }

    @Test
    void testFixedDiscountCalculation() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 0, DiscountType.FIXED, 0.10);
        double discount = accountService.calculatePointOfSaleDiscount(c, 100.0);
        assertEquals(10.0, discount, 0.001);
    }

    @Test
    void testFlexibleDiscountIsZeroAtSale() {
        // flexible discounts are calculated at month end, not at sale
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 200, DiscountType.FLEXIBLE, 0);
        assertEquals(0.0, accountService.calculatePointOfSaleDiscount(c, 100), 0.001);
    }

    @Test
    void testNoDiscountCustomer() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 0, DiscountType.NONE, 0);
        assertEquals(0.0, accountService.calculatePointOfSaleDiscount(c, 100), 0.001);
    }

    @Test
    void testFlexibleDiscount_highSpend() {
        // over £100 spend = 5% discount
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 150, DiscountType.FLEXIBLE, 0);
        assertEquals(7.50, accountService.calculateFlexibleMonthEndDiscount(c), 0.001);
    }

    @Test
    void testFlexibleDiscount_midSpend() {
        // between 50-100 = 3%
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 75, DiscountType.FLEXIBLE, 0);
        assertEquals(2.25, accountService.calculateFlexibleMonthEndDiscount(c), 0.001);
    }

    @Test
    void testFlexibleDiscount_lowSpend() {
        // under 50 = 1%
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 30, DiscountType.FLEXIBLE, 0);
        assertEquals(0.30, accountService.calculateFlexibleMonthEndDiscount(c), 0.001);
    }

    @Test
    void testFlexibleDiscountNotAppliedToFixedCustomer() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 200, DiscountType.FIXED, 0.10);
        assertEquals(0.0, accountService.calculateFlexibleMonthEndDiscount(c), 0.001);
    }

    @Test
    void testRecordSaleUpdatesBalance() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 50, 0, DiscountType.NONE, 0);
        accountService.recordAccountSale(c, 25.0);
        assertEquals(75.0, repo.lastBalance, 0.001);
    }

    @Test
    void testRecordSaleUpdatesSpend() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 50, 100, DiscountType.NONE, 0);
        accountService.recordAccountSale(c, 30.0);
        assertEquals(130.0, repo.lastSpend, 0.001);
    }

    @Test
    void testPaymentReducesBalance() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 100, 0, DiscountType.NONE, 0);
        accountService.processPayment(c, 40);
        assertEquals(60.0, repo.lastBalance, 0.001);
    }

    @Test
    void testPaymentCantGoBelowZero() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 30, 0, DiscountType.NONE, 0);
        accountService.processPayment(c, 200); // paying more than owed
        assertEquals(0.0, repo.lastBalance, 0.001);
    }

    @Test
    void testRestoreDefaultAccount() {
        Customer c = buildCustomer(AccountStatus.IN_DEFAULT, 500, 0, 0, DiscountType.NONE, 0);
        boolean result = accountService.restoreToNormal(c);
        assertTrue(result);
        assertEquals(AccountStatus.NORMAL, repo.lastStatus);
    }

    @Test
    void testRestoreNormalDoesNothing() {
        Customer c = buildCustomer(AccountStatus.NORMAL, 500, 0, 0, DiscountType.NONE, 0);
        assertFalse(accountService.restoreToNormal(c));
    }

    @Test
    void testRestoreSuspendedFails() {
        // can only restore from IN_DEFAULT not suspended
        Customer c = buildCustomer(AccountStatus.SUSPENDED, 500, 50, 0, DiscountType.NONE, 0);
        assertFalse(accountService.restoreToNormal(c));
    }
}
