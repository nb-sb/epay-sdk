package com.nbsb.epaysdk.api.entity.reponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * OrderInfoResponse
 * 订单信息响应实体类
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class OrderInfoResponse extends CommonResponse {

    /**
     * 易支付订单号
     */
    private String trade_no;

    /**
     * 商户订单号
     */
    private String out_trade_no;

    /**
     * 第三方订单号
     */
    private String api_trade_no;

    /**
     * 支付方式列表
     */
    private String type;

    /**
     * 发起支付的商户ID
     */
    private Integer pid;

    /**
     * 创建订单时间
     */
    private String addtime;

    /**
     * 完成交易时间
     */
    private String endtime;

    /**
     * 商品名称
     */
    private String name;

    /**
     * 商品金额
     */
    private String money;

    /**
     * 支付状态，1为支付成功，0为未支付
     */
    private Integer status;

    /**
     * 业务扩展参数，默认留空
     */
    private String param;

    /**
     * 支付者账号，默认留空
     */
    private String buyer;
}
