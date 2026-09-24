package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.math.BigDecimal;

/** 验签成功不替代业务对账、事务和幂等；完成业务处理后才向网关返回 ACK。 */
public record VerifiedNotification(
    String merchantId,
    String tradeNo,
    String outTradeNo,
    BigDecimal amount,
    OrderStatus status,
    String rawStatus,
    String paymentMethod,
    String param) {
  public VerifiedNotification {
    merchantId = Checks.text(merchantId);
    if ((tradeNo == null || tradeNo.isBlank()) && (outTradeNo == null || outTradeNo.isBlank())) {
      throw EPayException.protocol();
    }
    amount = Checks.amount(amount);
    status = status == null ? OrderStatus.UNKNOWN : status;
  }

  public String successAck() {
    return "success";
  }

  @Override
  public String toString() {
    return "VerifiedNotification[status=" + status + ", 其余已遮蔽]";
  }
}
