package com.nbsb.epaysdk.api.entity.reponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Recent orders ({@code api.php?act=orders}).
 */
public class OrderListResponse extends CommonResponse {

    private Integer count;
    private List<OrderInfoResponse> orders = new ArrayList<OrderInfoResponse>();

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public List<OrderInfoResponse> getOrders() {
        return orders;
    }

    public void setOrders(List<OrderInfoResponse> orders) {
        this.orders = orders;
    }
}
