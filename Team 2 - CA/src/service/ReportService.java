package service;

import model.*;
import repository.CustomerRepository;
import repository.SaleRepository;
import repository.WholesaleOrderRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// central reporting service, gathers data from the repos and returns typed report objects
// all report logic lives here so the UI classes stay thin
public class ReportService {

    private final SaleRepository saleRepo;
    private final CustomerRepository customerRepo;
    private final StockService stockService;
    private final WholesaleOrderRepository orderRepo;

    public ReportService(SaleRepository saleRepo, CustomerRepository customerRepo,
                         StockService stockService, WholesaleOrderRepository orderRepo) {
        this.saleRepo = saleRepo;
        this.customerRepo = customerRepo;
        this.stockService = stockService;
        this.orderRepo = orderRepo;
    }

    // data classes returned by each report method

    public static class TurnoverReport {
        public final LocalDate from;
        public final LocalDate to;
        public final int saleCount;
        public final double totalRevenue;
        public final int cashCount;
        public final int cardCount;
        public final int orderCount;
        public final double orderTotal;
        public final List<SaleRow> rows;

        TurnoverReport(LocalDate from, LocalDate to, int saleCount, double totalRevenue,
                       int cashCount, int cardCount, int orderCount, double orderTotal, List<SaleRow> rows) {
            this.from = from; this.to = to; this.saleCount = saleCount;
            this.totalRevenue = totalRevenue; this.cashCount = cashCount;
            this.cardCount = cardCount; this.orderCount = orderCount;
            this.orderTotal = orderTotal; this.rows = rows;
        }
    }

    public static class SaleRow {
        public final int saleId;
        public final LocalDate date;
        public final String customerLabel; // "Walk-in" or "Acct #X"
        public final String paymentMethod;
        public final double total;

        SaleRow(int saleId, LocalDate date, String customerLabel, String paymentMethod, double total) {
            this.saleId = saleId; this.date = date; this.customerLabel = customerLabel;
            this.paymentMethod = paymentMethod; this.total = total;
        }
    }

    public static class StockReport {
        public final LocalDate generatedAt;
        public final double totalStockValue;
        public final List<StockRow> rows;

        StockReport(LocalDate generatedAt, double totalStockValue, List<StockRow> rows) {
            this.generatedAt = generatedAt; this.totalStockValue = totalStockValue; this.rows = rows;
        }
    }

    public static class StockRow {
        public final String name;
        public final int quantity;
        public final double unitPriceIncVat;
        public final double stockValue;
        public final boolean lowStock;

        StockRow(String name, int quantity, double unitPriceIncVat, double stockValue, boolean lowStock) {
            this.name = name; this.quantity = quantity; this.unitPriceIncVat = unitPriceIncVat;
            this.stockValue = stockValue; this.lowStock = lowStock;
        }
    }

    public static class DebtReport {
        public final LocalDate generatedAt;
        public final double totalDebt;
        public final int debtorCount;
        public final List<DebtRow> rows;

        DebtReport(LocalDate generatedAt, double totalDebt, int debtorCount, List<DebtRow> rows) {
            this.generatedAt = generatedAt; this.totalDebt = totalDebt;
            this.debtorCount = debtorCount; this.rows = rows;
        }
    }

    public static class DebtRow {
        public final String accountNumber;
        public final String name;
        public final AccountStatus status;
        public final double balance;
        public final double creditLimit;
        public final double utilisationPct;

        DebtRow(String accountNumber, String name, AccountStatus status,
                double balance, double creditLimit, double utilisationPct) {
            this.accountNumber = accountNumber; this.name = name; this.status = status;
            this.balance = balance; this.creditLimit = creditLimit; this.utilisationPct = utilisationPct;
        }
    }

    public TurnoverReport generateTurnoverReport(LocalDate from, LocalDate to) {
        List<Sale> sales = saleRepo.findByDateRange(from, to);

        int saleCount = sales.size();
        double totalRevenue = 0;
        int cashCount = 0;
        int cardCount = 0;
        List<SaleRow> rows = new ArrayList<>();

        for (Sale s : sales) {
            totalRevenue += s.getTotalIncVat();
            if (s.getPaymentMethod() == PaymentMethod.CASH) cashCount++;
            else                                            cardCount++;
            String custLabel = s.getCustomerId() == 0 ? "Walk-in" : "Acct #" + s.getCustomerId();
            rows.add(new SaleRow(s.getSaleId(), s.getSaleDate().toLocalDate(),
                    custLabel, s.getPaymentMethod().name(), s.getTotalIncVat()));
        }

        // wholesale orders placed in the same range
        List<WholesaleOrder> orders = orderRepo.findAll().stream()
                .filter(o -> !o.getOrderDate().isBefore(from) && !o.getOrderDate().isAfter(to))
                .collect(Collectors.toList());

        int orderCount = orders.size();
        double orderTotal = orders.stream().mapToDouble(WholesaleOrder::getTotalValue).sum();

        return new TurnoverReport(from, to, saleCount, totalRevenue, cashCount, cardCount, orderCount, orderTotal, rows);
    }

    public static class LowStockRow {
        public final String itemCode;
        public final String name;
        public final String packageType;
        public final String unit;
        public final int quantity;
        public final int threshold;

        LowStockRow(String itemCode, String name, String packageType, String unit, int quantity, int threshold) {
            this.itemCode = itemCode; this.name = name; this.packageType = packageType;
            this.unit = unit; this.quantity = quantity; this.threshold = threshold;
        }
    }

    public static class LowStockReport {
        public final LocalDate generatedAt;
        public final int lowStockCount;
        public final List<LowStockRow> rows;

        LowStockReport(LocalDate generatedAt, int lowStockCount, List<LowStockRow> rows) {
            this.generatedAt = generatedAt; this.lowStockCount = lowStockCount; this.rows = rows;
        }
    }

    // returns only items that are at or below their stock threshold
    public LowStockReport generateLowStockReport() {
        List<StockItem> items = stockService.getAllStock();
        List<LowStockRow> rows = new ArrayList<>();

        for (StockItem item : items) {
            if (item.isLowStock()) {
                rows.add(new LowStockRow(
                    item.getItemCode(), item.getName(),
                    item.getPackageType(), item.getUnit(),
                    item.getQuantity(), item.getLowStockThreshold()
                ));
            }
        }

        return new LowStockReport(LocalDate.now(), rows.size(), rows);
    }

    // stock report shows the current state, no date range needed
    public StockReport generateStockReport() {
        List<StockItem> items = stockService.getAllStock();
        List<StockRow> rows = new ArrayList<>();
        double total = 0;

        for (StockItem item : items) {
            double unitPrice = item.getPriceIncVat();
            double stockValue = item.getQuantity() * unitPrice;
            total += stockValue;
            rows.add(new StockRow(item.getName(), item.getQuantity(), unitPrice, stockValue, item.isLowStock()));
        }

        return new StockReport(LocalDate.now(), total, rows);
    }

    // NOTE: this is a snapshot of current balances, not historical
    // would need payment timestamps to do a proper period report but we dont store those
    public DebtReport generateDebtReport() {
        List<Customer> customers = customerRepo.findAll();
        List<DebtRow> rows = new ArrayList<>();
        double totalDebt = 0;
        int debtorCount = 0;

        for (Customer c : customers) {
            double bal = c.getCurrentBalance();
            double util = c.getCreditLimit() > 0 ? (bal / c.getCreditLimit() * 100.0) : 0.0;
            rows.add(new DebtRow(c.getAccountNumber(), c.getName(), c.getStatus(), bal, c.getCreditLimit(), util));
            if (bal > 0) { totalDebt += bal; debtorCount++; }
        }

        return new DebtReport(LocalDate.now(), totalDebt, debtorCount, rows);
    }
}
