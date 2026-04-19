package model;

// payment methods accepted by ipos-ca
// account holders can only use card, occasional customers can use cash or card
public enum PaymentMethod {
    CASH,
    CREDIT_CARD,
    DEBIT_CARD
}
