package com.nbsb.epaysdk.epaybase.properties;

/**
* author: Wanghaonan @戏人看戏
* description: 信息
* create: 2024/4/21 15:34
*/
public class AccountConfig {
    /**
     * 商户id
     */
    private static String appId;
    /**
     * 商户密钥
     */
    private static String appKey;

    private static String url;

    public static String getUrl() {
        return url;
    }

    public static void setUrl(String url) {
        AccountConfig.url = url;
    }

    public static String getAppId() {
        return appId;
    }

    public static void setAppId(String appId) {
        AccountConfig.appId = appId;
    }

    public static String getAppKey() {
        return appKey;
    }

    public static void setAppKey(String appKey) {
        AccountConfig.appKey = appKey;
    }
}
