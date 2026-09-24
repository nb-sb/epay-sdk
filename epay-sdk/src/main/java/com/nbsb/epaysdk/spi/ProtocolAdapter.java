package com.nbsb.epaysdk.spi;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.*;
import java.util.Set;

/** 完整业务操作扩展边界；实现必须无可变商户状态并在建客户端前严格匹配凭证。 */
public interface ProtocolAdapter {
  String id();

  Set<Capability> capabilities();

  void validateCredentials(Credentials credentials);

  GatewayResult<PaymentResult> createPayment(ProtocolContext context, PaymentRequest request);

  GatewayResult<OrderResult> queryOrder(ProtocolContext context, OrderReference request);

  GatewayResult<VerifiedNotification> verifyNotification(
      ProtocolContext context, NotificationRequest request);

  default GatewayResult<RefundResult> refund(ProtocolContext context, RefundRequest request) {
    throw EPayException.unsupported();
  }

  default GatewayResult<RefundResult> queryRefund(
      ProtocolContext context, RefundReference request) {
    throw EPayException.unsupported();
  }

  default GatewayResult<MerchantResult> queryMerchant(ProtocolContext context) {
    throw EPayException.unsupported();
  }

  default GatewayResult<OrderListResult> listOrders(
      ProtocolContext context, OrderListRequest request) {
    throw EPayException.unsupported();
  }
}
