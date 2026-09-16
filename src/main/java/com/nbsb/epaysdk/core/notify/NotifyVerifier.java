package com.nbsb.epaysdk.core.notify;

import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPaySignException;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verifies 易支付 / 码支付 MD5 notify signatures.
 */
public final class NotifyVerifier {

    private NotifyVerifier() {
    }

    public static boolean verify(Map<String, String> params, String appKey) {
        if (params == null || params.isEmpty() || appKey == null || appKey.isEmpty()) {
            return false;
        }
        String sign = params.get("sign");
        if (sign == null || sign.isEmpty()) {
            return false;
        }
        Map<String, String> copy = new LinkedHashMap<String, String>(params);
        String expected = SignUtil.map2Md5(copy, appKey);
        return sign.equalsIgnoreCase(expected);
    }

    public static boolean verify(Map<String, String> params, MerchantConfig config) {
        if (config == null) {
            return false;
        }
        if (!verify(params, config.getAppKey())) {
            return false;
        }
        String pid = params.get("pid");
        return pid == null || pid.isEmpty() || config.getAppId().equals(pid);
    }

    public static NotifyPayload parseAndVerify(Map<String, String> params, MerchantConfig config) {
        if (!verify(params, config)) {
            throw new EPaySignException("异步通知签名验证失败");
        }
        return NotifyPayload.from(params);
    }
}
