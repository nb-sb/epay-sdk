package com.nbsb.epaysdk.spring;

import com.nbsb.epaysdk.model.Capability;
import com.nbsb.epaysdk.model.Credentials;
import com.nbsb.epaysdk.model.HttpOptions;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.model.RsaCredentials;
import java.time.Duration;
import java.util.EnumSet;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/** 仅在自动装配启用时绑定；凭据只接受文字，不读取外部资源或全局静态配置。 */
@ConfigurationProperties("epay")
public class EPayProperties {
  private boolean enabled = true;
  private String baseUrl;
  private String merchantId;
  private String protocol = "auto";
  private final CredentialProperties credentials = new CredentialProperties();
  private final Http http = new Http();
  private EnumSet<Capability> disabledCapabilities = EnumSet.noneOf(Capability.class);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getMerchantId() {
    return merchantId;
  }

  public void setMerchantId(String merchantId) {
    this.merchantId = merchantId;
  }

  public String getProtocol() {
    return protocol;
  }

  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  public CredentialProperties getCredentials() {
    return credentials;
  }

  public Http getHttp() {
    return http;
  }

  public EnumSet<Capability> getDisabledCapabilities() {
    return disabledCapabilities.clone();
  }

  public void setDisabledCapabilities(EnumSet<Capability> disabledCapabilities) {
    this.disabledCapabilities =
        disabledCapabilities == null
            ? EnumSet.noneOf(Capability.class)
            : disabledCapabilities.clone();
  }

  MerchantConfig merchantConfig() {
    if (!StringUtils.hasText(baseUrl)) {
      throw new IllegalArgumentException("epay.base-url 必须是有效的 HTTP(S) 基础地址");
    }
    if (!StringUtils.hasText(merchantId)) {
      throw new IllegalArgumentException("epay.merchant-id 不能为空");
    }
    Credentials resolvedCredentials = credentials.resolve();
    try {
      return MerchantConfig.builder()
          .baseUrl(baseUrl)
          .merchantId(merchantId)
          .credentials(resolvedCredentials)
          .build();
    } catch (RuntimeException ignored) {
      throw new IllegalArgumentException("epay.base-url 或 epay.merchant-id 无效；地址不得含用户信息、查询参数或片段");
    }
  }

  HttpOptions httpOptions() {
    try {
      return new HttpOptions(
          http.connectTimeout,
          http.connectionRequestTimeout,
          http.responseTimeout,
          http.requestTimeout);
    } catch (RuntimeException ignored) {
      throw new IllegalArgumentException("epay.http 的各项超时必须为正且不超过一天");
    }
  }

  @Override
  public String toString() {
    return "EPayProperties[已遮蔽]";
  }

  /** MD5 与 RSA 二选一；RSA 要求 PKCS8 商户私钥及 X509 平台公钥 PEM。 */
  public static class CredentialProperties {
    private String md5Key;
    private String merchantPrivateKey;
    private String platformPublicKey;

    public String getMd5Key() {
      return md5Key;
    }

    public void setMd5Key(String md5Key) {
      this.md5Key = md5Key;
    }

    public String getMerchantPrivateKey() {
      return merchantPrivateKey;
    }

    public void setMerchantPrivateKey(String merchantPrivateKey) {
      this.merchantPrivateKey = merchantPrivateKey;
    }

    public String getPlatformPublicKey() {
      return platformPublicKey;
    }

    public void setPlatformPublicKey(String platformPublicKey) {
      this.platformPublicKey = platformPublicKey;
    }

    private Credentials resolve() {
      boolean md5 = md5Key != null;
      boolean rsa = merchantPrivateKey != null || platformPublicKey != null;
      if (md5 && rsa) {
        throw new IllegalArgumentException("epay.credentials 不得混用 MD5 与 RSA 凭据");
      }
      if (rsa) {
        if (!StringUtils.hasText(merchantPrivateKey) || !StringUtils.hasText(platformPublicKey)) {
          throw new IllegalArgumentException(
              "epay.credentials 必须同时提供 merchant-private-key 和 platform-public-key");
        }
        try {
          return RsaCredentials.fromPem(merchantPrivateKey, platformPublicKey);
        } catch (RuntimeException ignored) {
          throw new IllegalArgumentException(
              "epay.credentials RSA 凭据无效：需要至少 2048 位的 PKCS8 商户私钥和 X509 平台公钥 PEM");
        }
      }
      if (!StringUtils.hasText(md5Key)) {
        throw new IllegalArgumentException("epay.credentials 必须提供非空 md5-key 或完整 RSA 凭据");
      }
      try {
        return new Md5Credentials(md5Key);
      } catch (RuntimeException ignored) {
        throw new IllegalArgumentException("epay.credentials.md5-key 无效");
      }
    }

    @Override
    public String toString() {
      return "CredentialProperties[已遮蔽]";
    }
  }

  /** 各阶段超时与总请求预算独立绑定，默认值与核心一致。 */
  public static class Http {
    private Duration connectTimeout = HttpOptions.defaults().connectTimeout();
    private Duration connectionRequestTimeout = HttpOptions.defaults().connectionRequestTimeout();
    private Duration responseTimeout = HttpOptions.defaults().responseTimeout();
    private Duration requestTimeout = HttpOptions.defaults().requestTimeout();

    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    public Duration getConnectionRequestTimeout() {
      return connectionRequestTimeout;
    }

    public void setConnectionRequestTimeout(Duration connectionRequestTimeout) {
      this.connectionRequestTimeout = connectionRequestTimeout;
    }

    public Duration getResponseTimeout() {
      return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
      this.responseTimeout = responseTimeout;
    }

    public Duration getRequestTimeout() {
      return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
      this.requestTimeout = requestTimeout;
    }
  }
}
