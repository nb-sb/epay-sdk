package com.nbsb.epaysdk.api.entity.reponse;

/**
 * YZF refund response ({@code api.php?act=refund}).
 */
public class RefundResponse extends CommonResponse {

    private String money;

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
    }
}
