package com.nbsb.epaysdk.core.config;

import com.nbsb.epaysdk.core.exception.EPayConfigException;
import com.nbsb.epaysdk.epaybase.properties.AccountConfig;

/**
 * Per-client merchant credentials and HTTP settings.
 * Prefer this over the process-wide {@link AccountConfig} statics.
 */
public class MerchantConfig {

    private final String url;
    private final String appId;
    private final String appKey;
    private final String clientIp;
    private final int connectTimeoutMs;
    private final int responseTimeoutMs;

    private MerchantConfig(Builder builder) {
        this.url = normalizeUrl(builder.url);
        this.appId = builder.appId;
        this.appKey = builder.appKey;
        this.clientIp = isBlank(builder.clientIp) ? "127.0.0.1" : builder.clientIp.trim();
        this.connectTimeoutMs = builder.connectTimeoutMs > 0 ? builder.connectTimeoutMs : 5000;
        this.responseTimeoutMs = builder.responseTimeoutMs > 0 ? builder.responseTimeoutMs : 15000;
        validate();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fallback for existing {@code nbsb.pay.account.*} / {@link AccountConfig} users.
     */
    public static MerchantConfig fromAccountConfig() {
        return builder()
                .url(AccountConfig.getUrl())
                .appId(AccountConfig.getAppId())
                .appKey(AccountConfig.getAppKey())
                .build();
    }

    public String getUrl() {
        return url;
    }

    public String getAppId() {
        return appId;
    }

    public String getAppKey() {
        return appKey;
    }

    public String getClientIp() {
        return clientIp;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getResponseTimeoutMs() {
        return responseTimeoutMs;
    }

    public String join(String path) {
        if (path == null || path.isEmpty()) {
            return url;
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        return url + relative;
    }

    private void validate() {
        if (isBlank(url)) {
            throw new EPayConfigException("支付网关 url 未配置，请设置 nbsb.pay.account.url 或 MerchantConfig.url");
        }
        if (isBlank(appId)) {
            throw new EPayConfigException("商户 appId 未配置，请设置 nbsb.pay.account.appId 或 MerchantConfig.appId");
        }
        if (isBlank(appKey)) {
            throw new EPayConfigException("商户 appKey 未配置，请设置 nbsb.pay.account.appKey 或 MerchantConfig.appKey");
        }
    }

    private static String normalizeUrl(String raw) {
        if (isBlank(raw)) {
            return raw;
        }
        String trimmed = raw.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Builder {
        private String url;
        private String appId;
        private String appKey;
        private String clientIp;
        private int connectTimeoutMs = 5000;
        private int responseTimeoutMs = 15000;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder appId(String appId) {
            this.appId = appId;
            return this;
        }

        public Builder appKey(String appKey) {
            this.appKey = appKey;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder connectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder responseTimeoutMs(int responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
            return this;
        }

        public MerchantConfig build() {
            return new MerchantConfig(this);
        }
    }
}
