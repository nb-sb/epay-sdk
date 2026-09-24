package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;

/** 标识类型与值成对出现，平台单号和商户单号不能同时提交。 */
public record OrderReference(Type type, String value) {
  public enum Type {
    TRADE_NO,
    OUT_TRADE_NO
  }

  public OrderReference {
    type = Checks.required(type);
    value = Checks.text(value);
  }

  public static OrderReference byTradeNo(String value) {
    return new OrderReference(Type.TRADE_NO, value);
  }

  public static OrderReference byOutTradeNo(String value) {
    return new OrderReference(Type.OUT_TRADE_NO, value);
  }

  @Override
  public String toString() {
    return "OrderReference[type=" + type + ", value=已遮蔽]";
  }
}
