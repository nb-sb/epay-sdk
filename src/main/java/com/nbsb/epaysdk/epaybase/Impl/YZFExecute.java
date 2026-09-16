package com.nbsb.epaysdk.epaybase.Impl;

import com.nbsb.epaysdk.core.exception.EPayValidationException;
import com.nbsb.epaysdk.core.http.EPayHttpClient;
import com.nbsb.epaysdk.epaybase.EPayExecute;

import java.util.LinkedHashMap;
import java.util.Map;

public class YZFExecute extends EPayExecute {

    public YZFExecute(EPayHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * @deprecated prefer {@link #YZFExecute(EPayHttpClient)} with a configured client.
     */
    @Deprecated
    public YZFExecute() {
        super(null);
    }

    @Override
    public String executePayment(Map<String, String> map) {
        Map<String, String> copy = new LinkedHashMap<String, String>(map);
        String url = copy.remove("url");
        copy.remove("epayBodyType");
        copy.remove("key");
        return requireClient().postForm(url, copy);
    }

    @Override
    public String queryOrderInfo(Map<String, String> map) {
        Map<String, String> copy = new LinkedHashMap<String, String>(map);
        String url = copy.remove("url");
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("act", copy.get("act") == null ? "order" : copy.get("act"));
        query.put("pid", copy.get("pid"));
        query.put("key", copy.get("key"));

        String queryType = copy.get("query_type");
        String orderNo = copy.get("order_no");
        if ("1".equals(queryType)) {
            query.put("trade_no", orderNo);
        } else if ("2".equals(queryType)) {
            query.put("out_trade_no", orderNo);
        } else {
            throw new EPayValidationException("订单类型错误");
        }
        return requireClient().get(url, query);
    }

    public String get(String url, Map<String, String> params) {
        return requireClient().get(url, params);
    }

    private EPayHttpClient requireClient() {
        if (httpClient == null) {
            throw new IllegalStateException("YZFExecute 需要 EPayHttpClient，请使用 EPayYZF(MerchantConfig) 或 EPayClient");
        }
        return httpClient;
    }
}
