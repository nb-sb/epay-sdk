package com.nbsb.epaysdk.epaybase.enumeration;

/**
 * Gateway order status: 1 paid, 0 unpaid.
 */
public enum OrderStatus {
    UNPAID(0),
    PAID(1);

    private final int code;

    OrderStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OrderStatus fromCode(Integer code) {
        if (code != null && code == 1) {
            return PAID;
        }
        return UNPAID;
    }
}
