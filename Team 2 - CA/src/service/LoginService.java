package service;

import exception.AuthException;
import model.User;
import repository.UserRepository;

// handles login logic for the CA system
// no UI dependency so it can be tested on its own
public class LoginService {

    private final UserRepository userRepository;

    public LoginService(UserRepository userRepository) {
        if (userRepository == null) {
            throw new IllegalArgumentException("UserRepository cannot be null.");
        }
        this.userRepository = userRepository;
    }

    // tries to log the user in with the given credentials, throws AuthException if anything fails
    public User login(String username, String password) throws AuthException {

        // 1. reject blank inputs straight away, no point hitting the database
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

        // 2. look up the user by username
        User user = userRepository.findByUsername(trimmedUsername);

        if (user == null) {
            // Do NOT reveal whether it was the username or password that was wrong
            throw new AuthException(
                AuthException.Reason.INVALID_CREDENTIALS,
                "Invalid username or password."
            );
        }

        // 3. check the password against the stored record
        if (!user.checkPassword(password)) {
            throw new AuthException(
                AuthException.Reason.INVALID_CREDENTIALS,
                "Invalid username or password."
            );
        }

        // 4. all checks passed, return the authenticated user
        return user;
    }
}
