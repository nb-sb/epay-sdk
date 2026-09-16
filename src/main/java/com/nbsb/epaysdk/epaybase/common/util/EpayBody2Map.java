package com.nbsb.epaysdk.epaybase.common.util;

import com.alibaba.fastjson.JSON;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayException;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import com.nbsb.epaysdk.epaybase.properties.AccountConfig;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EpayBody2Map {

    public static <T> Map<String, String> beanToMap(T obj) {
        Map<String, String> map = new HashMap<String, String>();
        if (obj == null) {
            return map;
        }
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value != null) {
                    map.put(field.getName(), String.valueOf(value));
                }
            } catch (IllegalAccessException e) {
                throw new EPayException("对象转换Map异常", e);
            }
        }
        return map;
    }

    public static <T> T Map2Bean(Map<String, Object> resultMap, Class<T> type) {
        if (resultMap == null) {
            throw new EPayException("网关返回为空，无法解析 " + type.getSimpleName());
        }
        try {
            return JSON.parseObject(JSON.toJSONString(resultMap), type);
        } catch (Exception e) {
            throw new EPayException("解析响应失败: " + type.getSimpleName(), e);
        }
    }

    public static EPayBody cmd2EPayBody(GetQRCmd cmd) {
        return cmd2EPayBody(cmd, null);
    }

    public static EPayBody cmd2EPayBody(GetQRCmd cmd, MerchantConfig config) {
        EPayBody ePayBody = new EPayBody();
        if (config != null) {
            ePayBody.setPid(config.getAppId());
            ePayBody.setKey(config.getAppKey());
            ePayBody.setClientip(blankToDefault(cmd.getClientIp(), config.getClientIp()));
        } else {
            ePayBody.setPid(AccountConfig.getAppId());
            ePayBody.setKey(AccountConfig.getAppKey());
            ePayBody.setClientip(blankToDefault(cmd.getClientIp(), "127.0.0.1"));
        }
        ePayBody.setMoney(cmd.getAmount());
        ePayBody.setName(cmd.getName());
        ePayBody.setOut_trade_no(cmd.getOrderNo());
        ePayBody.setNotify_url(cmd.getNotify_url());
        ePayBody.setReturn_url(cmd.getReturn_url());
        ePayBody.setType(cmd.getPayType().getMethodName());
        DeviceType device = cmd.getDevice();
        ePayBody.setDeviceType(device);
        ePayBody.setDevice(device.getDeviceTypeName());
        ePayBody.setParam(cmd.getParam() == null ? "" : cmd.getParam());
        return ePayBody;
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
