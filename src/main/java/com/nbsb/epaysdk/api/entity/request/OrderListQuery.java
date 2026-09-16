package com.nbsb.epaysdk.api.entity.request;

/**
 * Recent-order list query ({@code api.php?act=orders}).
 */
public class OrderListQuery {

    private Integer limit = 20;
    private Integer page;

    public OrderListQuery() {
    }

    public OrderListQuery(Integer limit) {
        this.limit = limit;
    }

    public Integer getLimit() {
        return limit;
    }

    public void setLimit(Integer limit) {
        this.limit = limit;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }
}
