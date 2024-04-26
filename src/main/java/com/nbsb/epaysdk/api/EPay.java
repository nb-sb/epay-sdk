package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;

/**
 * author: Wanghaonan @戏人看戏
 * description: 定义支付的api接口需要实现的内容，必须要实现的接口
 * create: 2024/4/21 00:04
 */
public interface EPay {
    //API接口支付
    Object mapi(GetQRCmd ePayBody);
    //页面跳转支付
    Object submit(GetQRCmd ePayBody);
    //查询单个订单
    Object queryOrder(Query query);
//注： 我不建议查询太多的订单，如果想查询很多订单并且查看订单建议直接到易支付后台进行可视化页面进行查询，所以只提供查询单个订单的方法
}
