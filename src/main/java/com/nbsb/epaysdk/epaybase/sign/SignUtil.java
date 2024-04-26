package com.nbsb.epaysdk.epaybase.sign;

import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
/**
* author: Wanghaonan @戏人看戏
* description: 签名验证方法
* create: 2024/4/21 19:53
*/
public class SignUtil {
    //获取Md5
    public static String Body2Md5(EPayBody ePayBody)  {
        //参数存入 map
        Map<String, String> sign = new HashMap<>();
        sign.put("pid", ePayBody.getPid());
        sign.put("type", ePayBody.getType());
        sign.put("out_trade_no", ePayBody.getOut_trade_no());
        sign.put("notify_url", ePayBody.getNotify_url());
        sign.put("return_url", ePayBody.getReturn_url());
        sign.put("name", ePayBody.getName());
        sign.put("money", ePayBody.getMoney());
        //如果是 yzf
        if (!"true".equals(ePayBody.isIs_mzf())) {
            sign.put("device", ePayBody.getDeviceType().getDeviceTypeName());
            sign.put("param", "");
            sign.put("clientip", "192.168.1.100");
        }
        String signStr = map2Md5(sign, ePayBody.getKey());
        return signStr;
    }
    public static String map2Md5(Map<String, String> map,String key)  {
        //根据key升序排序
        map = sortByKey(map);
        String signStr = "";
        //遍历map 转成字符串
        for (Map.Entry<String, String> m : map.entrySet()) {
            String value = m.getValue();
            if (!"sign".equals(m.getKey()) && !"sign_type".equals(m.getKey())&& value != null && !"".equals(value)){
                signStr += m.getKey() + "=" + m.getValue() + "&";
            }
        }
        //去掉最后一个 &
        signStr = signStr.substring(0, signStr.length() - 1);
        //最后拼接上KEY
        signStr += key;
        //转为MD5
        signStr = DigestUtils.md5DigestAsHex(signStr.getBytes());
        return signStr;
    }
    public static <K extends Comparable<? super K>, V > Map<K, V> sortByKey(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<>();
        map.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByKey()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }
}
