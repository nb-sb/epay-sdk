package com.nbsb.epaysdk.model;

import java.math.BigDecimal;

/** 独立退款状态，避免将受理成功当成资金已经退回。 */
public record RefundResult(
    String refundNo,
    String outRefundNo,
    String tradeNo,
    String outTradeNo,
    BigDecimal amount,
    RefundStatus status,
    String rawStatus) {
  public RefundResult {
    status = status == null ? RefundStatus.UNKNOWN : status;
  }

  @Override
  public String toString() {
    return "RefundResult[status=" + status + ", 其余已遮蔽]";
  }
}
