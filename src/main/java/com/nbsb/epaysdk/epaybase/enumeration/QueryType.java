package com.nbsb.epaysdk.epaybase.enumeration;

/**
 * Order lookup key for {@code api.php?act=order}.
 * 1 = 易支付订单号 trade_no; 2 = 商户订单号 out_trade_no.
 */
public enum QueryType {
    TRADE_NO(1),
    OUT_TRADE_NO(2);

    private final int code;

    QueryType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static QueryType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (QueryType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知查询类型: " + code);
    }
}
