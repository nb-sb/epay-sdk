package com.nbsb.epaysdk.model;

import java.math.BigDecimal;

/** 原始状态保持字符串，不把未知状态归为未支付。 */
public record OrderResult(
    String tradeNo,
    String outTradeNo,
    String merchantId,
    BigDecimal amount,
    OrderStatus status,
    String rawStatus,
    String paymentMethod) {
  public OrderResult {
    status = status == null ? OrderStatus.UNKNOWN : status;
  }

  @Override
  public String toString() {
    return "OrderResult[status=" + status + ", 其余已遮蔽]";
  }
}
