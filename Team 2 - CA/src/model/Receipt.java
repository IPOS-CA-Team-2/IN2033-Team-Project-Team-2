package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// represents the output of a completed sale
// for account customers this formats as a formal invoice letter (per brief appendix 7)
// for cash/walk-in customers it formats as a simple till receipt
public class Receipt {

    // pharmacy details pulled from configurable settings — managers can update via templates screen

    private final String receiptNumber;
    private final Sale sale;
    private final String cashierName;
    private final LocalDateTime issuedAt;

    // optional — only set for account customers
    private final String customerName;
    private final String customerAddress;
    private final String accountNumber;

    // constructor for account customers — full invoice format
    public Receipt(String receiptNumber, Sale sale, String cashierName,
                   String customerName, String customerAddress, String accountNumber) {
        if (receiptNumber == null || receiptNumber.isBlank()) throw new IllegalArgumentException("receipt number cannot be blank");
        if (sale == null) throw new IllegalArgumentException("sale cannot be null");
        if (cashierName == null || cashierName.isBlank()) throw new IllegalArgumentException("cashier name cannot be blank");

        this.receiptNumber = receiptNumber;
        this.sale = sale;
        this.cashierName = cashierName;
        this.issuedAt = LocalDateTime.now();
        this.customerName = customerName;
        this.customerAddress = customerAddress;
        this.accountNumber = accountNumber;
    }

    // constructor for walk-in cash customers — no account info needed
    public Receipt(String receiptNumber, Sale sale, String cashierName) {
        this(receiptNumber, sale, cashierName, null, null, null);
    }

    // formats the receipt — invoice style for account customers, simple receipt for cash
    public String format() {
        if (customerName != null) {
            return formatInvoice();
        } else {
            return formatCashReceipt();
        }
    }

    // formal invoice letter per brief appendix 7 — used for account customers
    private String formatInvoice() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMMM yyyy");
        StringBuilder sb = new StringBuilder();

        // address block — customer left, pharmacy right
        String[] custLines = customerAddress != null ? customerAddress.split(",") : new String[]{};

        repository.ConfigRepository cfg = new repository.ConfigRepository(); // replaced with template text so everythign is modifiable
        String[] pharmLines = {cfg.get("pharmacy_name"), cfg.get("pharmacy_address"),
                cfg.get("pharmacy_phone"), cfg.get("pharmacy_email")};

        sb.append(String.format("%-40s%s%n", customerName, pharmLines[0]));
        for (int i = 0; i < Math.max(custLines.length, pharmLines.length - 1); i++) {
            String left  = i < custLines.length ? custLines[i].trim() : "";
            String right = (i + 1) < pharmLines.length ? pharmLines[i + 1] : "";
            sb.append(String.format("%-40s%s%n", left, right));
        }

        sb.append("\n");
        sb.append(String.format("%-40s%s%n", "", issuedAt.format(fmt)));
        sb.append("\n");
        sb.append("Dear ").append(customerName).append(",\n\n");
        sb.append(String.format("%45s%n%n", "INVOICE NO.: " + receiptNumber));

        if (accountNumber != null) {
            sb.append("Account No: ").append(accountNumber).append("\n\n");
        }

        // item table
        sb.append(String.format("%-20s %-10s %-16s %s%n", "Item ID", "Packages", "Package Cost, £", "Amount, £"));
        sb.append("-".repeat(62)).append("\n");

        double subtotal = 0;
        for (SaleLine line : sale.getLines()) {
            double amount = line.getLineTotal();
            subtotal += amount;
            sb.append(String.format("%-20s %-10d %-16.2f %.2f%n",
                line.getItemName(), line.getQuantity(), line.getUnitPrice(), amount));
        }

        // totals block
        sb.append(String.format("%48s %-10s %.2f%n", "", "Total", subtotal));
        double vatAmount = sale.getTotalIncVat() - sale.getTotal();
        if (vatAmount > 0.001) {
            sb.append(String.format("%48s %-10s %.2f%n", "", "VAT @ " +
                String.format("%.1f%%", sale.getLines().get(0).getVatRate() * 100), vatAmount));
        }
        if (sale.getDiscountPercent() > 0) {
            sb.append(String.format("%48s %-10s %.2f%n", "", "Discount", -sale.getDiscountAmount()));
        }
        sb.append(String.format("%48s %-10s %.2f%n", "", "Amount Due", sale.getTotalIncVat()));

        sb.append("\n\n").append(new repository.ConfigRepository().get("receipt_footer")).append("\n"); // replaced with template
        sb.append("\n\nYours sincerely,\n\n\n\n");
        sb.append(cashierName).append("\n");

        return sb.toString();
    }

    // simple till-style receipt for walk-in cash customers
    private String formatCashReceipt() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("========================================\n");
        sb.append("        ").append(new repository.ConfigRepository().get("pharmacy_name").toUpperCase()).append("\n");
        sb.append("========================================\n");
        sb.append("Receipt No: ").append(receiptNumber).append("\n");
        sb.append("Date:       ").append(issuedAt.format(fmt)).append("\n");
        sb.append("Cashier:    ").append(cashierName).append("\n");
        sb.append("----------------------------------------\n");
        sb.append(String.format("%-20s %6s %10s%n", "Item", "Qty", "Amount"));
        sb.append("----------------------------------------\n");

        for (SaleLine line : sale.getLines()) {
            sb.append(String.format("%-20s %6d %10.2f%n",
                line.getItemName(), line.getQuantity(), line.getLineTotal()));
        }

        sb.append("----------------------------------------\n");
        sb.append(String.format("%-28s %8.2f%n", "Subtotal:", sale.getSubtotal()));

        if (sale.getDiscountPercent() > 0) {
            sb.append(String.format("%-28s-%7.2f%n",
                String.format("Discount (%.0f%%):", sale.getDiscountPercent() * 100),
                sale.getDiscountAmount()));
        }

        double vatAmount = sale.getTotalIncVat() - sale.getTotal();
        if (vatAmount > 0.001) {
            sb.append(String.format("%-28s %8.2f%n", "VAT:", vatAmount));
        }

        sb.append(String.format("%-28s %8.2f%n", "TOTAL:", sale.getTotalIncVat()));
        sb.append("========================================\n");
        sb.append("  ").append(new repository.ConfigRepository().get("receipt_footer")).append("\n"); // replaced with template
        sb.append("========================================\n");

        return sb.toString();
    }

    public String getReceiptNumber()  { return receiptNumber; }
    public Sale getSale()             { return sale; }
    public String getCashierName()    { return cashierName; }
    public LocalDateTime getIssuedAt(){ return issuedAt; }
    public String getCustomerName()   { return customerName; }
    public String getAccountNumber()  { return accountNumber; }

    @Override
    public String toString() {
        return "Receipt{receiptNumber='" + receiptNumber + "', total=" + sale.getTotalIncVat()
                + ", cashier='" + cashierName + "'}";
    }
}
