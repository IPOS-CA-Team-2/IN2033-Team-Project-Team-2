/**
 * Concrete implementation of UserRepository.
 *
 * Currently backed by hardcoded seed users (one per role).
 * Replace with DatabaseManager lookups once Samuel's DB layer is ready.
 */
public class UserRepositoryImpl implements UserRepository {

    @Override
    public User findByUsername(String username) {
        // Basic validation
        if (username == null || username.isBlank()) {
            return null;
        }

        String searchName = username.trim();

        // Seed users — swap these out for DB lookups when Samuel's layer is ready
        if (searchName.equals("admin1")) {
            return new Admin("admin1", "pass123", "Alice");
        } else if (searchName.equals("pharma1")) {
            return new Pharmacist("pharma1", "pass456", "Bob");
        } else if (searchName.equals("manager1")) {
            return new Manager("manager1", "pass789", "Carol");
        }

        // No matching user found
        return null;
    }

    @Override
    public boolean validateCredentials(String username, String password) {
        User user = this.findByUsername(username);
        return user != null && user.checkPassword(password);
    }
}
