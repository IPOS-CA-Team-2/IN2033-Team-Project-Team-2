package repository;

import model.StockItem;
import java.util.List;

// data access for stock items in the db
public interface StockRepository {

    // get all stock items
    List<StockItem> findAll();

    // find one stock item by id
    StockItem findById(int itemId);

    // update qty for an item
    boolean updateQuantity(int itemId, int newQuantity);

    // get items that are below low stock threshold
    List<StockItem> findLowStock();

    // save a stock item (insert or replace)
    boolean save(StockItem item);

    // delete item by id
    boolean delete(int itemId);
}
