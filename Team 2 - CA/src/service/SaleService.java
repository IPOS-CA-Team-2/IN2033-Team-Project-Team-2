package service;

import exception.SaleException;
import exception.StockException;
import model.*;
import repository.SaleRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// handles the full sale workflow — validate stock, create sale, persist, update stock, return receipt
public class SaleService {

    private final StockService stockService;
    private final SaleRepository saleRepository;

    public SaleService(StockService stockService, SaleRepository saleRepository) {
        if (stockService == null) throw new IllegalArgumentException("StockService cannot be null");
        if (saleRepository == null) throw new IllegalArgumentException("SaleRepository cannot be null");
        this.stockService = stockService;
        this.saleRepository = saleRepository;
    }

    // main entry point — processes a complete sale and returns a receipt
    // customerId = 0 for walk-in occasional customers
    public Receipt processSale(int customerId, List<SaleLine> lines, double discountPercent,
                               PaymentMethod paymentMethod, CardDetails cardDetails,
                               String cashierName) throws SaleException {

        // 1. basic validation
        if (lines == null || lines.isEmpty()) {
            throw new SaleException(SaleException.Reason.EMPTY_SALE, "cannot process a sale with no items");
        }
        if (paymentMethod != PaymentMethod.CASH && cardDetails == null) {
            throw new SaleException(SaleException.Reason.INVALID_PAYMENT, "card details are required for card payments");
        }

        // 2. validate stock availability for every line before touching anything
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
        if (generatedId < 0) {
            throw new SaleException(SaleException.Reason.SAVE_FAILED, "failed to save the sale to the database");
        }

        // 4. deduct stock for each line now that the sale is saved
        for (SaleLine line : lines) {
            try {
                stockService.decreaseStock(line.getItemId(), line.getQuantity());
            } catch (StockException e) {
                // sale is already saved — log the stock error but dont fail the sale
                System.err.println("warning: stock decrease failed for item " + line.getItemId() + ": " + e.getMessage());
            }
        }

        // 5. rebuild sale with the real id so receipt number is accurate
        Sale savedSale = new Sale(generatedId, customerId, lines, sale.getSaleDate(),
                                  discountPercent, paymentMethod, cardDetails);

        // 6. generate receipt
        String receiptNumber = generateReceiptNumber(generatedId, sale.getSaleDate());
        return new Receipt(receiptNumber, savedSale, cashierName);
    }

    // receipt number format: RCP-YYYYMMDD-{id}
    private String generateReceiptNumber(int saleId, LocalDateTime date) {
        return "RCP-" + date.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + saleId;
    }
}
