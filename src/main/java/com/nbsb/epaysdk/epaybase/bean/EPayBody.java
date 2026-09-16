package com.nbsb.epaysdk.epaybase.bean;

import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Signed form body sent to the gateway.
 */
@Accessors(chain = true)
public class EPayBody {
    private String url;
    private String pid;
    private String type;
    private String out_trade_no;
    private String notify_url;
    private String return_url;
    private String name;
    private String money;
    private String sign_type = "MD5";
    private String key;
    private String sign;
    private String device;
    private String clientip;
    private String param = "";
    private EpayBodyType epayBodyType = new EpayBodyType();

    public Map<String, String> toFormMap() {
        Map<String, String> form = new LinkedHashMap<String, String>();
        put(form, "pid", pid);
        put(form, "type", type);
        put(form, "out_trade_no", out_trade_no);
        put(form, "notify_url", notify_url);
        put(form, "return_url", return_url);
        put(form, "name", name);
        put(form, "money", money);
        put(form, "sign_type", sign_type);
        put(form, "sign", sign);
        if (!"true".equals(isIs_mzf())) {
            put(form, "device", device);
            put(form, "param", param == null ? "" : param);
            put(form, "clientip", clientip);
        }
        return form;
    }

    private static void put(Map<String, String> form, String key, String value) {
        if (value != null) {
            form.put(key, value);
        }
    }

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
        if (type != null) {
            this.device = type.getDeviceTypeName();
        }
    }

    public String getDevice() {
        if (device != null) {
            return device;
        }
        return getDeviceType() == null ? DeviceType.JUMP.getDeviceTypeName() : getDeviceType().getDeviceTypeName();
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getClientip() {
        return clientip;
    }

    public void setClientip(String clientip) {
        this.clientip = clientip;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
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
