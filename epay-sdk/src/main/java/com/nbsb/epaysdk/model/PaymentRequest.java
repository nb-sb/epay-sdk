package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.math.BigDecimal;
import java.net.URI;

/** 不可变下单请求；场景特有必填项及字节长度交由协议校验。 */
public record PaymentRequest(
    String outTradeNo,
    String name,
    BigDecimal amount,
    String paymentMethod,
    URI notifyUrl,
    URI returnUrl,
    String clientIp,
    String device,
    String param,
    PaymentScene scene,
    PaymentOptions options) {
  public PaymentRequest {
    outTradeNo = Checks.text(outTradeNo);
    name = Checks.text(name);
    amount = Checks.amount(amount);
    if (notifyUrl != null) notifyUrl = Checks.httpUri(notifyUrl);
    if (returnUrl != null) returnUrl = Checks.httpUri(returnUrl);
    scene = Checks.required(scene);
    options = options == null ? PaymentOptions.defaults() : options;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String toString() {
    return "PaymentRequest[已遮蔽]";
  }

  public static final class Builder {
    private String outTradeNo;
    private String name;
    private BigDecimal amount;
    private String paymentMethod;
    private URI notifyUrl;
    private URI returnUrl;
    private String clientIp;
    private String device;
    private String param;
    private PaymentScene scene = PaymentScene.API;
    private PaymentOptions options = PaymentOptions.defaults();

    private Builder() {}

    public Builder outTradeNo(String value) {
      outTradeNo = value;
      return this;
    }

    public Builder name(String value) {
      name = value;
      return this;
    }

    public Builder amount(BigDecimal value) {
      amount = value;
      return this;
    }

    public Builder paymentMethod(String value) {
      paymentMethod = value;
      return this;
    }

    public Builder notifyUrl(String value) {
      notifyUrl = value == null ? null : Checks.httpUri(value);
      return this;
    }

    public Builder returnUrl(String value) {
      returnUrl = value == null ? null : Checks.httpUri(value);
      return this;
    }

    public Builder clientIp(String value) {
      clientIp = value;
      return this;
    }

    public Builder device(String value) {
      device = value;
      return this;
    }

    public Builder param(String value) {
      param = value;
      return this;
    }

    public Builder scene(PaymentScene value) {
      scene = value;
      return this;
    }

    public Builder options(PaymentOptions value) {
      options = value;
      return this;
    }

    public PaymentRequest build() {
      return new PaymentRequest(
          outTradeNo,
          name,
          amount,
          paymentMethod,
          notifyUrl,
          returnUrl,
          clientIp,
          device,
          param,
          scene,
          options);
    }
  }
}
