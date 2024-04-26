package com.nbsb.epaysdk.api.entity.request;

import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * author: Wanghaonan @戏人看戏
 * description: 查询
 * create: 2024/4/21 16:36
 */
@Builder
@AllArgsConstructor
public class Query {
    //查询类型 1:本地订单号,2:商户订单号
    private Integer query_type;
    //    订单号
    //系统订单号 - 第三方收款生成的订单号 trade_no
    //商户订单号 - 自己生成的订单号 out_trade_no
    private String order_no;
    private String act = "order";
    private Integer pid;
    private String key;

    public Query(Integer query_type, String order_no) {
        this.query_type = query_type;
        this.order_no = order_no;
    }

    public Integer getQueryType() {
        return query_type;
    }

    public void setQueryType(Integer queryType) {
        this.query_type = queryType;
    }

    public String getOrder_no() {
        return order_no;
    }

    public void setOrder_no(String order_no) {
        this.order_no = order_no;
    }

    public Integer getPid() {
        return pid;
    }

    public void setPid(Integer pid) {
        this.pid = pid;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }


}
