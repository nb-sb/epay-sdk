package com.nbsb.epaysdk.api.entity.request;

import com.nbsb.epaysdk.epaybase.enumeration.QueryType;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Single-order query. Type 1 = trade_no, type 2 = out_trade_no.
 */
@Builder
@AllArgsConstructor
public class Query {
    private Integer query_type;
    private String order_no;
    @Builder.Default
    private String act = "order";
    private Integer pid;
    private String key;

    public Query() {
    }

    public Query(Integer query_type, String order_no) {
        this.query_type = query_type;
        this.order_no = order_no;
        this.act = "order";
    }

    public Query(QueryType queryType, String order_no) {
        this(queryType == null ? null : queryType.getCode(), order_no);
    }

    public static Query byTradeNo(String tradeNo) {
        return new Query(QueryType.TRADE_NO, tradeNo);
    }

    public static Query byOutTradeNo(String outTradeNo) {
        return new Query(QueryType.OUT_TRADE_NO, outTradeNo);
    }

    public Integer getQueryType() {
        return query_type;
    }

    public Integer getQuery_type() {
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

    public String getAct() {
        return act == null ? "order" : act;
    }

    public void setAct(String act) {
        this.act = act;
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
