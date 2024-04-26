package com.nbsb.epaysdk.epaybase;

import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * author: whn @戏人看戏
 * description: 易支付执行方法
 * create: 2024/4/20 23:49
 */
public abstract class EPayExecute {
    //执行支付功能
    public abstract String executePayment(Map<String, String> map);
    //查询订单信息
    public abstract String queryOrderInfo(Map<String,String> map) throws IOException;
    public static HttpEntity buildFormData(Map<String, String> map) {
        List<NameValuePair> formParams = new ArrayList<>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        return new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8);
    }
}
