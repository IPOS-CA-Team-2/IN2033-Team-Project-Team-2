/**
 * Repository interface for user data access.
 * Ishmael's implementation should implement this interface.
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

/**
 * Implementation of UserRepository Interface
 */
public class UserRepositoryImpl implements UserRepository {

    @Override
    public User findByUsername(String username) {
        // 1. Basic validation: return null if input is empty
        if (username == null || username.isBlank()) {
            return null;
        }

        String searchName = username.trim();

        // 2. Data Lookup Logic
        // These are left empty for now as specific user accounts are not yet known.
        if (searchName.equals("")) {
            // return new Admin("", "", "");
        } else if (searchName.equals("")) {
            // return new Pharmacist("", "", "");
        } else if (searchName.equals("")) {
            // return new Manager("", "", "");
        }

        // Return null if no matching user record is found
        return null; 
    }

    @Override
    public boolean validateCredentials(String username, String password) {
        // 1. Attempt to retrieve the user using the findByUsername method
        User user = this.findByUsername(username);
        
        // 2. Use the unchangeable User.checkPassword logic to verify the password
        // 3. Return false if the user is null OR the password check fails
        return user != null && user.checkPassword(password);
    }
}

