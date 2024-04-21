package com.nbsb.epaysdk.api.entity.request;

import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * @author: Wanghaonan @戏人看戏
 * @description: 获取二维码的请求
 * @create: 2024/4/21 00:11
 */
@Builder
@AllArgsConstructor
public class GetQRCmd {
    //商品名称
    private String name;
    //订单号
    private String orderNo;
    //商品金额
    private String amount;
    //支付方式
    private PaymentMethod payType = PaymentMethod.ALIPAY;
    //异步通知
    private String notify_url;
    //跳转地址
    private String return_url;

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
}
