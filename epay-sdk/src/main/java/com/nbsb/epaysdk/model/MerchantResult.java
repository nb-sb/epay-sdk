package com.nbsb.epaysdk.model;

import java.math.BigDecimal;

/** 商户查询只暴露所需业务字段，不保留网关返回的密钥或完整账户报文。 */
public record MerchantResult(String merchantId, BigDecimal balance, String rawStatus) {
  @Override
  public String toString() {
    return "MerchantResult[已遮蔽]";
  }
}
