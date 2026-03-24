package model;

//pharmacist is an extension of the parent class User
public class Pharmacist extends User {
    //constructor for Pharmacist and takes username, password and name as input
    public Pharmacist(String username, String password, String name) {
        //calls the constructor of the parent class
        super(username, password, "Pharmacist", name);
    }
}