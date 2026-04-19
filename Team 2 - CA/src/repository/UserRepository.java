package repository;

import model.User;

// data access for system users (staff accounts)
public interface UserRepository {

    // find a user by their username, returns null if not found
    User findByUsername(String username);

    // check if a username and password match a stored user record
    boolean validateCredentials(String username, String password);
}
