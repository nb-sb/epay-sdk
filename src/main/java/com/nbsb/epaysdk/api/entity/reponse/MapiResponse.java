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

}
