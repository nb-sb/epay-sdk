package com.nbsb.epaysdk.api.entity.reponse;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
* @author: Wanghaonan @戏人看戏
* @description: API接口支付 响应实体
* @create: 2024/4/21 15:52
*/
@EqualsAndHashCode(callSuper = true)
@Data
public class MapiResponse extends CommonResponse {

    /**
     * 订单号
     */
    private String trade_no;
    /**
     * 支付跳转url
     */
    private String payurl;
    /**
     * 二维码链接
     */
    private String qrcode;
    /**
     * 小程序跳转url
     */
    private String urlscheme;
    /**
     * h5 url
     */
    private String h5_qrurl;
    /**
     * 商户订单号
     */
    private String out_trade_no;
    /**
     * 支付类型
     */
    private String type;
    /**
     * 金额
     */
    private String money;
    /**
     * 收款码图片地址
     */
    private String code_url;
}
