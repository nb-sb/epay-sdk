package com.nbsb.epaysdk.epaybase.enumeration;

/**
 * author: Wanghaonan @戏人看戏
 * description: 是码支付 还是易支付
 * create: 2024/4/21 14:00
 */
public enum PayType {
    YZF("yzf"),
    MZF("mzf");

    private String type;

    PayType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
