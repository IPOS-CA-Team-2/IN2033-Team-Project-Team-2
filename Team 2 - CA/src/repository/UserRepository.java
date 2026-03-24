package repository;

import model.User;

/**
 * Repository interface for user data access.
 */
public interface UserRepository {

    /**
     * Finds a user by their username.
     *
     * @param username the username to search for
     * @return the matching User, or null if not found
     */
    User findByUsername(String username);

    /**
     * Checks whether a username/password pair matches a stored user record.
     *
     * @param username the username to check
     * @param password the plain-text password to check
     * @return true if credentials are valid, false otherwise
     */
    boolean validateCredentials(String username, String password);
}
