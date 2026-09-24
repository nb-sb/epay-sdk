package com.nbsb.epaysdk.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.reponse.MerchantInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderListResponse;
import com.nbsb.epaysdk.api.entity.reponse.RefundResponse;
import com.nbsb.epaysdk.api.entity.reponse.SubmitResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.OrderListQuery;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.api.entity.request.RefundCmd;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayException;
import com.nbsb.epaysdk.core.exception.EPayUnsupportedException;
import com.nbsb.epaysdk.core.form.SubmitFormBuilder;
import com.nbsb.epaysdk.core.form.SubmitHtmlParser;
import com.nbsb.epaysdk.core.http.EPayHttpClient;
import com.nbsb.epaysdk.core.notify.NotifyPayload;
import com.nbsb.epaysdk.core.notify.NotifyVerifier;
import com.nbsb.epaysdk.core.validation.RequestValidator;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.Map2Bean;
import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.beanToMap;
import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.cmd2EPayBody;

/**
 * Shared pay / query / notify flow. Channels only override paths and parse quirks.
 */
public abstract class AbstractEPay implements EPay {

    protected final MerchantConfig config;
    protected final EPayHttpClient httpClient;

    protected AbstractEPay(MerchantConfig config) {
        this.config = config;
        this.httpClient = new EPayHttpClient(config);
    }

    protected AbstractEPay(MerchantConfig config, EPayHttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    protected abstract boolean isMzf();

    protected abstract String mapiPath();

    protected abstract String submitPath();

    protected abstract String queryPath();

    protected abstract String apiPath();

    @Override
    public MapiResponse mapi(GetQRCmd cmd) {
        RequestValidator.validatePay(cmd);
        EPayBody body = signedBody(cmd, mapiPath());
        String raw = httpClient.postForm(body.getUrl(), body.toFormMap());
        return parseMapi(raw);
    }

    @Override
    public SubmitResponse submit(GetQRCmd cmd) {
        if (isMzf()) {
            throw new EPayUnsupportedException("码支付不支持页面跳转 submit，请使用 mapi 或 buildSubmitForm");
        }
        RequestValidator.validatePay(cmd);
        EPayBody body = signedBody(cmd, submitPath());
        String raw = httpClient.postForm(body.getUrl(), body.toFormMap());
        SubmitResponse response = new SubmitResponse();
        response.setRawHtml(raw);
        String relative = SubmitHtmlParser.extractRelativeUrl(raw);
        if (relative != null) {
            response.setPayUrl(config.getUrl() + (relative.startsWith("/") ? relative.substring(1) : relative));
            response.setCode(1);
            response.setMsg("ok");
        } else {
            response.setCode(0);
            response.setMsg("未从 HTML 中解析到跳转地址");
        }
        return response;
    }

    @Override
    public String buildSubmitForm(GetQRCmd cmd) {
        RequestValidator.validatePay(cmd);
        EPayBody body = signedBody(cmd, submitPath());
        return SubmitFormBuilder.build(body.getUrl(), body.toFormMap());
    }

    @Override
    public OrderInfoResponse queryOrder(Query query) {
        RequestValidator.validateQuery(query);
        return doQueryOrder(query);
    }

    @Override
    public RefundResponse refund(RefundCmd cmd) {
        if (isMzf()) {
            throw new EPayUnsupportedException("码支付未提供退款接口");
        }
        RequestValidator.validateRefund(cmd);
        Map<String, String> params = signedApiParams("refund");
        if (cmd.getTradeNo() != null && !cmd.getTradeNo().isEmpty()) {
            params.put("trade_no", cmd.getTradeNo());
        }
        if (cmd.getOutTradeNo() != null && !cmd.getOutTradeNo().isEmpty()) {
            params.put("out_trade_no", cmd.getOutTradeNo());
        }
        params.put("money", cmd.getMoney());
        return parseJson(httpClient.get(config.join(apiPath()), params), RefundResponse.class);
    }

    @Override
    public MerchantInfoResponse queryMerchant() {
        if (isMzf()) {
            throw new EPayUnsupportedException("码支付未提供商户查询接口");
        }
        Map<String, String> params = signedApiParams("query");
        return parseJson(httpClient.get(config.join(apiPath()), params), MerchantInfoResponse.class);
    }

    @Override
    public OrderListResponse queryOrders(OrderListQuery query) {
        if (isMzf()) {
            throw new EPayUnsupportedException("码支付未提供订单列表接口");
        }
        OrderListQuery effective = query == null ? new OrderListQuery() : query;
        Map<String, String> params = signedApiParams("orders");
        if (effective.getLimit() != null) {
            params.put("limit", String.valueOf(effective.getLimit()));
        }
        if (effective.getPage() != null) {
            params.put("page", String.valueOf(effective.getPage()));
        }
        return parseOrderList(httpClient.get(config.join(apiPath()), params));
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        return NotifyVerifier.verify(params, config);
    }

    @Override
    public NotifyPayload parseNotify(Map<String, String> params) {
        return NotifyVerifier.parseAndVerify(params, config);
    }

    @Override
    public OrderInfoResponse waitUntilPaid(Query query, long timeoutMs, long intervalMs) {
        RequestValidator.validateQuery(query);
        if (timeoutMs <= 0) {
            throw new EPayException("timeoutMs 必须大于 0");
        }
        long sleep = intervalMs <= 0 ? 1000L : intervalMs;
        long deadline = System.currentTimeMillis() + timeoutMs;
        EPayException last = null;
        while (System.currentTimeMillis() <= deadline) {
            try {
                OrderInfoResponse info = doQueryOrder(query);
                if (info != null && info.isPaid()) {
                    return info;
                }
            } catch (EPayException e) {
                last = e;
            }
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                break;
            }
            try {
                Thread.sleep(Math.min(sleep, remaining));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new EPayException("等待支付被中断", e);
            }
        }
        if (last != null) {
            throw new EPayException("等待支付超时: " + last.getMessage(), last);
        }
        throw new EPayException("等待支付超时");
    }

    protected EPayBody signedBody(GetQRCmd cmd, String path) {
        EPayBody body = cmd2EPayBody(cmd, config);
        body.setUrl(config.join(path));
        body.setIs_mzf(isMzf() ? "true" : "false");
        body.setSign(SignUtil.Body2Md5(body));
        return body;
    }

    /**
     * Gateway query parameters. The caller's {@link Query} is not modified, and the merchant key stays off that object.
     */
    protected Map<String, String> orderQueryParams(Query query) {
        Map<String, String> map = new LinkedHashMap<String, String>(beanToMap(query));
        map.put("key", config.getAppKey());
        map.put("pid", config.getAppId());
        map.put("url", config.join(queryPath()));
        return map;
    }

    protected abstract OrderInfoResponse doQueryOrder(Query query);

    protected MapiResponse parseMapi(String raw) {
        Map<String, Object> resultMap = parseObjectMap(raw);
        MapiResponse response = Map2Bean(resultMap, MapiResponse.class);
        if (isMzf() && response.getCode() != null) {
            response.setCode(response.getCode() - 200);
        }
        return response;
    }

    protected Map<String, String> signedApiParams(String act) {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("act", act);
        params.put("pid", config.getAppId());
        params.put("key", config.getAppKey());
        return params;
    }

    protected OrderListResponse parseOrderList(String raw) {
        JSONObject json = parseObject(raw);
        OrderListResponse response = new OrderListResponse();
        response.setCode(json.getInteger("code"));
        response.setMsg(json.getString("msg"));
        response.setCount(json.getInteger("count"));
        Object data = json.get("data");
        if (data instanceof JSONArray) {
            List<OrderInfoResponse> orders = JSON.parseArray(((JSONArray) data).toJSONString(), OrderInfoResponse.class);
            response.setOrders(orders);
            if (response.getCount() == null) {
                response.setCount(orders.size());
            }
        }
        return response;
    }

    protected static <T> T parseJson(String raw, Class<T> type) {
        try {
            return JSON.parseObject(raw, type);
        } catch (Exception e) {
            throw new EPayException("解析响应失败: " + type.getSimpleName(), e);
        }
    }

    protected static Map<String, Object> parseObjectMap(String raw) {
        try {
            return JSON.parseObject(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new EPayException("解析 JSON 失败", e);
        }
    }

    protected static JSONObject parseObject(String raw) {
        try {
            JSONObject json = JSON.parseObject(raw);
            if (json == null) {
                throw new EPayException("网关返回空 JSON");
            }
            return json;
        } catch (EPayException e) {
            throw e;
        } catch (Exception e) {
            throw new EPayException("解析 JSON 失败", e);
        }
    }
}
