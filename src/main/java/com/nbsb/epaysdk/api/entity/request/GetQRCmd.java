package com.nbsb.epaysdk.api.entity.request;

import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Create-payment command (mapi / submit).
 */
@Builder
@AllArgsConstructor
public class GetQRCmd {
    private String name;
    private String orderNo;
    private String amount;
    @Builder.Default
    private PaymentMethod payType = PaymentMethod.ALIPAY;
    private String notify_url;
    private String return_url;
    @Builder.Default
    private DeviceType device = DeviceType.JUMP;
    private String clientIp;
    private String param;

    public GetQRCmd() {
    }

    public GetQRCmd(String name, String orderNo, String amount, PaymentMethod payType,
                    String notify_url, String return_url) {
        this.name = name;
        this.orderNo = orderNo;
        this.amount = amount;
        this.payType = payType;
        this.notify_url = notify_url;
        this.return_url = return_url;
        this.device = DeviceType.JUMP;
    }


    public PaymentMethod getPayType() {
        return payType;
    }

    public void setPayType(PaymentMethod payType) {
        this.payType = payType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
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

    public DeviceType getDevice() {
        return device == null ? DeviceType.JUMP : device;
    }

    public void setDevice(DeviceType device) {
        this.device = device;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }
}
