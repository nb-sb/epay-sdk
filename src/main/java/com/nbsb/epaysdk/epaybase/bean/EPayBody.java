package com.nbsb.epaysdk.epaybase.bean;

import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import lombok.experimental.Accessors;

/**
 * @author whn
 */
@Accessors(chain = true)
public class EPayBody {
    //支付地址
    private String url;
    //商户id
    private String pid;
    //支付类型
    private String type;
    //商户单号
    private String out_trade_no;
    //异步通知
    private String notify_url;
    //跳转地址
    private String return_url;
    //商品名
    private String name;
    //价格
    private String money;
    //签名类型
    private String sign_type = "MD5";
    //商户密钥
    private String key;
    private String sign;
//    //平台类型 是mzf还是yzf
//    private String is_mzf = "false";
    private EpayBodyType epayBodyType = new EpayBodyType();

    public EpayBodyType getEpayBodyType() {
        return epayBodyType;
    }

    public void setEpayBodyType(EpayBodyType epayBodyType) {
        this.epayBodyType = epayBodyType;
    }

    public String isIs_mzf() {
        return getEpayBodyType().getIs_mzf();
    }

    public void setIs_mzf(String is_mzf) {
        getEpayBodyType().setIs_mzf(is_mzf);
    }
    public DeviceType getDeviceType() {
        return getEpayBodyType().getDeviceType();
    }
    public void setDeviceType(DeviceType type) {
        getEpayBodyType().setType(type);
    }
    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }



    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMoney() {
        return money;
    }

    public void setMoney(String money) {
        this.money = money;
    }

    public String getOut_trade_no() {
        return out_trade_no;
    }

    public void setOut_trade_no(String out_trade_no) {
        this.out_trade_no = out_trade_no;
    }

    public String getNotify_url() {
        return notify_url;
    }

    public void setNotify_url(String notify_url) {
        this.notify_url = notify_url;
    }

    public String getReturn_url() {
        return return_url;
    }

    public void setReturn_url(String return_url) {
        this.return_url = return_url;
    }

    public String getSign_type() {
        return sign_type;
    }

    public void setSign_type(String sign_type) {
        this.sign_type = sign_type;
    }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
