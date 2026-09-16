package com.nbsb.epaysdk.epaybase;

import com.nbsb.epaysdk.core.http.EPayHttpClient;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Channel HTTP port. Implementations must not mutate the incoming map.
 */
public abstract class EPayExecute {

    protected final EPayHttpClient httpClient;

    protected EPayExecute(EPayHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public abstract String executePayment(Map<String, String> map);

    public abstract String queryOrderInfo(Map<String, String> map);

    public static HttpEntity buildFormData(Map<String, String> map) {
        List<NameValuePair> formParams = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            formParams.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        return new UrlEncodedFormEntity(formParams, StandardCharsets.UTF_8);
    }
}
