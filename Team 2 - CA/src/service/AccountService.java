package service;

import model.*;
import repository.CustomerRepository;
import repository.SaleRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

// handles account holder logic — credit checks, discounts, status changes etc
public class AccountService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository; // optional — used to derive statement dates from real sale history

    public AccountService(CustomerRepository customerRepository) {
        this(customerRepository, null);
    }

    public AccountService(CustomerRepository customerRepository, SaleRepository saleRepository) {
        if (customerRepository == null) throw new IllegalArgumentException("CustomerRepository cannot be null");
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
    }

    // only NORMAL accounts can buy — suspended and in default are blocked
    public boolean canMakePurchase(Customer customer, double purchaseAmount) {
        if (customer.getStatus() != AccountStatus.NORMAL) return false;
        return (customer.getCurrentBalance() + purchaseAmount) <= customer.getCreditLimit();
    }

    // discount at point of sale — fixed applies now, flexible is 0 (calculated at month end)
    public double calculatePointOfSaleDiscount(Customer customer, double subtotal) {
        if (customer.getDiscountType() == DiscountType.FIXED) {
            return subtotal * customer.getFixedDiscountRate();
        }
        // flexible discount is not applied at point of sale per brief spec
        return 0.0;
    }

    // month end flexible discount — tiers based on total monthly spend (per spec)
    // < £100 → 0%,  £100–£300 → 1%,  £300+ → 2%
    public double calculateFlexibleMonthEndDiscount(Customer customer) {
        if (customer.getDiscountType() != DiscountType.FLEXIBLE) return 0.0;

        double spend = customer.getMonthlySpend();
        double rate;
        if (spend >= 300) {
            rate = 0.02;
        } else if (spend >= 100) {
            rate = 0.01;
        } else {
            rate = 0.0;
        }

        return spend * rate;
    }

    // apply end-of-month flexible discount to a customer — reduces their outstanding balance
    public boolean applyFlexibleDiscount(Customer customer) {
        if (customer.getDiscountType() != DiscountType.FLEXIBLE) return false;

        double discount = calculateFlexibleMonthEndDiscount(customer);
        double newBalance = Math.max(0, customer.getCurrentBalance() - discount);
        // reset monthly spend to 0 for the new month
        return customerRepository.updateBalance(customer.getCustomerId(), newBalance, 0.0);
    }

    // called after a successful account sale — adds to balance and monthly spend
    public boolean recordAccountSale(Customer customer, double saleTotal) {
        double newBalance = customer.getCurrentBalance() + saleTotal;
        double newMonthlySpend = customer.getMonthlySpend() + saleTotal;
        return customerRepository.updateBalance(customer.getCustomerId(), newBalance, newMonthlySpend);
    }

    // called when a payment is received from an account holder
    // per brief: suspended accounts auto-restore to normal when balance is cleared
    //            in-default accounts cannot auto-restore — manager must do it manually
    public boolean processPayment(Customer customer, double amount) {
        double newBalance = Math.max(0, customer.getCurrentBalance() - amount);
        // round to 2dp to avoid floating point artifacts (e.g. 0.004 remaining after discount)
        newBalance = Math.round(newBalance * 100.0) / 100.0;

        customerRepository.updateBalance(customer.getCustomerId(), newBalance, customer.getMonthlySpend());

        // only auto-restore SUSPENDED accounts — IN_DEFAULT requires manager intervention
        if (customer.getStatus() == AccountStatus.SUSPENDED && newBalance < 0.01) {
            customerRepository.updateStatus(
                customer.getCustomerId(),
                AccountStatus.NORMAL,
                "no_need", "no_need",
                null, null,
                null
            );
        }

        return true;
    }

    // runs the status state machine — normal → suspended → in default
    // based on fig 1 in the brief
    public void updateAccountStatuses() {
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepository.findAll();

        for (Customer customer : customers) {
            // treat near-zero balances as cleared (avoids floating point artifacts like 0.004)
            double balance = Math.round(customer.getCurrentBalance() * 100.0) / 100.0;
            if (balance < 0.01) continue;

            // derive statement date from the customer's oldest sale so we use the correct
            // billing period — if no sale repo available, fall back to end of last month
            LocalDate statementDate = deriveStatementDate(customer, today);

            // persist it if it was missing or stale
            if (!statementDate.equals(customer.getStatementDate())) {
                setStatementDate(customer, statementDate);
            }

            // 15th of the month following the statement — suspension deadline
            LocalDate suspendDate  = statementDate.plusMonths(1).withDayOfMonth(15);
            // end of that same following month — default deadline
            LocalDate defaultDate  = statementDate.plusMonths(1)
                    .withDayOfMonth(statementDate.plusMonths(1).lengthOfMonth());

            AccountStatus current = customer.getStatus();

            if (current == AccountStatus.NORMAL && !today.isBefore(defaultDate)) {
                // past both deadlines in one go (e.g. very old purchase) — skip straight to in default
                customerRepository.updateStatus(customer.getCustomerId(),
                    AccountStatus.IN_DEFAULT, "due", "due", null, null, statementDate);

            } else if (current == AccountStatus.NORMAL && !today.isBefore(suspendDate)) {
                // past the 15th — suspend
                customerRepository.updateStatus(customer.getCustomerId(),
                    AccountStatus.SUSPENDED, "due", customer.getStatus2ndReminder(),
                    null, null, statementDate);

            } else if (current == AccountStatus.SUSPENDED && !today.isBefore(defaultDate)) {
                // still unpaid past end of month — in default
                customerRepository.updateStatus(customer.getCustomerId(),
                    AccountStatus.IN_DEFAULT, customer.getStatus1stReminder(),
                    "due", null, null, statementDate);
            }
        }
    }

    // derives the correct statement date for a customer:
    //   if we have a sale repo → use end of the month the oldest sale was made in
    //   otherwise → end of last month
    private LocalDate deriveStatementDate(Customer customer, LocalDate today) {
        if (saleRepository != null) {
            List<model.Sale> sales = saleRepository.findByCustomerId(customer.getCustomerId());
            if (sales != null && !sales.isEmpty()) {
                LocalDate oldest = sales.stream()
                    .map(s -> s.getSaleDate().toLocalDate())
                    .min(Comparator.naturalOrder())
                    .orElse(null);
                if (oldest != null) {
                    return oldest.withDayOfMonth(oldest.lengthOfMonth());
                }
            }
        }
        // fallback — end of last month
        LocalDate lastMonth = today.minusMonths(1);
        return lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
    }

    // explicit manager intervention to restore an in-default account to normal
    // per brief: can only be done by authorised user (manager)
    public boolean restoreToNormal(Customer customer) {
        if (customer.getStatus() != AccountStatus.IN_DEFAULT) return false;

        // balance must be fully cleared before an in-default account can be restored
        double balance = Math.round(customer.getCurrentBalance() * 100.0) / 100.0;
        if (balance >= 0.01) return false;

        return customerRepository.updateStatus(
            customer.getCustomerId(),
            AccountStatus.NORMAL,
            "no_need", "no_need",
            null, null,
            null
        );
    }

    // set the statement date to end of the previous month — called when generating monthly statements
    // e.g. run in April → statement_date = 31 March → suspension deadline = 15 April
    public boolean setStatementDate(Customer customer) {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        return setStatementDate(customer, lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
    }

    // set an explicit statement date — used internally when deriving from sale history
    public boolean setStatementDate(Customer customer, LocalDate statementDate) {
        return customerRepository.updateStatus(
            customer.getCustomerId(),
            customer.getStatus(),
            customer.getStatus1stReminder(),
            customer.getStatus2ndReminder(),
            customer.getDate1stReminder(),
            customer.getDate2ndReminder(),
            statementDate
        );
    }

    public CustomerRepository getCustomerRepository() { return customerRepository; }
}
