package com.nbsb.epaysdk.api;

import java.util.Map;

/**
 * author: Wanghaonan @戏人看戏
 * description: 必须要实现的类，回调接口
 * create: 2024/4/21 18:19
 */
public interface EPayInterface {
    //如果不是success则会重复发送回调信息，你可以实现这个GET controller接口进行等待回调订单
    //这个接口可以保存
    String onPayResult(Map<String, String> params);
}
