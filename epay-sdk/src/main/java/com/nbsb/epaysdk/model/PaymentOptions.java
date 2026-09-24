package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;

/** 受控支付选项，不允许覆盖商户、订单、金额或签名字段。 */
public record PaymentOptions(
    String method,
    String openId,
    String authCode,
    String subAppId,
    String subOpenId,
    Boolean isApplet) {
  public PaymentOptions {
    method = method == null ? "web" : Checks.text(method);
  }

  /** 保留原五参数构造；未指定小程序标记时不发送 is_applet。 */
  public PaymentOptions(
      String method, String openId, String authCode, String subAppId, String subOpenId) {
    this(method, openId, authCode, subAppId, subOpenId, null);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static PaymentOptions defaults() {
    return builder().build();
  }

  @Override
  public String toString() {
    return "PaymentOptions[已遮蔽]";
  }

  public static final class Builder {
    private String method = "web";
    private String openId;
    private String authCode;
    private String subAppId;
    private String subOpenId;
    private Boolean isApplet;

    private Builder() {}

    public Builder method(String value) {
      method = value;
      return this;
    }

    public Builder openId(String value) {
      openId = value;
      return this;
    }

    public Builder authCode(String value) {
      authCode = value;
      return this;
    }

    public Builder subAppId(String value) {
      subAppId = value;
      return this;
    }

    public Builder subOpenId(String value) {
      subOpenId = value;
      return this;
    }

    /** 参考 V2 JSAPI 的 is_applet：true/false 分别发送 1/0，null 不发送。 */
    public Builder isApplet(Boolean value) {
      isApplet = value;
      return this;
    }

    public PaymentOptions build() {
      return new PaymentOptions(method, openId, authCode, subAppId, subOpenId, isApplet);
    }
  }
}
