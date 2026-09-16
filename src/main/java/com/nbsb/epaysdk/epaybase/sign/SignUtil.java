package com.nbsb.epaysdk.epaybase.sign;

import com.nbsb.epaysdk.core.exception.EPayException;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MD5 sign used by 易支付 / 码支付 (sorted key=value&amp;... + key).
 */
public class SignUtil {

    public static String Body2Md5(EPayBody ePayBody) {
        Map<String, String> sign = new LinkedHashMap<String, String>();
        sign.put("pid", ePayBody.getPid());
        sign.put("type", ePayBody.getType());
        sign.put("out_trade_no", ePayBody.getOut_trade_no());
        sign.put("notify_url", ePayBody.getNotify_url());
        sign.put("return_url", ePayBody.getReturn_url());
        sign.put("name", ePayBody.getName());
        sign.put("money", ePayBody.getMoney());
        if (!"true".equals(ePayBody.isIs_mzf())) {
            sign.put("device", ePayBody.getDevice());
            sign.put("param", ePayBody.getParam() == null ? "" : ePayBody.getParam());
            sign.put("clientip", ePayBody.getClientip());
        }
        return map2Md5(sign, ePayBody.getKey());
    }

    public static String map2Md5(Map<String, String> map, String key) {
        map = sortByKey(map);
        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            String value = entry.getValue();
            if (!"sign".equals(entry.getKey()) && !"sign_type".equals(entry.getKey())
                    && value != null && !value.isEmpty()) {
                signStr.append(entry.getKey()).append('=').append(value).append('&');
            }
        }
        if (signStr.length() == 0) {
            throw new EPayException("签名字段为空");
        }
        signStr.setLength(signStr.length() - 1);
        signStr.append(key);
        return md5Hex(signStr.toString());
    }

    public static String md5Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new EPayException("MD5 不可用", e);
        }
    }

    public static <K extends Comparable<? super K>, V> Map<K, V> sortByKey(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<K, V>();
        map.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByKey())
                .forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }
}
