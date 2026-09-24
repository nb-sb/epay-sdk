package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.math.BigDecimal;

/** 商户退款号的协议必填性由适配器决定，退款金额永不自动舍入。 */
public record RefundRequest(OrderReference orderReference, BigDecimal amount, String outRefundNo) {
  public RefundRequest {
    orderReference = Checks.required(orderReference);
    amount = Checks.amount(amount);
    if (outRefundNo != null) outRefundNo = Checks.text(outRefundNo);
  }

  @Override
  public String toString() {
    return "RefundRequest[已遮蔽]";
  }
}
