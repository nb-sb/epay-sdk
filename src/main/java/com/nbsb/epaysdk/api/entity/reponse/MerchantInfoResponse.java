package com.nbsb.epaysdk.api.entity.reponse;

/**
 * Merchant account snapshot ({@code api.php?act=query}).
 */
public class MerchantInfoResponse extends CommonResponse {

    private Integer pid;
    private Integer active;
    private String money;
    private String account;
    private String username;
    private Integer settles;

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public Integer getActive() {
        return active;
    }

    public void setActive(Integer active) {
        this.active = active;
    }

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Integer getSettles() {
        return settles;
    }

    public void setSettles(Integer settles) {
        this.settles = settles;
    }
}
