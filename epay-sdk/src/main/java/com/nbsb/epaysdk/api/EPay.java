package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.model.*;
import java.time.Duration;
import java.util.Set;

/** 小型业务接口，不泄漏 HTTP、JSON 库或任意 Map 执行入口。 */
public interface EPay extends AutoCloseable {
  GatewayResult<PaymentResult> createPayment(PaymentRequest request);

  GatewayResult<OrderResult> queryOrder(OrderReference request);

  /** 单次请求预算覆盖，范围为 1 毫秒至一天，实际不超过客户端默认请求预算。非法时长属于参数错误。 */
  GatewayResult<OrderResult> queryOrder(OrderReference request, Duration requestTimeout);

  GatewayResult<VerifiedNotification> verifyNotification(NotificationRequest request);

  GatewayResult<RefundResult> refund(RefundRequest request);

  GatewayResult<RefundResult> queryRefund(RefundReference request);

  GatewayResult<MerchantResult> queryMerchant();

  GatewayResult<OrderListResult> listOrders(OrderListRequest request);

  Set<Capability> capabilities();

  String protocolId();

  @Override
  void close();
}
