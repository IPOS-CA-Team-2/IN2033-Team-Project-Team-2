public abstract class User {
    //sets the following variables to 'final' with the assumption that they will not be changed
    private final String username;
    private final String password;
    private final String role;
    private final String name;

    public User(String username, String password, String role, String name) {
        //checks for null and empty arguments and outputs neccesary error messages
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be null or blank.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password cannot be null or blank.");
        }
        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role cannot be null or blank.");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be null or blank.");
        }

        this.username = username;
        this.password = password;
        this.role = role;
        this.name = name;
    }

    //getter methods for each variable
    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRole() {
        return role;
    }

    public String getName() {
        return name;
    }

    public boolean checkPassword(String inputPassword) {
        return password.equals(inputPassword);
    }

    //overrides the default toString() method from Java’s base Object class to change how the object is printed as a string
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", role='" + role + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}