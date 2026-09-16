package com.nbsb.epaysdk.epaybase.enumeration;

/**
 * Async notify {@code trade_status}. Only TRADE_SUCCESS means paid.
 */
public enum TradeStatus {
    TRADE_SUCCESS("TRADE_SUCCESS"),
    TRADE_CLOSED("TRADE_CLOSED"),
    UNKNOWN("UNKNOWN");

    private final String value;

    TradeStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public boolean isPaid() {
        return this == TRADE_SUCCESS;
    }

    public static TradeStatus fromValue(String raw) {
        if (raw == null) {
            return UNKNOWN;
        }
        for (TradeStatus status : values()) {
            if (status.value.equalsIgnoreCase(raw.trim())) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
