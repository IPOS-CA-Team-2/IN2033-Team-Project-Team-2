package repository;

import model.Sale;
import java.time.LocalDate;
import java.util.List;

// data access for completed sale transactions
public interface SaleRepository {

    // save a sale and all its lines, returns the generated sale id
    int save(Sale sale);

    // get one sale by id including its lines
    Sale findById(int saleId);

    // get all sales
    List<Sale> findAll();

    // get all sales for a specific account holder, used for debt tracking
    List<Sale> findByCustomerId(int customerId);

    // get sales within a date range, used for turnover report
    List<Sale> findByDateRange(LocalDate from, LocalDate to);

    // get all unpaid account sales, used for aggregated debt report
    List<Sale> findUnpaidAccountSales();

    // mark a sale as paid, called when an account holder clears their balance
    boolean markAsPaid(int saleId);
}
