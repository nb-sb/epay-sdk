package com.nbsb.epaysdk.api.entity.request;

/**
 * Refund against a YZF order ({@code api.php?act=refund}).
 */
public class RefundCmd {

    private String tradeNo;
    private String outTradeNo;
    private String money;

    public RefundCmd() {
    }

    public RefundCmd(String tradeNo, String outTradeNo, String money) {
        this.tradeNo = tradeNo;
        this.outTradeNo = outTradeNo;
        this.money = money;
    }

    public static RefundCmd byTradeNo(String tradeNo, String money) {
        return new RefundCmd(tradeNo, null, money);
    }

    public static RefundCmd byOutTradeNo(String outTradeNo, String money) {
        return new RefundCmd(null, outTradeNo, money);
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getOutTradeNo() {
        return outTradeNo;
    }

    public void setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
    }
}
