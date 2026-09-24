package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.util.List;

/** 下单结果的动作列表不可变；订单最终状态仍需查询或可信通知确认。 */
public record PaymentResult(String tradeNo, String outTradeNo, List<PaymentAction> actions) {
  public PaymentResult {
    actions = List.copyOf(Checks.required(actions));
  }

  @Override
  public String toString() {
    return "PaymentResult[已遮蔽]";
  }
}
