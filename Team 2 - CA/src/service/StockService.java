package service;

import app.AppContext;
import exception.StockException;
import model.StockItem;
import repository.StockRepository;
import java.util.Collections;
import java.util.List;

// business logic for stock operations
// validates inputs and prevents invalid states before hitting the repo
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        if (stockRepository == null) {
            throw new IllegalArgumentException("StockRepository cannot be null.");
        }
        this.stockRepository = stockRepository;
    }

    // grab everything from the repo
    public List<StockItem> getAllStock() {
        List<StockItem> items = stockRepository.findAll();
        return items != null ? items : Collections.emptyList();
    }

    // check the id makes sense then fetch from repo
    public StockItem getStockItem(int itemId) throws StockException {
        validateItemId(itemId);

        StockItem item = stockRepository.findById(itemId);
        if (item == null) {
            throw new StockException(
                StockException.Reason.ITEM_NOT_FOUND,
                "no stock item found with id " + itemId
            );
        }
        return item;
    }

    // e.g. new delivery arrived — bump the count up
    public void increaseStock(int itemId, int amount) throws StockException {
        validateItemId(itemId);
        validateQuantity(amount);

        StockItem item = getStockItem(itemId);
        int newQuantity = item.getQuantity() + amount;
        stockRepository.updateQuantity(itemId, newQuantity);
    }

    // e.g. sale made — make sure we dont go negative
    public void decreaseStock(int itemId, int amount) throws StockException {
        validateItemId(itemId);
        validateQuantity(amount);

        StockItem item = getStockItem(itemId);
        if (amount > item.getQuantity()) {
            throw new StockException(
                StockException.Reason.INSUFFICIENT_STOCK,
                "cannot decrease by " + amount + " — only " + item.getQuantity() + " in stock"
            );
        }

        int newQuantity = item.getQuantity() - amount;
        stockRepository.updateQuantity(itemId, newQuantity);
    }

    // items below their threshold — pharmacist needs to reorder
    public List<StockItem> getLowStock() {
        List<StockItem> items = stockRepository.findLowStock();
        return items != null ? items : Collections.emptyList();
    }

    // add a brand new item to the catalogue and notify PU so it appears online
    public void addStockItem(StockItem item) throws StockException {
        if (item == null) {
            throw new StockException(
                StockException.Reason.NULL_ITEM,
                "stock item cannot be null"
            );
        }
        stockRepository.save(item);
        if (AppContext.getPuAdapter() != null) {
            AppContext.getPuAdapter().notifyStockUpdated(item);
        }
    }

    // removes item from catalogue and notifies PU so it disappears from the online shop
    public void removeStockItem(int itemId) throws StockException {
        validateItemId(itemId);
        getStockItem(itemId); // confirms item exists before deletion
        stockRepository.delete(itemId);
        if (AppContext.getPuAdapter() != null) {
            AppContext.getPuAdapter().notifyProductDeleted(itemId);
        }
    }

    // -- private helpers --

    private void validateItemId(int itemId) throws StockException {
        if (itemId <= 0) {
            throw new StockException(
                StockException.Reason.INVALID_ITEM_ID,
                "item id must be positive"
            );
        }
    }

    private void validateQuantity(int amount) throws StockException {
        if (amount <= 0) {
            throw new StockException(
                StockException.Reason.INVALID_QUANTITY,
                "quantity must be positive"
            );
        }
    }
}
