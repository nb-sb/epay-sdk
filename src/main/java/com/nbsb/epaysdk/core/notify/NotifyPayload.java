package com.nbsb.epaysdk.core.notify;

import com.nbsb.epaysdk.epaybase.enumeration.TradeStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parsed async notify (or return_url) payload.
 */
public class NotifyPayload {

    public static final String SUCCESS_ACK = "success";

    private final String pid;
    private final String tradeNo;
    private final String outTradeNo;
    private final String type;
    private final String name;
    private final String money;
    private final TradeStatus tradeStatus;
    private final String param;
    private final String sign;
    private final String signType;
    private final Map<String, String> raw;

    public NotifyPayload(String pid, String tradeNo, String outTradeNo, String type, String name,
                         String money, TradeStatus tradeStatus, String param, String sign,
                         String signType, Map<String, String> raw) {
        this.pid = pid;
        this.tradeNo = tradeNo;
        this.outTradeNo = outTradeNo;
        this.type = type;
        this.name = name;
        this.money = money;
        this.tradeStatus = tradeStatus == null ? TradeStatus.UNKNOWN : tradeStatus;
        this.param = param;
        this.sign = sign;
        this.signType = signType;
        this.raw = raw == null
                ? Collections.<String, String>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, String>(raw));
    }

    public static NotifyPayload from(Map<String, String> params) {
        Map<String, String> raw = params == null ? new LinkedHashMap<String, String>() : params;
        return new NotifyPayload(
                raw.get("pid"),
                raw.get("trade_no"),
                raw.get("out_trade_no"),
                raw.get("type"),
                raw.get("name"),
                raw.get("money"),
                TradeStatus.fromValue(raw.get("trade_status")),
                raw.get("param"),
                raw.get("sign"),
                raw.get("sign_type"),
                raw
        );
    }

    public boolean isPaid() {
        return tradeStatus.isPaid();
    }

    public String getPid() {
        return pid;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public String getOutTradeNo() {
        return outTradeNo;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getMoney() {
        return money;
    }

    public TradeStatus getTradeStatus() {
        return tradeStatus;
    }

    public String getParam() {
        return param;
    }

    public String getSign() {
        return sign;
    }

    public String getSignType() {
        return signType;
    }

    public Map<String, String> getRaw() {
        return raw;
    }
}
