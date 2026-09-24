package com.nbsb.epaysdk.protocol.epayv1;

import static org.junit.jupiter.api.Assertions.*;

import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.*;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** 仅从客户端和注入传输边界验证协议，不连接真实网关。 */
class EpayV1AdapterTest {
  @Test
  void refundDialectsAreStrictAndUsePostWithExactlyOneOrderReference() {
    for (EpayV1Adapter.Dialect dialect : EpayV1Adapter.Dialect.values()) {
      for (String code : List.of("0", "1", "200", "201", "-1")) {
        for (OrderReference order :
            List.of(OrderReference.byTradeNo("T1"), OrderReference.byOutTradeNo("O1"))) {
          try (EPayClient client =
              client(
                  new EpayV1Adapter(dialect),
                  request -> {
                    assertEquals("POST", request.method());
                    assertEquals("https://pay.example/prefix/api.php", request.uri().toString());
                    assertEquals(Map.of("act", "refund"), request.queryParameters());
                    assertEquals(
                        Map.of(
                            "pid",
                            "1001",
                            "key",
                            "secret",
                            order.type() == OrderReference.Type.TRADE_NO
                                ? "trade_no"
                                : "out_trade_no",
                            order.value(),
                            "money",
                            "1.20"),
                        request.parameters());
                    return new TransportResponse(200, "{\"code\":" + code + "}");
                  })) {
            GatewayResult<RefundResult> result =
                client.refund(new RefundRequest(order, new BigDecimal("1.20"), null));
            boolean expected = code.equals(dialect == EpayV1Adapter.Dialect.REFERENCE ? "0" : "1");
            assertEquals(expected, result.success());
            assertEquals(code, result.code());
            if (expected) assertEquals(RefundStatus.ACCEPTED, result.data().status());
            else assertNull(result.data());
          }
        }
      }
    }
  }

  /** 文档“提交订单退款”：路由在 query，认证与金额在 UTF-8 POST 表单；不使用真实商户。 */
  @Test
  void refundSendsDocumentedQueryAndFormOverRealHttp() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<List<String>> captured = new AtomicReference<>();
    server.createContext(
        "/prefix/api.php",
        exchange -> {
          try (exchange) {
            captured.set(
                List.of(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] body = "{\"code\":0}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          }
        });
    server.start();
    try (EPayClient client =
        EPayClient.builder()
            .config(
                MerchantConfig.builder()
                    .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/prefix")
                    .merchantId("1001")
                    .credentials(new Md5Credentials("test &+=中文"))
                    .build())
            .build()) {
      assertTrue(
          client
              .refund(
                  new RefundRequest(
                      OrderReference.byOutTradeNo("订单 &+=0"), new BigDecimal("1.20"), null))
              .success());
      List<String> wire = captured.get();
      assertEquals("POST", wire.get(0));
      assertEquals("/prefix/api.php?act=refund", wire.get(1));
      assertEquals("application/x-www-form-urlencoded; charset=UTF-8", wire.get(2));
      Map<String, String> form = new LinkedHashMap<>();
      for (String pair : wire.get(3).split("&")) {
        String[] field = pair.split("=", 2);
        assertNull(
            form.put(
                URLDecoder.decode(field[0], StandardCharsets.UTF_8),
                URLDecoder.decode(field[1], StandardCharsets.UTF_8)));
      }
      assertEquals(
          Map.of("pid", "1001", "key", "test &+=中文", "out_trade_no", "订单 &+=0", "money", "1.20"),
          form);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void defaultAdapterAndExplicitDialectNeverProbeAndRejectWrongCredentials() throws Exception {
    HttpTransport never =
        request -> {
          throw new AssertionError("不得联网");
        };
    try (EPayClient client =
            EPayClient.builder().config(config("1001", "secret")).transport(never).build();
        EPayClient mpay = client(new EpayV1Adapter(EpayV1Adapter.Dialect.MPAY), never)) {
      assertEquals("epay-v1", client.protocolId());
      assertEquals("epay-v1-mpay", mpay.protocolId());
      assertEquals(
          Set.of(
              Capability.CREATE_PAYMENT,
              Capability.QUERY_ORDER,
              Capability.VERIFY_NOTIFICATION,
              Capability.REFUND,
              Capability.QUERY_MERCHANT,
              Capability.LIST_ORDERS),
          client.capabilities());
    }
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
    MerchantConfig wrong =
        MerchantConfig.builder()
            .baseUrl("https://pay.example")
            .merchantId("1001")
            .credentials(rsa)
            .build();
    error(
        EPayException.Kind.CONFIGURATION,
        () ->
            EPayClient.builder()
                .config(wrong)
                .adapter(new EpayV1Adapter())
                .transport(never)
                .build());
  }

  @Test
  void mapiSignsExactWireWithExternalFixedVectorAndOptionalReturnUrl() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              assertEquals("POST", request.method());
              assertEquals("https://pay.example/prefix/mapi.php", request.uri().toString());
              Map<String, String> expected = new LinkedHashMap<>();
              expected.put("pid", "1001");
              expected.put("type", "wxpay");
              expected.put("out_trade_no", "O1");
              expected.put("notify_url", "https://shop.example/notify");
              expected.put("name", "中文 &+=");
              expected.put("money", "1.20");
              expected.put("clientip", "203.0.113.8");
              expected.put("device", "custom-device");
              expected.put("param", "0");
              expected.put("sign_type", "MD5");
              // 独立 Python hashlib.md5 对上述原值排序拼接后加 secret 计算的已知向量。
              expected.put("sign", "261f66f80d71221e83bd94108390396c");
              assertEquals(expected, request.parameters());
              assertThrows(UnsupportedOperationException.class, () -> request.parameters().clear());
              return new TransportResponse(
                  200,
                  "{\"code\":1,\"trade_no\":\"T1\","
                      + "\"payurl\":\"https://pay.example/go\",\"qrcode\":\"weixin://qr\",\"urlscheme\":\"alipays://go\"}");
            })) {
      PaymentResult result =
          client
              .createPayment(payment().name("中文 &+=").device("custom-device").param("0").build())
              .data();
      assertEquals("T1", result.tradeNo());
      assertEquals("O1", result.outTradeNo());
      assertEquals(3, result.actions().size());
      assertInstanceOf(PaymentAction.Redirect.class, result.actions().get(0));
      assertEquals("weixin://qr", ((PaymentAction.QrCode) result.actions().get(1)).content());
      assertEquals("alipays://go", ((PaymentAction.UrlScheme) result.actions().get(2)).url());
    }
  }

  @Test
  void browserFormIsLocalEscapedSignedAndAllowsMissingTypeAndClientIp() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              throw new AssertionError("不得联网");
            })) {
      GatewayResult<PaymentResult> created =
          client.createPayment(
              payment()
                  .scene(PaymentScene.BROWSER_FORM)
                  .paymentMethod(null)
                  .clientIp(null)
                  .name("\"><script>&'")
                  .returnUrl("https://shop.example/return?a=1&b=2")
                  .param("<private>")
                  .build());
      assertTrue(created.success());
      assertNull(created.code());
      PaymentResult result = created.data();
      String html = ((PaymentAction.Form) result.actions().get(0)).html();
      assertTrue(html.startsWith("<form method=\"post\""));
      assertTrue(html.contains("action=\"https://pay.example/prefix/submit.php\""));
      assertTrue(html.contains("&quot;&gt;&lt;script&gt;&amp;&#39;"));
      assertTrue(html.contains("&lt;private&gt;"));
      assertFalse(html.contains("<script>"));
      assertFalse(html.contains("name=\"type\""));
      assertFalse(html.contains("name=\"clientip\""));
      Map<String, String> wire =
          Map.of(
              "pid",
              "1001",
              "out_trade_no",
              "O1",
              "money",
              "1.20",
              "notify_url",
              "https://shop.example/notify",
              "return_url",
              "https://shop.example/return?a=1&b=2",
              "name",
              "\"><script>&'",
              "param",
              "<private>");
      assertTrue(html.contains("name=\"sign\" value=\"" + independentMd5(wire, "secret") + "\""));
    }
  }

  @Test
  void invalidRequestsAndUnsupportedV2OptionsFailBeforeTransport() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              throw new AssertionError("不得联网");
            })) {
      for (PaymentRequest invalid :
          List.of(
              payment().clientIp(null).build(),
              payment().clientIp(" ").build(),
              payment().paymentMethod(null).build(),
              payment().notifyUrl(null).build(),
              payment().device("").build(),
              payment().name("中".repeat(43)).build(),
              payment().name("a".repeat(128)).build(),
              payment().name("\uD800").build(),
              payment().scene(PaymentScene.BROWSER_FORM).returnUrl(null).build())) {
        error(EPayException.Kind.VALIDATION, () -> client.createPayment(invalid));
      }
      for (PaymentOptions options :
          List.of(
              PaymentOptions.builder().method("jsapi").build(),
              PaymentOptions.builder().openId("id").build(),
              PaymentOptions.builder().authCode("").build(),
              PaymentOptions.builder().subAppId("id").build(),
              PaymentOptions.builder().subOpenId("id").build())) {
        error(
            EPayException.Kind.UNSUPPORTED,
            () -> client.createPayment(payment().options(options).build()));
      }
      error(
          EPayException.Kind.UNSUPPORTED,
          () ->
              client.refund(
                  new RefundRequest(
                      OrderReference.byTradeNo("T1"), BigDecimal.ONE, "idempotency-promise")));
      error(EPayException.Kind.UNSUPPORTED, () -> client.queryRefund(null));
      for (OrderListRequest request :
          List.of(new OrderListRequest(1, 10), new OrderListRequest(0, 51))) {
        error(EPayException.Kind.VALIDATION, () -> client.listOrders(request));
      }
    }
  }

  @Test
  void utf8BoundaryAndDeviceAndOptionalReturnUrlArePreserved() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              assertEquals("中".repeat(42) + "a", request.parameters().get("name"));
              assertEquals("future-device", request.parameters().get("device"));
              assertEquals("https://shop.example/return", request.parameters().get("return_url"));
              assertEquals(
                  independentMd5(request.parameters(), "secret"), request.parameters().get("sign"));
              return new TransportResponse(200, "{\"code\":1,\"qrcode\":\"qr\"}");
            })) {
      assertTrue(
          client
              .createPayment(
                  payment()
                      .name("中".repeat(42) + "a")
                      .device("future-device")
                      .returnUrl("https://shop.example/return")
                      .build())
              .success());
    }
  }

  @Test
  void allNonRefundOperationsRejectOtherCodesWithoutRequiringData() {
    for (EpayV1Adapter.Dialect dialect : EpayV1Adapter.Dialect.values()) {
      for (String code : List.of("0", "200", "201", "-1")) {
        try (EPayClient client =
            client(
                new EpayV1Adapter(dialect),
                request ->
                    new TransportResponse(200, "{\"code\":" + code + ",\"msg\":\"denied\"}"))) {
          List<GatewayResult<?>> results =
              List.of(
                  client.createPayment(payment().build()),
                  client.queryOrder(OrderReference.byTradeNo("T1")),
                  client.queryMerchant(),
                  client.listOrders(new OrderListRequest(0, 10)));
          for (GatewayResult<?> result : results) {
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
  void queryUsesGetAuthenticationAndPreservesUnknownAndRefundStates() {
    for (String raw : List.of("0", "1", "2", "3", "REFUNDED", "future")) {
      for (OrderReference order :
          List.of(OrderReference.byTradeNo("T1"), OrderReference.byOutTradeNo("O1"))) {
        try (EPayClient client =
            client(
                new EpayV1Adapter(),
                request -> {
                  assertEquals("GET", request.method());
                  assertEquals("/prefix/api.php", request.uri().getPath());
                  assertEquals(
                      Map.of(
                          "act",
                          "order",
                          "pid",
                          "1001",
                          "key",
                          "secret",
                          order.type() == OrderReference.Type.TRADE_NO
                              ? "trade_no"
                              : "out_trade_no",
                          order.value()),
                      request.parameters());
                  return new TransportResponse(200, orderJson(raw));
                })) {
          OrderResult result = client.queryOrder(order).data();
          assertEquals(raw, result.rawStatus());
          assertEquals(
              raw.equals("0")
                  ? OrderStatus.PENDING
                  : raw.equals("1") ? OrderStatus.PAID : OrderStatus.UNKNOWN,
              result.status());
          assertEquals(new BigDecimal("1.20"), result.amount());
        }
      }
    }
  }

  @Test
  void incompleteOrMalformedSuccessfulQueriesAreProtocolErrors() {
    List<String> invalid =
        new ArrayList<>(List.of("{}", "{\"code\":1}", "[]", "{\"code\":1,\"code\":1}"));
    for (String field :
        List.of(
            "\"trade_no\":\"T1\",",
            "\"out_trade_no\":\"O1\",",
            "\"pid\":\"1001\",",
            "\"money\":\"1.20\",",
            "\"status\":\"1\",")) invalid.add(orderJson("1").replace(field, ""));
    for (String amount : List.of("NaN", "1e2", "1.234", "0", "-1", " 1.20", "")) {
      invalid.add(orderJson("1").replace("1.20", amount));
    }
    invalid.add(orderJson("1").replace("1001", "1002"));
    invalid.add(orderJson("1").replace("T1", "T2"));
    for (String body : invalid) {
      try (EPayClient client =
          client(new EpayV1Adapter(), request -> new TransportResponse(200, body))) {
        error(EPayException.Kind.PROTOCOL, () -> client.queryOrder(OrderReference.byTradeNo("T1")));
      }
    }
  }

  @Test
  void listUsesAlignedPagesAndNeverGuessesTotal() {
    for (String count : List.of("", ",\"count\":123")) {
      try (EPayClient client =
          client(
              new EpayV1Adapter(),
              request -> {
                assertEquals("GET", request.method());
                assertEquals("/prefix/api.php", request.uri().getPath());
                assertEquals(
                    Map.of(
                        "act", "orders", "pid", "1001", "key", "secret", "page", "3", "limit",
                        "50"),
                    request.parameters());
                return new TransportResponse(
                    200, "{\"code\":1,\"data\":[" + orderJson("2") + "]" + count + "}");
              })) {
        OrderListResult result = client.listOrders(new OrderListRequest(100, 50)).data();
        assertEquals(1, result.orders().size());
        assertEquals(OrderStatus.UNKNOWN, result.orders().get(0).status());
        assertEquals(count.isEmpty() ? null : 123L, result.total());
      }
    }
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              assertEquals("2147483648", request.parameters().get("page"));
              return new TransportResponse(200, "{\"code\":1,\"data\":[]}");
            })) {
      assertNull(client.listOrders(new OrderListRequest(Integer.MAX_VALUE, 1)).data().total());
    }
    for (String count : List.of("-1", "1.5", "9223372036854775808", "\"bad\"")) {
      try (EPayClient client =
          client(
              new EpayV1Adapter(),
              request ->
                  new TransportResponse(200, "{\"code\":1,\"data\":[],\"count\":" + count + "}"))) {
        error(EPayException.Kind.PROTOCOL, () -> client.listOrders(new OrderListRequest(0, 10)));
      }
    }
  }

  @Test
  void merchantIgnoresReturnedKeyAndOnlyExposesBusinessWhitelist() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              assertEquals("GET", request.method());
              assertEquals("/prefix/api.php", request.uri().getPath());
              assertEquals(
                  Map.of("act", "query", "pid", "1001", "key", "secret"), request.parameters());
              return new TransportResponse(
                  200,
                  "{\"code\":1,\"pid\":1001,\"money\":88.50,\"active\":1,"
                      + "\"key\":{\"never-copy-this\":true},\"msg\":\"private-server-message\"}");
            })) {
      GatewayResult<MerchantResult> result = client.queryMerchant();
      assertEquals(new BigDecimal("88.50"), result.data().balance());
      assertEquals("1001", result.data().merchantId());
      assertNull(result.message());
      assertFalse(result.toString().contains("private"));
    }
  }

  @Test
  void notificationVerifiesOriginalSnapshotIncludingUnknownParametersAndKeepsParam() {
    Map<String, String> original = notification("TRADE_SUCCESS");
    original.put("future_field", "中文 &+=0");
    original.put("sign", independentMd5(original, "secret"));
    NotificationRequest snapshot = NotificationRequest.fromParameters(original);
    original.put("money", "999");
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              throw new AssertionError("不得联网");
            })) {
      GatewayResult<VerifiedNotification> result = client.verifyNotification(snapshot);
      assertTrue(result.success());
      assertNull(result.code());
      assertEquals(OrderStatus.PAID, result.data().status());
      assertEquals(new BigDecimal("1.20"), result.data().amount());
      assertEquals("private &+=参数", result.data().param());
      assertEquals("success", result.data().successAck());
      Map<String, String> tampered = new LinkedHashMap<>(snapshot.parameters());
      tampered.put("future_field", "different");
      error(
          EPayException.Kind.SIGNATURE,
          () -> client.verifyNotification(NotificationRequest.fromParameters(tampered)));
      tampered.remove("future_field");
      error(
          EPayException.Kind.SIGNATURE,
          () -> client.verifyNotification(NotificationRequest.fromParameters(tampered)));
      for (String state : List.of("TRADE_FAILED", "TRADE_CLOSED", "1", "future")) {
        VerifiedNotification unknown =
            client.verifyNotification(signedNotification(notification(state))).data();
        assertEquals(OrderStatus.UNKNOWN, unknown.status());
        assertEquals(state, unknown.rawStatus());
      }
    }
  }

  @Test
  void signedMalformedNotificationsAreNeverBusinessRejectionsOrRawExceptions() {
    try (EPayClient client =
        client(
            new EpayV1Adapter(),
            request -> {
              throw new AssertionError("不得联网");
            })) {
      for (String field :
          List.of("pid", "trade_no", "out_trade_no", "money", "trade_status", "type", "name")) {
        Map<String, String> values = notification("TRADE_SUCCESS");
        values.remove(field);
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.verifyNotification(signedNotification(values)));
        values.put(field, " ");
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.verifyNotification(signedNotification(values)));
      }
      for (String amount : List.of("NaN", "0", "-1", "1.001", "1.000", "1e2", " 1.00")) {
        Map<String, String> values = notification("TRADE_SUCCESS");
        values.put("money", amount);
        error(
            EPayException.Kind.PROTOCOL,
            () -> client.verifyNotification(signedNotification(values)));
      }
      for (String signType : List.of("", "RSA", "md5")) {
        Map<String, String> values = notification("TRADE_SUCCESS");
        values.put("sign_type", signType);
        error(
            EPayException.Kind.SIGNATURE,
            () -> client.verifyNotification(signedNotification(values)));
      }
      Map<String, String> missingType = notification("TRADE_SUCCESS");
      missingType.remove("sign_type");
      error(
          EPayException.Kind.SIGNATURE,
          () -> client.verifyNotification(signedNotification(missingType)));
      Map<String, String> wrongMerchant = notification("TRADE_SUCCESS");
      wrongMerchant.put("pid", "1002");
      error(
          EPayException.Kind.SIGNATURE,
          () -> client.verifyNotification(signedNotification(wrongMerchant)));
      error(
          EPayException.Kind.PROTOCOL,
          () -> client.verifyNotification(signedNotification(Map.of("sign_type", "MD5"))));
      for (String sign : List.of("", "invalid", "0".repeat(32))) {
        error(
            EPayException.Kind.SIGNATURE,
            () ->
                client.verifyNotification(
                    NotificationRequest.fromParameters(Map.of("sign", sign))));
      }
    }
  }

  @Test
  void sharedAdapterKeepsConcurrentMerchantSecretsAndParametersIsolated() throws Exception {
    EpayV1Adapter adapter = new EpayV1Adapter();
    AtomicInteger calls = new AtomicInteger();
    CountDownLatch simultaneous = new CountDownLatch(2);
    HttpTransport transport =
        request -> {
          simultaneous.countDown();
          try {
            assertTrue(simultaneous.await(5, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
          }
          String pid = request.parameters().get("pid");
          String secret = pid.equals("1001") ? "key-one" : "key-two";
          assertEquals(
              independentMd5(request.parameters(), secret), request.parameters().get("sign"));
          assertEquals(pid + "-order", request.parameters().get("out_trade_no"));
          calls.incrementAndGet();
          return new TransportResponse(200, "{\"code\":1,\"qrcode\":\"qr\"}");
        };
    var executor = Executors.newFixedThreadPool(2);
    try (EPayClient first =
            EPayClient.builder()
                .config(config("1001", "key-one"))
                .adapter(adapter)
                .transport(transport)
                .build();
        EPayClient second =
            EPayClient.builder()
                .config(config("1002", "key-two"))
                .adapter(adapter)
                .transport(transport)
                .build()) {
      List<Callable<Boolean>> jobs = new ArrayList<>();
      for (int i = 0; i < 40; i++) {
        boolean one = i % 2 == 0;
        jobs.add(
            () ->
                (one ? first : second)
                    .createPayment(payment().outTradeNo(one ? "1001-order" : "1002-order").build())
                    .success());
      }
      for (var result : executor.invokeAll(jobs)) assertTrue(result.get());
      assertEquals(40, calls.get());
    } finally {
      executor.shutdownNow();
    }
  }

  private static PaymentRequest.Builder payment() {
    return PaymentRequest.builder()
        .outTradeNo("O1")
        .name("demo")
        .amount(new BigDecimal("1.20"))
        .paymentMethod("wxpay")
        .notifyUrl("https://shop.example/notify")
        .clientIp("203.0.113.8");
  }

  private static String orderJson(String status) {
    return "{\"code\":1,\"trade_no\":\"T1\",\"out_trade_no\":\"O1\",\"pid\":\"1001\","
        + "\"money\":\"1.20\",\"status\":\""
        + status
        + "\",\"type\":\"wxpay\"}";
  }

  private static Map<String, String> notification(String status) {
    return new LinkedHashMap<>(
        Map.of(
            "pid",
            "1001",
            "trade_no",
            "T1",
            "out_trade_no",
            "O1",
            "money",
            "1.20",
            "trade_status",
            status,
            "type",
            "wxpay",
            "name",
            "测试商品",
            "sign_type",
            "MD5",
            "param",
            "private &+=参数"));
  }

  private static NotificationRequest signedNotification(Map<String, String> original) {
    Map<String, String> values = new LinkedHashMap<>(original);
    values.put("sign", independentMd5(values, "secret"));
    return NotificationRequest.fromParameters(values);
  }

  /** 测试侧独立 JCA 实现，不调用生产签名函数构造预期。 */
  private static String independentMd5(Map<String, String> values, String secret) {
    String canonical =
        new TreeMap<>(values)
            .entrySet().stream()
                .filter(
                    e ->
                        !e.getKey().equals("sign")
                            && !e.getKey().equals("sign_type")
                            && !e.getValue().isEmpty())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("MD5")
                  .digest((canonical + secret).getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  private static void error(EPayException.Kind kind, Executable operation) {
    EPayException error = assertThrows(EPayException.class, operation);
    assertEquals(kind, error.kind());
    assertNull(error.getCause());
    assertFalse(error.executionUncertain());
  }

  private static EPayClient client(EpayV1Adapter adapter, HttpTransport transport) {
    return EPayClient.builder()
        .config(config("1001", "secret"))
        .adapter(adapter)
        .transport(transport)
        .build();
  }

  private static MerchantConfig config(String pid, String key) {
    return MerchantConfig.builder()
        .baseUrl("https://pay.example/prefix")
        .merchantId(pid)
        .credentials(new Md5Credentials(key))
        .build();
  }
}
