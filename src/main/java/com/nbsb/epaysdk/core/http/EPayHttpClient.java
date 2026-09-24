package com.nbsb.epaysdk.core.http;

import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayHttpException;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared HTTP transport. Does not mutate caller maps.
 */
public class EPayHttpClient {

    private static final Logger log = LoggerFactory.getLogger(EPayHttpClient.class);
    private static final Pattern KEY_QUERY_PARAM = Pattern.compile("(?i)(^|[?&])(key)=[^&\\s]*");

    private final CloseableHttpClient client;

    public EPayHttpClient(MerchantConfig config) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(config.getConnectTimeoutMs()))
                .setConnectTimeout(Timeout.ofMilliseconds(config.getConnectTimeoutMs()))
                .setResponseTimeout(Timeout.ofMilliseconds(config.getResponseTimeoutMs()))
                .build();
        this.client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public EPayHttpClient(CloseableHttpClient client) {
        this.client = client;
    }

    public String postForm(String url, Map<String, String> params) {
        if (url == null || url.trim().isEmpty()) {
            throw new EPayHttpException("请求地址为空");
        }
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(new UrlEncodedFormEntity(toPairs(params), StandardCharsets.UTF_8));
        return execute(httpPost);
    }

    public String get(String url, Map<String, String> params) {
        if (url == null || url.trim().isEmpty()) {
            throw new EPayHttpException("请求地址为空");
        }
        try {
            URIBuilder builder = new URIBuilder(url);
            for (Map.Entry<String, String> entry : copy(params).entrySet()) {
                if (entry.getValue() != null) {
                    builder.addParameter(entry.getKey(), entry.getValue());
                }
            }
            return execute(new HttpGet(builder.build()));
        } catch (URISyntaxException e) {
            throw new EPayHttpException("非法请求地址: " + url, e);
        }
    }

    private String execute(org.apache.hc.client5.http.classic.methods.HttpUriRequestBase request) {
        try (CloseableHttpResponse response = client.execute(request)) {
            int status = response.getCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            log.debug("ePay {} {} -> {} body={}",
                    request.getMethod(),
                    redactSensitive(String.valueOf(request.getRequestUri())),
                    status,
                    redactSensitive(body));
            if (status < 200 || status >= 300) {
                throw new EPayHttpException("支付网关 HTTP " + status + ": " + body);
            }
            return body;
        } catch (EPayHttpException e) {
            throw e;
        } catch (IOException | ParseException e) {
            throw new EPayHttpException("请求支付网关失败: " + e.getMessage(), e);
        }
    }

    /**
     * Hides the merchant key. 易支付 puts {@code key} on the query string; debug logs must not keep it.
     */
    static String redactSensitive(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        return KEY_QUERY_PARAM.matcher(raw).replaceAll("$1$2=***");
    }

    private static List<NameValuePair> toPairs(Map<String, String> params) {
        List<NameValuePair> pairs = new ArrayList<NameValuePair>();
        for (Map.Entry<String, String> entry : copy(params).entrySet()) {
            if (entry.getValue() != null) {
                pairs.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
        }
        return pairs;
    }

    private static Map<String, String> copy(Map<String, String> params) {
        return params == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<String, String>(params);
    }
}
