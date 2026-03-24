package model;

import java.time.LocalDate;

// represents an account holder — a regular customer with credit, discount, and status tracking
// pharmacist identifies them by account number at point of sale
public class Customer {

    private final int customerId;
    private final String name;
    private final String address;
    private final String accountNumber;   // e.g. CSM000123 — used to identify at till
    private final double creditLimit;     // max outstanding balance allowed
    private final double currentBalance;  // current unpaid debt
    private final double monthlySpend;    // total spend this calendar month (for flexible discount)
    private final DiscountType discountType;
    private final double fixedDiscountRate; // e.g. 0.10 for 10% — only used when type is FIXED
    private final AccountStatus status;

    // reminder attributes per brief spec — values: no_need / due / sent
    private final String status1stReminder;
    private final String status2ndReminder;
    private final LocalDate date1stReminder;  // null means 'now'
    private final LocalDate date2ndReminder;
    private final LocalDate statementDate;    // end of the month the debt was incurred

    public Customer(int customerId, String name, String address, String accountNumber,
                    double creditLimit, double currentBalance, double monthlySpend,
                    DiscountType discountType, double fixedDiscountRate,
                    AccountStatus status, String status1stReminder, String status2ndReminder,
                    LocalDate date1stReminder, LocalDate date2ndReminder, LocalDate statementDate) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("customer name cannot be blank");
        if (accountNumber == null || accountNumber.isBlank()) throw new IllegalArgumentException("account number cannot be blank");
        if (creditLimit < 0) throw new IllegalArgumentException("credit limit cannot be negative");
        if (discountType == null) throw new IllegalArgumentException("discount type cannot be null");
        if (status == null) throw new IllegalArgumentException("account status cannot be null");

        this.customerId = customerId;
        this.name = name;
        this.address = address;
        this.accountNumber = accountNumber;
        this.creditLimit = creditLimit;
        this.currentBalance = currentBalance;
        this.monthlySpend = monthlySpend;
        this.discountType = discountType;
        this.fixedDiscountRate = fixedDiscountRate;
        this.status = status;
        this.status1stReminder = status1stReminder != null ? status1stReminder : "no_need";
        this.status2ndReminder = status2ndReminder != null ? status2ndReminder : "no_need";
        this.date1stReminder = date1stReminder;
        this.date2ndReminder = date2ndReminder;
        this.statementDate = statementDate;
    }

    // convenience constructor for creating a new account holder with defaults
    public Customer(String name, String address, String accountNumber,
                    double creditLimit, DiscountType discountType, double fixedDiscountRate) {
        this(0, name, address, accountNumber, creditLimit, 0.0, 0.0,
             discountType, fixedDiscountRate, AccountStatus.NORMAL,
             "no_need", "no_need", null, null, null);
    }

    public int getCustomerId()          { return customerId; }
    public String getName()             { return name; }
    public String getAddress()          { return address; }
    public String getAccountNumber()    { return accountNumber; }
    public double getCreditLimit()      { return creditLimit; }
    public double getCurrentBalance()   { return currentBalance; }
    public double getMonthlySpend()     { return monthlySpend; }
    public DiscountType getDiscountType(){ return discountType; }
    public double getFixedDiscountRate(){ return fixedDiscountRate; }
    public AccountStatus getStatus()    { return status; }
    public String getStatus1stReminder(){ return status1stReminder; }
    public String getStatus2ndReminder(){ return status2ndReminder; }
    public LocalDate getDate1stReminder(){ return date1stReminder; }
    public LocalDate getDate2ndReminder(){ return date2ndReminder; }
    public LocalDate getStatementDate() { return statementDate; }

    @Override
    public String toString() {
        return "Customer{id=" + customerId + ", name='" + name + "', account='" + accountNumber
                + "', balance=" + currentBalance + ", status=" + status + "}";
    }
}
