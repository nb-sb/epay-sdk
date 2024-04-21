package com.nbsb.epaysdk.epaybase.enumeration;
/**
* @author: Wanghaonan @戏人看戏
* @description: 支付方式枚举类
* @create: 2024/4/21 11:43
*/
public enum  PaymentMethod {
    /**
     * alipay	支付宝
     * wxpay	微信支付
     * qqpay	QQ钱包
     * bank	    网银支付
     * jdpay	京东支付
     */
    ALIPAY("alipay"),
    WXPAY("wxpay"),
    QQPAY("qqpay"),
    BANK("bank"),
    JDPAY("jdpay");

    private String method;

    PaymentMethod(String method) {
        this.method = method;
    }

    public String getMethodName() {
        return method;
    }
}
