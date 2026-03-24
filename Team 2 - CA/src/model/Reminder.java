package model;

import java.time.LocalDate;

// represents a generated payment reminder letter for an account holder
// first reminder is sent when account is suspended, second when heading to in default
public class Reminder {

    public enum Type { FIRST, SECOND }

    private final int reminderId;
    private final int customerId;
    private final String customerName;
    private final String accountNumber;
    private final String customerAddress;
    private final Type type;
    private final LocalDate generatedDate;
    private final double outstandingBalance;
    private final String letterText;

    public Reminder(int reminderId, int customerId, String customerName,
                    String accountNumber, String customerAddress,
                    Type type, LocalDate generatedDate, double outstandingBalance,
                    String letterText) {
        this.reminderId = reminderId;
        this.customerId = customerId;
        this.customerName = customerName;
        this.accountNumber = accountNumber;
        this.customerAddress = customerAddress;
        this.type = type;
        this.generatedDate = generatedDate;
        this.outstandingBalance = outstandingBalance;
        this.letterText = letterText;
    }

    public int getReminderId()            { return reminderId; }
    public int getCustomerId()            { return customerId; }
    public String getCustomerName()       { return customerName; }
    public String getAccountNumber()      { return accountNumber; }
    public String getCustomerAddress()    { return customerAddress; }
    public Type getType()                 { return type; }
    public LocalDate getGeneratedDate()   { return generatedDate; }
    public double getOutstandingBalance() { return outstandingBalance; }
    public String getLetterText()         { return letterText; }

    @Override
    public String toString() {
        return "Reminder{type=" + type + ", account='" + accountNumber + "', date=" + generatedDate + "}";
    }
}
