package service;

import exception.AuthException;
import model.User;
import repository.UserRepository;

/**
 * Handles authentication business logic for IPOS-CA.
 *
 * Responsibilities:
 *   - Validate that input is not blank
 *   - Delegate credential lookup to UserRepository
 *   - Return the authenticated User on success
 *   - Throw AuthException with a specific reason on failure
 *
 * This class has no dependency on any UI and can be unit-tested independently.
 */
public class LoginService {

    private final UserRepository userRepository;

    /**
     * @param userRepository the data source used to look up users
     */
    public LoginService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository cannot be null.");
        }
        this.userRepository = userRepository;
    }

    /**
     * Attempts to authenticate a user with the supplied credentials.
     *
     * @param username the entered username (trimmed before use)
     * @param password the entered password
     * @return the authenticated User object on success
     * @throws AuthException if authentication fails for any reason
     */
    public User login(String username, String password) throws AuthException {

        // 1. Reject blank inputs immediately — no point hitting the database
        if (username == null || username.isBlank()) {
            throw new AuthException(
                AuthException.Reason.BLANK_INPUT,
                "Username cannot be blank."
            );
        }
        if (password == null || password.isBlank()) {
            throw new AuthException(
                AuthException.Reason.BLANK_INPUT,
                "Password cannot be blank."
            );
        }

        String trimmedUsername = username.trim();

        // 2. Look up the user by username
        User user = userRepository.findByUsername(trimmedUsername);

        if (user == null) {
            // Do NOT reveal whether it was the username or password that was wrong
            throw new AuthException(
                AuthException.Reason.INVALID_CREDENTIALS,
                "Invalid username or password."
            );
        }

        // 3. Validate password against the stored record
        if (!user.checkPassword(password)) {
            throw new AuthException(
                AuthException.Reason.INVALID_CREDENTIALS,
                "Invalid username or password."
            );
        }

        // 4. All checks passed — return the authenticated user
        return user;
    }
}
