package com.nbsb.epaysdk.api.Impl;

import com.nbsb.epaysdk.api.AbstractEPay;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.http.EPayHttpClient;
import com.nbsb.epaysdk.epaybase.Impl.YZFExecute;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.Map2Bean;

/**
 * 彩虹易支付 channel.
 */
public class EPayYZF extends AbstractEPay {

    private final YZFExecute execute;

    public EPayYZF() {
        this(MerchantConfig.fromAccountConfig());
    }

    public EPayYZF(MerchantConfig config) {
        super(config);
        this.execute = new YZFExecute(this.httpClient);
    }

    public EPayYZF(MerchantConfig config, EPayHttpClient httpClient) {
        super(config, httpClient);
        this.execute = new YZFExecute(httpClient);
    }

    @Override
    protected boolean isMzf() {
        return false;
    }

    @Override
    protected String mapiPath() {
        return "mapi.php";
    }

    @Override
    protected String submitPath() {
        return "submit.php";
    }

    @Override
    protected String queryPath() {
        return "api.php";
    }

    @Override
    protected String apiPath() {
        return "api.php";
    }

    @Override
    protected OrderInfoResponse doQueryOrder(Query query) {
        query.setKey(config.getAppKey());
        query.setPid(Integer.valueOf(config.getAppId()));
        Map<String, String> map = new LinkedHashMap<String, String>(EpayBody2Map.beanToMap(query));
        map.put("url", config.join(queryPath()));
        String res = execute.queryOrderInfo(map);
        return Map2Bean(parseObjectMap(res), OrderInfoResponse.class);
    }

    /**
     * Kept for callers that parsed submit.php HTML themselves.
     */
    public static String extractURL(String htmlContent) {
        return com.nbsb.epaysdk.core.form.SubmitHtmlParser.extractRelativeUrl(htmlContent);
    }
}
