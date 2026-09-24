package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.util.List;

/** 网关可能不返回总数，此时 total 为 null，而非推测为零。 */
public record OrderListResult(List<OrderResult> orders, Long total) {
  public OrderListResult {
    orders = List.copyOf(Checks.required(orders));
    if (total != null && total < 0) throw EPayException.protocol();
  }

  @Override
  public String toString() {
    return "OrderListResult[已遮蔽]";
  }
}
