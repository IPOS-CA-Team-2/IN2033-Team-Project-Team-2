package service;

import exception.StockException;
import model.OnlineSale;
import model.OnlineSaleItem;

import java.util.ArrayList;
import java.util.List;

// handles incoming online sale events from ipos-pu
// deducts stock for each item in the sale
public class OnlineSaleService {

    private final StockService stockService;

    public OnlineSaleService(StockService stockService) {
        this.stockService = stockService;
    }

    // process an online sale — returns true if all items were fully deducted
    // if any item has insufficient stock it is skipped and false is returned
    // skipped items are reported so the caller can surface the issue
    public boolean processOnlineSale(OnlineSale sale) {
        if (sale == null || sale.getItems().isEmpty()) return false;

        boolean fullyApplied = true;

        for (OnlineSaleItem item : sale.getItems()) {
            try {
                stockService.decreaseStock(item.getItemId(), item.getQuantity());
            } catch (StockException e) {
                System.err.println("online sale " + sale.getPuOrderId()
                    + ": skipped item " + item.getItemId() + " — " + e.getMessage());
                fullyApplied = false;
            }
        }

        return fullyApplied;
    }

    // returns a list of item ids that could not be fully applied (insufficient stock)
    // useful for showing the pharmacist what went wrong in the simulate dialog
    public List<Integer> getSkippedItems(OnlineSale sale) {
        List<Integer> skipped = new ArrayList<>();
        for (OnlineSaleItem item : sale.getItems()) {
            try {
                // check without applying — just see if the stock is available
                stockService.getStockItem(item.getItemId());
                // if we get here, check the quantity
            } catch (StockException ignored) {
                skipped.add(item.getItemId());
            }
        }
        return skipped;
    }
}
