package model;

//Admin is an extension of the parent class User
public class Admin extends User {
    //constructor for Admin and takes username, password and name as input
    public Admin(String username, String password, String name) {
        //calls the constructor of the parent class
        super(username, password, "Admin", name);
    }
}