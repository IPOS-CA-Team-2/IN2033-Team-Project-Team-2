package service;

import model.*;
import repository.CustomerRepository;

import java.time.LocalDate;
import java.util.List;

// business logic for account holder management
// handles credit checks, discount calculation, status state machine, and payments
public class AccountService {

    private final CustomerRepository customerRepository;

    public AccountService(CustomerRepository customerRepository) {
        if (customerRepository == null) throw new IllegalArgumentException("CustomerRepository cannot be null");
        this.customerRepository = customerRepository;
    }

    // --- credit limit check (step 19) ---

    // returns true if the account holder is allowed to make a purchase of this amount
    // only normal accounts can purchase — suspended and in default are both blocked
    public boolean canMakePurchase(Customer customer, double purchaseAmount) {
        if (customer.getStatus() != AccountStatus.NORMAL) return false;
        return (customer.getCurrentBalance() + purchaseAmount) <= customer.getCreditLimit();
    }

    // --- discount calculation (step 20) ---

    // returns the discount amount to deduct from the sale subtotal
    // fixed: applied immediately at point of sale
    // flexible: not applied at point of sale — calculated at month end, so returns 0 here
    public double calculatePointOfSaleDiscount(Customer customer, double subtotal) {
        if (customer.getDiscountType() == DiscountType.FIXED) {
            return subtotal * customer.getFixedDiscountRate();
        }
        // flexible discount is not applied at point of sale per brief spec
        return 0.0;
    }

    // calculates the flexible discount earned across a full calendar month
    // retail tiers: under £50 = 1%, £50-£100 = 3%, over £100 = 5%
    public double calculateFlexibleMonthEndDiscount(Customer customer) {
        if (customer.getDiscountType() != DiscountType.FLEXIBLE) return 0.0;

        double spend = customer.getMonthlySpend();
        double rate;
        if (spend > 100) {
            rate = 0.05;
        } else if (spend >= 50) {
            rate = 0.03;
        } else {
            rate = 0.01;
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

    // --- sale with account holder (step 21) ---

    // called after a successful account sale — adds to balance and monthly spend
    public boolean recordAccountSale(Customer customer, double saleTotal) {
        double newBalance = customer.getCurrentBalance() + saleTotal;
        double newMonthlySpend = customer.getMonthlySpend() + saleTotal;
        return customerRepository.updateBalance(customer.getCustomerId(), newBalance, newMonthlySpend);
    }

    // called when a payment is received from an account holder
    // per brief: if account not in default, reset reminder flags
    public boolean processPayment(Customer customer, double amount) {
        double newBalance = Math.max(0, customer.getCurrentBalance() - amount);

        customerRepository.updateBalance(customer.getCustomerId(), newBalance, customer.getMonthlySpend());

        // if not in default and balance is now cleared, reset reminder status per brief algorithm
        if (customer.getStatus() != AccountStatus.IN_DEFAULT && newBalance == 0) {
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

    // --- account status update logic (step 22) ---

    // runs the fig. 1 state machine for all accounts — should be called on app startup
    // and can be triggered manually
    // normal → suspended at 15th of month following statement date if balance unpaid
    // suspended → in default at end of that month if still unpaid
    public void updateAccountStatuses() {
        LocalDate today = LocalDate.now();
        List<Customer> customers = customerRepository.findAll();

        for (Customer customer : customers) {
            // no debt = nothing to change
            if (customer.getCurrentBalance() <= 0) continue;

            // no statement date means no debt period started yet
            LocalDate statementDate = customer.getStatementDate();
            if (statementDate == null) continue;

            // 15th of the month following the statement — suspension deadline
            LocalDate suspendDate = statementDate.plusMonths(1).withDayOfMonth(15);
            // end of that same month — default deadline
            int lastDay = statementDate.plusMonths(1).lengthOfMonth();
            LocalDate defaultDate = statementDate.plusMonths(1).withDayOfMonth(lastDay);

            if (customer.getStatus() == AccountStatus.NORMAL && !today.isBefore(suspendDate)) {
                // past the 15th and still unpaid — suspend the account
                customerRepository.updateStatus(
                    customer.getCustomerId(),
                    AccountStatus.SUSPENDED,
                    "due",
                    customer.getStatus2ndReminder(),
                    null, null,
                    statementDate
                );

            } else if (customer.getStatus() == AccountStatus.SUSPENDED && !today.isBefore(defaultDate)) {
                // past end of month and still unpaid — move to in default
                customerRepository.updateStatus(
                    customer.getCustomerId(),
                    AccountStatus.IN_DEFAULT,
                    customer.getStatus1stReminder(),
                    "due",
                    null, null,
                    statementDate
                );
            }
        }
    }

    // explicit manager intervention to restore an in-default account to normal
    // per brief: can only be done by authorised user (manager)
    public boolean restoreToNormal(Customer customer) {
        if (customer.getStatus() != AccountStatus.IN_DEFAULT) return false;

        return customerRepository.updateStatus(
            customer.getCustomerId(),
            AccountStatus.NORMAL,
            "no_need", "no_need",
            null, null,
            null
        );
    }

    // set the statement date to end of current month — called when generating monthly statements
    public boolean setStatementDate(Customer customer) {
        LocalDate endOfMonth = LocalDate.now()
            .withDayOfMonth(LocalDate.now().lengthOfMonth());

        return customerRepository.updateStatus(
            customer.getCustomerId(),
            customer.getStatus(),
            customer.getStatus1stReminder(),
            customer.getStatus2ndReminder(),
            customer.getDate1stReminder(),
            customer.getDate2ndReminder(),
            endOfMonth
        );
    }

    public CustomerRepository getCustomerRepository() { return customerRepository; }
}
