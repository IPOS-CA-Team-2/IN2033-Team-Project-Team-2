//Manager is an extension of the parent class User
public class Manager extends User {
    //constructor for Pharmacist and takes username, password and name as input
    public Manager(String username, String password, String name) {
        //calls the constructor of the parent class
        super(username, password, "Manager", name);
    }
}