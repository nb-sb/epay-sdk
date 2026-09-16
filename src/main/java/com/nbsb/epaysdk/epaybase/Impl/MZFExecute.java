package com.nbsb.epaysdk.epaybase.Impl;

import com.nbsb.epaysdk.core.http.EPayHttpClient;
import com.nbsb.epaysdk.epaybase.EPayExecute;

import java.util.LinkedHashMap;
import java.util.Map;

public class MZFExecute extends EPayExecute {

    public MZFExecute(EPayHttpClient httpClient) {
        super(httpClient);
    }

    /**
     * @deprecated prefer {@link #MZFExecute(EPayHttpClient)} with a configured client.
     */
    @Deprecated
    public MZFExecute() {
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
        query.put("order_no", copy.get("order_no"));
        query.put("type", copy.get("query_type"));
        return requireClient().get(url, query);
    }

    private EPayHttpClient requireClient() {
        if (httpClient == null) {
            throw new IllegalStateException("MZFExecute 需要 EPayHttpClient，请使用 EPayMZF(MerchantConfig) 或 EPayClient");
        }
        return httpClient;
    }
}
