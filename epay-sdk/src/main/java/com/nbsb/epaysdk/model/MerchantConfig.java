package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.net.URI;

/** 每个客户端独立的不可变配置；基础地址保留部署前缀。 */
public final class MerchantConfig {
  private final URI baseUrl;
  private final String merchantId;
  private final Credentials credentials;

  private MerchantConfig(Builder builder) {
    try {
      URI parsed = Checks.safePath(Checks.httpUri(builder.baseUrl));
      if (parsed.getRawQuery() != null || !parsed.equals(parsed.normalize())) {
        throw EPayException.configuration();
      }
      String raw = parsed.toASCIIString();
      baseUrl = URI.create(raw.endsWith("/") ? raw : raw + "/");
      merchantId = Checks.text(builder.merchantId);
      credentials = Checks.required(builder.credentials);
      if (builder.mixedCredentials) throw EPayException.configuration();
    } catch (RuntimeException e) {
      throw EPayException.configuration();
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public URI baseUrl() {
    return baseUrl;
  }

  public String merchantId() {
    return merchantId;
  }

  public Credentials credentials() {
    return credentials;
  }

  @Override
  public String toString() {
    return "MerchantConfig[已遮蔽]";
  }

  public static final class Builder {
    private String baseUrl;
    private String merchantId;
    private Credentials credentials;
    private Class<? extends Credentials> credentialType;
    private boolean mixedCredentials;

    private Builder() {}

    public Builder baseUrl(String value) {
      baseUrl = value;
      return this;
    }

    public Builder merchantId(String value) {
      merchantId = value;
      return this;
    }

    public Builder credentials(Credentials value) {
      if (value != null) {
        if (credentialType != null && credentialType != value.getClass()) mixedCredentials = true;
        credentialType = value.getClass();
      }
      credentials = value;
      return this;
    }

    public MerchantConfig build() {
      return new MerchantConfig(this);
    }
  }
}
