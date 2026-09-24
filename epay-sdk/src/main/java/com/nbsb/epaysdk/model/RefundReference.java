package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;

/** 退款标识与订单标识分离，避免混用订单查询接口。 */
public record RefundReference(Type type, String value) {
  public enum Type {
    REFUND_NO,
    OUT_REFUND_NO
  }

  public RefundReference {
    type = Checks.required(type);
    value = Checks.text(value);
  }

  public static RefundReference byRefundNo(String value) {
    return new RefundReference(Type.REFUND_NO, value);
  }

  public static RefundReference byOutRefundNo(String value) {
    return new RefundReference(Type.OUT_REFUND_NO, value);
  }

  @Override
  public String toString() {
    return "RefundReference[type=" + type + ", value=已遮蔽]";
  }
}
