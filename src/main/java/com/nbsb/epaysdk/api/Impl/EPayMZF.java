package com.nbsb.epaysdk.api.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.nbsb.epaysdk.api.AbstractEPay;
import com.nbsb.epaysdk.api.entity.reponse.MZFOrderInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayException;
import com.nbsb.epaysdk.core.http.EPayHttpClient;
import com.nbsb.epaysdk.epaybase.Impl.MZFExecute;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.Map2Bean;

/**
 * 码支付 channel.
 */
public class EPayMZF extends AbstractEPay {

    private final MZFExecute execute;

    public EPayMZF() {
        this(MerchantConfig.fromAccountConfig());
    }

    public EPayMZF(MerchantConfig config) {
        super(config);
        this.execute = new MZFExecute(this.httpClient);
    }

    public EPayMZF(MerchantConfig config, EPayHttpClient httpClient) {
        super(config, httpClient);
        this.execute = new MZFExecute(httpClient);
    }

    @Override
    protected boolean isMzf() {
        return true;
    }

    @Override
    protected String mapiPath() {
        return "pay/apisubmit";
    }

    @Override
    protected String submitPath() {
        return "pay/apisubmit";
    }

    @Override
    protected String queryPath() {
        return "pay/chaorder";
    }

    @Override
    protected String apiPath() {
        return "pay/chaorder";
    }

    @Override
    protected OrderInfoResponse doQueryOrder(Query query) {
        query.setKey(config.getAppKey());
        query.setPid(Integer.valueOf(config.getAppId()));
        Map<String, String> map = new LinkedHashMap<String, String>(EpayBody2Map.beanToMap(query));
        map.put("url", config.join(queryPath()));
        String res = execute.queryOrderInfo(map);
        Map<String, Object> resultMap = parseObjectMap(res);
        MZFOrderInfoResponse wrapped = Map2Bean(resultMap, MZFOrderInfoResponse.class);
        Object data = wrapped.getData();
        if (data == null) {
            throw new EPayException("码支付订单查询未返回 data");
        }
        Map<String, Object> dataMap = JSON.parseObject(data.toString(), new TypeReference<Map<String, Object>>() {
        });
        OrderInfoResponse response = Map2Bean(dataMap, OrderInfoResponse.class);
        if (resultMap.get("code") instanceof Number) {
            response.setCode(((Number) resultMap.get("code")).intValue() - 200);
        }
        if (resultMap.get("msg") != null) {
            response.setMsg(String.valueOf(resultMap.get("msg")));
        }
        return response;
    }
}
