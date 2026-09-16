package com.nbsb.epaysdk.spring;

import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code nbsb.pay.*} binding for Spring Boot.
 */
@ConfigurationProperties(prefix = "nbsb.pay")
public class EPayProperties {

    /**
     * yzf or mzf.
     */
    private String type = "yzf";

    private Account account = new Account();

    private Http http = new Http();

    public PayType payType() {
        if (type == null) {
            return PayType.YZF;
        }
        return PayType.valueOf(type.trim().toUpperCase());
    }

    public MerchantConfig toMerchantConfig() {
        return MerchantConfig.builder()
                .url(account.getUrl())
                .appId(account.getAppId())
                .appKey(account.getAppKey())
                .clientIp(account.getClientIp())
                .connectTimeoutMs(http.getConnectTimeoutMs())
                .responseTimeoutMs(http.getResponseTimeoutMs())
                .build();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public Http getHttp() {
        return http;
    }

    public void setHttp(Http http) {
        this.http = http;
    }

    public static class Account {
        private String url;
        private String appId;
        private String appKey;
        private String clientIp;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getClientIp() {
            return clientIp;
        }

        public void setClientIp(String clientIp) {
            this.clientIp = clientIp;
        }
    }

    public static class Http {
        private int connectTimeoutMs = 5000;
        private int responseTimeoutMs = 15000;

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }
    }
}
