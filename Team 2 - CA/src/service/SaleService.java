package service;

import exception.SaleException;
import exception.StockException;
import model.*;
import repository.SaleRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// handles the full sale workflow — validate stock, credit check, discount, persist, update stock, return receipt
public class SaleService {

    private final StockService stockService;
    private final SaleRepository saleRepository;
    private final AccountService accountService;

    // constructor without account service — for walk-in cash sales only
    public SaleService(StockService stockService, SaleRepository saleRepository) {
        this(stockService, saleRepository, null);
    }

    // full constructor — use this when account customer sales are needed
    public SaleService(StockService stockService, SaleRepository saleRepository, AccountService accountService) {
        if (stockService == null) throw new IllegalArgumentException("StockService cannot be null");
        if (saleRepository == null) throw new IllegalArgumentException("SaleRepository cannot be null");
        this.stockService = stockService;
        this.saleRepository = saleRepository;
        this.accountService = accountService;
    }

    // process a sale for a walk-in occasional customer (no account)
    public Receipt processSale(int customerId, List<SaleLine> lines, double discountPercent,
                               PaymentMethod paymentMethod, CardDetails cardDetails,
                               String cashierName) throws SaleException {
        return processInternal(customerId, null, lines, discountPercent, paymentMethod, cardDetails, cashierName);
    }

    // process a sale for an account holder — applies credit check and fixed discount
    public Receipt processSaleForAccount(Customer customer, List<SaleLine> lines,
                                         PaymentMethod paymentMethod, CardDetails cardDetails,
                                         String cashierName) throws SaleException {
        if (accountService == null) throw new SaleException(SaleException.Reason.SAVE_FAILED,
            "account service not configured");
        if (customer == null) throw new SaleException(SaleException.Reason.SAVE_FAILED,
            "customer cannot be null");

        // build a temp sale to calculate subtotal for the credit check
        double subtotal = lines.stream().mapToDouble(SaleLine::getLineTotal).sum();

        // apply fixed discount immediately — flexible is calculated at month end
        double discountAmount = accountService.calculatePointOfSaleDiscount(customer, subtotal);
        double discountPercent = subtotal > 0 ? discountAmount / subtotal : 0;
        double chargeAmount = subtotal - discountAmount;

        // credit limit check — per brief: suspended can still buy if limit not exceeded
        if (!accountService.canMakePurchase(customer, chargeAmount)) {
            throw new SaleException(SaleException.Reason.CREDIT_LIMIT_EXCEEDED,
                customer.getStatus() == AccountStatus.IN_DEFAULT
                    ? "account is in default — no new purchases allowed"
                    : "purchase would exceed credit limit of £" + String.format("%.2f", customer.getCreditLimit())
                      + " (current balance: £" + String.format("%.2f", customer.getCurrentBalance()) + ")");
        }

        Receipt receipt = processInternal(customer.getCustomerId(), customer, lines,
            discountPercent, paymentMethod, cardDetails, cashierName);

        // add to customer's outstanding balance and monthly spend
        accountService.recordAccountSale(customer, chargeAmount);

        return receipt;
    }

    // shared internal sale processing logic
    private Receipt processInternal(int customerId, Customer customer, List<SaleLine> lines,
                                    double discountPercent, PaymentMethod paymentMethod,
                                    CardDetails cardDetails, String cashierName) throws SaleException {
        // 1. basic validation
        if (lines == null || lines.isEmpty())
            throw new SaleException(SaleException.Reason.EMPTY_SALE, "cannot process a sale with no items");
        if (paymentMethod != PaymentMethod.CASH && cardDetails == null)
            throw new SaleException(SaleException.Reason.INVALID_PAYMENT, "card details are required for card payments");

        // 2. check stock availability for every line before touching anything
        for (SaleLine line : lines) {
            try {
                StockItem item = stockService.getStockItem(line.getItemId());
                if (item.getQuantity() < line.getQuantity()) {
                    throw new SaleException(SaleException.Reason.INSUFFICIENT_STOCK,
                        "insufficient stock for " + item.getName()
                        + " — requested: " + line.getQuantity()
                        + ", available: " + item.getQuantity());
                }
            } catch (StockException e) {
                throw new SaleException(SaleException.Reason.ITEM_NOT_FOUND,
                    "item not found: " + line.getItemName());
            }
        }

        // 3. build and persist the sale
        Sale sale = new Sale(0, customerId, lines, LocalDateTime.now(),
                             discountPercent, paymentMethod, cardDetails);
        int generatedId = saleRepository.save(sale);
        if (generatedId < 0)
            throw new SaleException(SaleException.Reason.SAVE_FAILED, "failed to save the sale to the database");

        // 4. deduct stock — sale is committed so log errors but dont fail
        for (SaleLine line : lines) {
            try {
                stockService.decreaseStock(line.getItemId(), line.getQuantity());
            } catch (StockException e) {
                System.err.println("warning: stock decrease failed for item " + line.getItemId() + ": " + e.getMessage());
            }
        }

        // 5. rebuild with real id for receipt number accuracy
        Sale savedSale = new Sale(generatedId, customerId, lines, sale.getSaleDate(),
                                  discountPercent, paymentMethod, cardDetails);

        // 6. generate receipt — invoice format for account customers, simple receipt for walk-ins
        String receiptNumber = generateReceiptNumber(generatedId, sale.getSaleDate());
        if (customer != null) {
            return new Receipt(receiptNumber, savedSale, cashierName,
                customer.getName(), customer.getAddress(), customer.getAccountNumber());
        }
        return new Receipt(receiptNumber, savedSale, cashierName);
    }

    private String generateReceiptNumber(int saleId, LocalDateTime date) {
        return "RCP-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + saleId;
    }
}
