package model;

// lifecycle of a wholesale order from ca to infopharma (sa)
public enum OrderStatus {
    PENDING,         // submitted, awaiting sa acceptance
    ACCEPTED,        // sa has accepted the order
    BEING_PROCESSED, // sa is picking/packing
    DISPATCHED,      // shipped, courier assigned
    DELIVERED,       // received by merchant, stock updated
    CANCELLED        // order cancelled by sa or ca
}
