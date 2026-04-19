package repository;

import model.AccountStatus;
import model.Customer;
import java.time.LocalDate;
import java.util.List;

// data access for account holders
public interface CustomerRepository {

    // get all account holders
    List<Customer> findAll();

    // find by db id
    Customer findById(int customerId);

    // pharmacist looks up customer by account number at point of sale
    Customer findByAccountNumber(String accountNumber);

    // insert new account holder, returns generated id
    int save(Customer customer);

    // full update of an existing customer record
    boolean update(Customer customer);

    // update just the balance and monthly spend after a sale or payment
    boolean updateBalance(int customerId, double newBalance, double newMonthlySpend);

    // update account status and reminder flags, called by account status engine
    boolean updateStatus(int customerId, AccountStatus status,
                         String status1st, String status2nd,
                         LocalDate date1st, LocalDate date2nd, LocalDate statementDate);

    // delete an account holder
    boolean delete(int customerId);

    // generate the next available account number in CSM format (e.g. CSM000004)
    String generateAccountNumber();
}
