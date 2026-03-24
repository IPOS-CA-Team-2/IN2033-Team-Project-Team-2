package model;

// the three possible states of an account holder's account — per brief fig. 1
public enum AccountStatus {
    NORMAL,      // can buy up to credit limit
    SUSPENDED,   // still can buy if credit limit not exceeded, 1st reminder due
    IN_DEFAULT   // cannot make any new purchases, 2nd reminder due
}
