package com.nbsb.epaysdk.protocol.mzf;

import static org.junit.jupiter.api.Assertions.*;

import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.*;
import java.math.BigDecimal;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** 以原仓库 201 行为为基线，仅通过客户端和传输边界验证，不访问真实网关。 */
class MzfLegacyAdapterTest {
  @Test
  void paymentUsesLegacyPostAndExternalMd5VectorWithoutCodeArithmetic() {
    try (EPayClient client =
        client(
            request -> {
              assertEquals("POST", request.method());
              assertEquals("https://pay.example/prefix/pay/apisubmit", request.uri().toString());
              // 独立 Python hashlib 计算的固定值，不从生产签名函数生成期望。
              assertEquals(
                  Map.of(
                      "pid",
                      "1001",
                      "type",
                      "wxpay",
                      "out_trade_no",
                      "O1",
                      "notify_url",
                      "https://shop.example/notify",
                      "return_url",
                      "https://shop.example/return",
                      "name",
                      "demo",
                      "money",
                      "1.20",
                      "sign_type",
                      "MD5",
                      "sign",
                      "dff458a435a2f7db7837ce2688f3da50"),
                  request.parameters());
              return new TransportResponse(
                  200, "{\"code\":201,\"msg\":\"ok\",\"qrcode\":\"https://mzf.example/qr\"}");
            })) {
      GatewayResult<PaymentResult> result = client.createPayment(payment().build());
      assertTrue(result.success());
      assertEquals("201", result.code());
      assertEquals("mzf-legacy201", client.protocolId());
      assertEquals(
          "https://mzf.example/qr",
          ((PaymentAction.QrCode) result.data().actions().get(0)).content());
    }
  }

  @Test
  void non201CodesAreBusinessRejectionsEvenWithNoDataOrTemptingPaymentFields() {
    // 这些是故意构造的反例，不是 200 版的兼容性证据或官方响应样本。
    for (String code : List.of("200", "1", "0", "202", "401", "-1")) {
      for (String data : List.of("", ",\"data\":{}", ",\"data\":\"not-json\"")) {
        try (EPayClient client =
            client(
                request ->
                    new TransportResponse(
                        200,
                        "{\"code\":"
                            + code
                            + ",\"msg\":\"denied\",\"qrcode\":\"qr\""
                            + data
                            + "}"))) {
          for (GatewayResult<?> result :
              List.of(
                  client.createPayment(payment().build()),
                  client.queryOrder(OrderReference.byOutTradeNo("O1")))) {
            assertFalse(result.success());
            assertEquals(code, result.code());
            assertEquals("denied", result.message());
            assertNull(result.data());
          }
        }
      }
    }
  }

  @Test
  void queryUsesLegacyGetOrderNumberAndTypeForObjectAndJsonStringData() {
    // 来源：旧 EPayMZF#doQueryOrder 的 data 解析与 MZFExecute 的查询参数。
    for (boolean stringData : List.of(false, true)) {
      for (OrderReference order :
          List.of(OrderReference.byTradeNo("T1"), OrderReference.byOutTradeNo("O1"))) {
        for (String state : List.of("0", "1", "2", "201", "TRADE_SUCCESS", "future")) {
          try (EPayClient client =
              client(
                  request -> {
                    assertEquals("GET", request.method());
                    assertEquals(
                        "https://pay.example/prefix/pay/chaorder", request.uri().toString());
                    assertEquals(
                        Map.of(
                            "order_no",
                            order.value(),
                            "type",
                            order.type() == OrderReference.Type.TRADE_NO ? "1" : "2"),
                        request.parameters());
                    return new TransportResponse(200, envelope(orderJson(state), stringData));
                  })) {
            GatewayResult<OrderResult> result = client.queryOrder(order);
            assertTrue(result.success());
            assertEquals("201", result.code());
            assertEquals("T1", result.data().tradeNo());
            assertEquals("O1", result.data().outTradeNo());
            assertEquals(new BigDecimal("1.20"), result.data().amount());
            assertEquals(state, result.data().rawStatus());
            assertEquals(
                state.equals("0")
                    ? OrderStatus.PENDING
                    : state.equals("1") ? OrderStatus.PAID : OrderStatus.UNKNOWN,
                result.data().status());
          }
        }
      }
    }
  }

  @Test
  void successfulQueryCannotInventAmountStatusOrMerchantOwnership() {
    for (String field :
        List.of("\"out_trade_no\":\"O1\",", "\"money\":\"1.20\",", "\"status\":\"1\",")) {
      try (EPayClient client =
          client(
              request ->
                  new TransportResponse(200, envelope(orderJson("1").replace(field, ""), false)))) {
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.queryOrder(OrderReference.byOutTradeNo("O1")));
      }
    }
    for (String data :
        List.of(orderJson("1").replace("1001", "1002"), orderJson("1").replace("O1", "other"))) {
      try (EPayClient client =
          client(request -> new TransportResponse(200, envelope(data, true)))) {
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.queryOrder(OrderReference.byOutTradeNo("O1")));
      }
    }
    try (EPayClient client =
        client(
            request ->
                new TransportResponse(
                    200, envelope(orderJson("1").replace("\"pid\":\"1001\",", ""), false)))) {
      assertNull(client.queryOrder(OrderReference.byOutTradeNo("O1")).data().merchantId());
    }
  }

  @Test
  void malformedSuccessDataAndAmountsBecomeProtocolErrors() {
    for (String data :
        List.of(
            "null",
            "[]",
            "1",
            "true",
            "\"not-json\"",
            "\"[]\"",
            "{}",
            "\"{\\\"status\\\":1,\\\"status\\\":0}\"")) {
      try (EPayClient client =
          client(request -> new TransportResponse(200, "{\"code\":201,\"data\":" + data + "}"))) {
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.queryOrder(OrderReference.byOutTradeNo("O1")));
      }
    }
    for (String amount : List.of("NaN", "1e2", "0", "-1", "1.234", " 1.20")) {
      try (EPayClient client =
          client(
              request ->
                  new TransportResponse(
                      200, envelope(orderJson("1").replace("1.20", amount), true)))) {
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.queryOrder(OrderReference.byOutTradeNo("O1")));
      }
    }
    try (EPayClient client = client(request -> new TransportResponse(200, "{\"code\":201}"))) {
      error(
          EPayException.Kind.PROTOCOL, () -> client.queryOrder(OrderReference.byOutTradeNo("O1")));
    }
  }

  @Test
  void onlyCreateAndQueryAreDeclaredAndUnsupportedOperationsNeverSend() {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不支持的能力不得联网");
            })) {
      assertEquals(
          Set.of(Capability.CREATE_PAYMENT, Capability.QUERY_ORDER), client.capabilities());
      error(
          EPayException.Kind.UNSUPPORTED,
          () -> client.verifyNotification(NotificationRequest.fromParameters(Map.of())));
      error(
          EPayException.Kind.UNSUPPORTED,
          () ->
              client.refund(
                  new RefundRequest(OrderReference.byTradeNo("T1"), BigDecimal.ONE, null)));
      error(EPayException.Kind.UNSUPPORTED, () -> client.queryRefund(null));
      error(EPayException.Kind.UNSUPPORTED, client::queryMerchant);
      error(EPayException.Kind.UNSUPPORTED, () -> client.listOrders(new OrderListRequest(0, 10)));
      error(
          EPayException.Kind.UNSUPPORTED,
          () -> client.createPayment(payment().scene(PaymentScene.BROWSER_FORM).build()));
      for (PaymentRequest unsupported :
          List.of(
              payment().clientIp("203.0.113.8").build(),
              payment().device("pc").build(),
              payment().param("0").build(),
              payment().options(PaymentOptions.builder().method("jsapi").build()).build(),
              payment().options(PaymentOptions.builder().openId("id").build()).build(),
              payment().options(PaymentOptions.builder().authCode("id").build()).build(),
              payment().options(PaymentOptions.builder().subAppId("id").build()).build(),
              payment().options(PaymentOptions.builder().subOpenId("id").build()).build())) {
        error(EPayException.Kind.UNSUPPORTED, () -> client.createPayment(unsupported));
      }
    }
  }

  @Test
  void invalidLegacyPaymentFailsLocally() {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("非法参数不得联网");
            })) {
      for (PaymentRequest invalid :
          List.of(
              payment().notifyUrl(null).build(),
              payment().returnUrl(null).build(),
              payment().paymentMethod(" ").build(),
              payment().name("中".repeat(43)).build())) {
        error(EPayException.Kind.VALIDATION, () -> client.createPayment(invalid));
      }
    }
  }

  @Test
  void missingOrUnsafePaymentActionsAreProtocolErrorsNotSuccessfulWrites() {
    for (String body :
        List.of(
            "{\"code\":201}",
            "{\"code\":201,\"payurl\":\"javascript:alert(1)\"}",
            "{\"code\":201,\"qrcode\":{}}",
            "{\"code\":201,\"qrcode\":\"qr\",\"out_trade_no\":\"other\"}")) {
      try (EPayClient client = client(request -> new TransportResponse(200, body))) {
        EPayException error =
            assertThrows(EPayException.class, () -> client.createPayment(payment().build()));
        assertEquals(EPayException.Kind.PROTOCOL, error.kind());
        assertTrue(error.executionUncertain());
        assertNull(error.getCause());
      }
    }
  }

  @Test
  void legacyAdditionalActionsRemainPassiveData() {
    try (EPayClient client =
        client(
            request ->
                new TransportResponse(
                    200,
                    "{\"code\":201,\"payurl\":\"https://pay.example/go\",\"urlscheme\":\"alipays://go\","
                        + "\"h5_qrurl\":\"https://pay.example/h5\",\"code_url\":\"https://pay.example/qr.png\"}"))) {
      List<PaymentAction> actions = client.createPayment(payment().build()).data().actions();
      assertEquals(4, actions.size());
      assertInstanceOf(PaymentAction.Redirect.class, actions.get(0));
      assertInstanceOf(PaymentAction.UrlScheme.class, actions.get(1));
      assertInstanceOf(PaymentAction.Redirect.class, actions.get(2));
      assertInstanceOf(PaymentAction.QrImage.class, actions.get(3));
    }
  }

  @Test
  void rsaCredentialsAreRejectedBeforeAnyRequest() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var keys = generator.generateKeyPair();
    RsaCredentials rsa =
        new RsaCredentials(
            "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----",
            "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----");
    MerchantConfig config =
        MerchantConfig.builder()
            .baseUrl("https://pay.example")
            .merchantId("1001")
            .credentials(rsa)
            .build();
    error(
        EPayException.Kind.CONFIGURATION,
        () ->
            EPayClient.builder()
                .config(config)
                .adapter(new MzfLegacyAdapter())
                .transport(
                    request -> {
                      throw new AssertionError("凭证不匹配不得联网");
                    })
                .build());
  }

  private static String orderJson(String status) {
    return "{\"trade_no\":\"T1\",\"out_trade_no\":\"O1\",\"pid\":\"1001\",\"money\":\"1.20\","
        + "\"status\":\""
        + status
        + "\",\"type\":\"wxpay\"}";
  }

  private static String envelope(String data, boolean stringData) {
    String value =
        stringData ? "\"" + data.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" : data;
    return "{\"code\":201,\"msg\":\"ok\",\"data\":" + value + "}";
  }

  private static void error(EPayException.Kind kind, Executable operation) {
    EPayException error = assertThrows(EPayException.class, operation);
    assertEquals(kind, error.kind());
    assertNull(error.getCause());
    assertFalse(error.executionUncertain());
  }

  private static PaymentRequest.Builder payment() {
    return PaymentRequest.builder()
        .outTradeNo("O1")
        .name("demo")
        .amount(new BigDecimal("1.20"))
        .paymentMethod("wxpay")
        .notifyUrl("https://shop.example/notify")
        .returnUrl("https://shop.example/return");
  }

  private static EPayClient client(HttpTransport transport) {
    return EPayClient.builder()
        .config(
            MerchantConfig.builder()
                .baseUrl("https://pay.example/prefix")
                .merchantId("1001")
                .credentials(new Md5Credentials("secret"))
                .build())
        .adapter(new MzfLegacyAdapter())
        .transport(transport)
        .build();
  }
}
