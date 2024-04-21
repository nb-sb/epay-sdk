package com.nbsb.epaysdk.epaybase.common.util;

import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.properties.AccountConfig;
import com.nbsb.epaysdk.epaybase.reflection.MetaObject;
import com.nbsb.epaysdk.epaybase.reflection.SystemMetaObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class EpayBody2Map {
    public static <T>  Map<String, String> beanToMap(T obj) {
        Map<String, String> map = new HashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                map.put(field.getName(), String.valueOf(field.get(obj)));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                throw new RuntimeException("对象转换Map异常");
            }
        }
        return map;
    }

    public static <T> T Map2Bean(Map<String, Object> resultMap, Class<T> type) {
        T instance = null;
        try {
            instance =type.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
        MetaObject metaObject = SystemMetaObject.forObject(instance);
        for (Object key : resultMap.keySet()) {
            String propertyName = (String) key;
            if (metaObject.hasSetter(propertyName)) {
                String value = String.valueOf(resultMap.get(propertyName));
                Object convertedValue = convertValue(metaObject, propertyName, value);
                metaObject.setValue(propertyName, convertedValue);
            }
        }
        return instance;
    }

    /**
     * 根据setter的类型,将配置文件中的值强转成相应的类型
     */
    private static Object convertValue(MetaObject metaObject, String propertyName, String value) {
        Object convertedValue = value;
        Class<?> targetType = metaObject.getSetterType(propertyName);
        if (targetType == Integer.class || targetType == int.class) {
            convertedValue = Integer.valueOf(value);
        } else if (targetType == Long.class || targetType == long.class) {
            convertedValue = Long.valueOf(value);
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            convertedValue = Boolean.valueOf(value);
        }
        return convertedValue;
    }
    public static EPayBody cmd2EPayBody(GetQRCmd cmd) {
        EPayBody ePayBody = new EPayBody();
        ePayBody.setPid(AccountConfig.getAppId());
        ePayBody.setKey(AccountConfig.getAppKey());
        ePayBody.setMoney(cmd.getAmount());
        ePayBody.setName(cmd.getName());
        ePayBody.setOut_trade_no(cmd.getOrderNo());
        ePayBody.setNotify_url(cmd.getNotify_url());
        ePayBody.setReturn_url(cmd.getReturn_url());
        ePayBody.setType(cmd.getPayType().getMethodName());
        return ePayBody;
    }
}
