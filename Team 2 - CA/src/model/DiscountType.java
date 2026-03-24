package model;

// two discount plan types per brief spec (same structure as ipos-sa)
public enum DiscountType {
    NONE,      // no discount
    FIXED,     // same rate off all purchases, applied at point of sale
    FLEXIBLE   // rate depends on total monthly spend, calculated at end of month
}
