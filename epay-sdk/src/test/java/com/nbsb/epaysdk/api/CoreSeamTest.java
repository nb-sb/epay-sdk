package com.nbsb.epaysdk.api;

import static org.junit.jupiter.api.Assertions.*;

import com.nbsb.epaysdk.internal.ApacheHttpTransport;
import com.nbsb.epaysdk.internal.FormCodec;
import com.nbsb.epaysdk.internal.Signatures;
import com.nbsb.epaysdk.internal.StrictJson;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.protocol.epayv1.EpayV1Adapter;
import com.nbsb.epaysdk.protocol.epayv2.EpayV2Adapter;
import com.nbsb.epaysdk.protocol.mzf.MzfLegacyAdapter;
import com.nbsb.epaysdk.spi.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 仅通过客户端与传输 Seam 验证行为，不访问私有实现。 */
class CoreSeamTest {
  @Test
  void protocolCatalogIsTheOnlyIdSource() {
    assertNull(EPayClient.adapterFor("auto"));
    assertNull(EPayClient.adapterFor(" Auto "));
    assertEquals(
        EpayV1Adapter.REFERENCE_ID, EPayClient.adapterFor(EpayV1Adapter.REFERENCE_ID).id());
    assertEquals(EpayV1Adapter.MPAY_ID, EPayClient.adapterFor("EPAY-V1-MPAY").id());
    assertEquals(
        EpayV2Adapter.REFERENCE_ID, EPayClient.adapterFor(EpayV2Adapter.REFERENCE_ID).id());
    assertEquals(EpayV2Adapter.XARR_ID, EPayClient.adapterFor(EpayV2Adapter.XARR_ID).id());
    assertEquals(MzfLegacyAdapter.ID, EPayClient.adapterFor(MzfLegacyAdapter.ID).id());
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(EPayException.class, () -> EPayClient.adapterFor(null)).kind());
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(EPayException.class, () -> EPayClient.adapterFor("unknown-test-marker"))
            .kind());
    String choices = EPayClient.protocolChoices();
    assertEquals(
        "auto、"
            + EpayV1Adapter.REFERENCE_ID
            + "、"
            + EpayV1Adapter.MPAY_ID
            + "、"
            + EpayV2Adapter.REFERENCE_ID
            + "、"
            + EpayV2Adapter.XARR_ID
            + "、"
            + MzfLegacyAdapter.ID,
        choices);
    assertFalse(choices.contains("unknown-test-marker"));
  }

  @Test
  void explicitAdapterWinsAndExternalTransportRemainsOpen() {
    AtomicInteger closed = new AtomicInteger();
    AtomicInteger sent = new AtomicInteger();
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public TransportResponse execute(TransportRequest request) {
            sent.incrementAndGet();
            assertEquals("https://pay.example/prefix/order", request.uri().toString());
            return new TransportResponse(200, "拒绝");
          }

          @Override
          public void close() {
            closed.incrementAndGet();
          }
        };
    EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(transport)
            .build();
    assertEquals("test", client.protocolId());
    GatewayResult<OrderResult> result =
        client.queryOrder(OrderReference.byOutTradeNo("private-order"));
    assertFalse(result.success());
    assertEquals("DENIED", result.code());
    assertFalse(result.toString().contains("原始消息"));
    client.close();
    client.close();
    assertEquals(0, closed.get());
    assertEquals(
        EPayException.Kind.CLOSED,
        assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byOutTradeNo("x")))
            .kind());
    assertEquals(1, sent.get());
  }

  @Test
  void disabledCapabilityFailsBeforeTransport() {
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .disabledCapabilities(Set.of(Capability.QUERY_ORDER))
            .transport(
                request -> {
                  sent.incrementAndGet();
                  return new TransportResponse(200, "");
                })
            .build()) {
      assertFalse(client.capabilities().contains(Capability.QUERY_ORDER));
      assertEquals(
          EPayException.Kind.UNSUPPORTED,
          assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("x")))
              .kind());
      assertEquals(0, sent.get());
    }
  }

  @Test
  void writesWithUnconfirmedOutcomeAreNotRetriedAndNeverLeakCause() {
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(
                request -> {
                  sent.incrementAndGet();
                  throw new IllegalStateException(
                      "secret-token", new RuntimeException("secret-cause"));
                })
            .build()) {
      EPayException error =
          assertThrows(EPayException.class, () -> client.createPayment(payment()));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertTrue(error.executionUncertain());
      assertNull(error.getCause());
      assertEquals(0, error.getSuppressed().length);
      assertFalse(error.toString().contains("secret"));
      assertEquals(1, sent.get());
    }
  }

  @Test
  void localValidationDoesNotMarkWriteUncertain() {
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(
                request -> {
                  throw new AssertionError("不应发送");
                })
            .build()) {
      EPayException error = assertThrows(EPayException.class, () -> client.createPayment(null));
      assertEquals(EPayException.Kind.VALIDATION, error.kind());
      assertFalse(error.executionUncertain());
    }
  }

  @Test
  void contextUsesInjectedClockAndRequestTimeout() {
    Clock clock = Clock.fixed(Instant.ofEpochSecond(123456), ZoneOffset.UTC);
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            assertEquals(123456, context.clock().instant().getEpochSecond());
            assertEquals(Duration.ofSeconds(2), context.requestTimeout());
            return super.queryOrder(context, request);
          }
        };
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .clock(clock)
            .httpOptions(
                new HttpOptions(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(2)))
            .transport(request -> new TransportResponse(200, ""))
            .build()) {
      assertFalse(client.queryOrder(OrderReference.byTradeNo("x")).success());
    }
  }

  @Test
  void credentialTypesSelectProtocolWithoutSendingProbeRequests() throws Exception {
    HttpTransport neverSend =
        request -> {
          throw new AssertionError("禁止自动探测");
        };
    try (EPayClient v1 = EPayClient.builder().config(config()).transport(neverSend).build();
        EPayClient v2 = EPayClient.builder().config(rsaConfig(2048)).transport(neverSend).build()) {
      assertEquals(new EpayV1Adapter().id(), v1.protocolId());
      assertEquals(new EpayV2Adapter().id(), v2.protocolId());
    }
  }

  @Test
  void rejectsMissingMixedWeakAndExplicitlyMismatchedCredentials() throws Exception {
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(EPayException.class, () -> EPayClient.builder().build()).kind());
    assertThrows(
        EPayException.class,
        () -> MerchantConfig.builder().baseUrl("https://pay.example").merchantId("x").build());
    MerchantConfig rsa = rsaConfig(2048);
    assertThrows(
        EPayException.class,
        () -> EPayClient.builder().config(rsa).adapter(new TestAdapter()).build());
    assertThrows(
        EPayException.class,
        () ->
            MerchantConfig.builder()
                .baseUrl("https://pay.example")
                .merchantId("x")
                .credentials(new Md5Credentials("secret"))
                .credentials(rsa.credentials())
                .build());
    assertThrows(EPayException.class, () -> rsaConfig(1024));
    assertThrows(EPayException.class, () -> new RsaCredentials("private-sensitive-input", null));
    assertFalse(rsa.toString().contains("BEGIN"));
    assertFalse(rsa.credentials().toString().contains("BEGIN"));
  }

  @Test
  void transferredOwnershipIsClosedExactlyOnce() {
    AtomicInteger closes = new AtomicInteger();
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public TransportResponse execute(TransportRequest request) {
            return new TransportResponse(200, "");
          }

          @Override
          public void close() {
            closes.incrementAndGet();
          }
        };
    EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(transport, true)
            .build();
    client.close();
    client.close();
    assertEquals(1, closes.get());
  }

  @Test
  void invalidAmountsAreRejectedBeforeAnyPaymentIsSent() {
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(
                request -> {
                  throw new AssertionError("不应发送");
                })
            .build()) {
      for (String amount : List.of("0", "-1", "1.001", "1.000")) {
        EPayException error =
            assertThrows(
                EPayException.class,
                () ->
                    client.createPayment(
                        PaymentRequest.builder()
                            .name("private")
                            .outTradeNo("x")
                            .amount(new BigDecimal(amount))
                            .build()));
        assertEquals(EPayException.Kind.VALIDATION, error.kind());
        assertFalse(error.executionUncertain());
      }
    }
  }

  @Test
  void notificationsUseImmutableSnapshotAndRejectRepeatedKeys() {
    Map<String, String> original = new LinkedHashMap<>(Map.of("money", "1.00", "pid", "1001"));
    NotificationRequest snapshot = NotificationRequest.fromParameters(original);
    original.put("money", "999.00");
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<VerifiedNotification> verifyNotification(
              ProtocolContext context, NotificationRequest request) {
            assertEquals("1.00", request.parameters().get("money"));
            assertThrows(
                UnsupportedOperationException.class, () -> request.parameters().put("money", "2"));
            return GatewayResult.success(
                "OK",
                new VerifiedNotification(
                    "1001",
                    "t",
                    "o",
                    new BigDecimal(request.parameters().get("money")),
                    OrderStatus.UNKNOWN,
                    "new-status",
                    "custom",
                    "private-param"));
          }
        };
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .transport(
                request -> {
                  throw new AssertionError("通知不应联网");
                })
            .build()) {
      VerifiedNotification verified = client.verifyNotification(snapshot).data();
      assertEquals("success", verified.successAck());
      assertEquals(OrderStatus.UNKNOWN, verified.status());
      assertFalse(verified.toString().contains("private-param"));
      assertThrows(
          EPayException.class,
          () ->
              client.verifyNotification(
                  NotificationRequest.fromMultiValue(Map.of("pid", List.of("1001", "1001")))));
    }
  }

  @Test
  void protocolParsingAfterSendingMarksWriteUncertainButNotRead() {
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(
                request -> {
                  throw EPayException.protocol();
                })
            .build()) {
      assertTrue(
          assertThrows(EPayException.class, () -> client.createPayment(payment()))
              .executionUncertain());
      assertFalse(
          assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("x")))
              .executionUncertain());
    }
  }

  @Test
  void signingAndJsonHelpersWorkThroughCompleteOperationAdapter() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair platform = generator.generateKeyPair();
    String canonical = "amount=1.00&name=中文 &+=&zero=0";
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(platform.getPrivate());
    signer.update(canonical.getBytes(StandardCharsets.UTF_8));
    String signed = Base64.getEncoder().encodeToString(signer.sign());
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            StrictJson.ObjectValue json =
                StrictJson.parseObject(context.send("GET", "json", Map.of()).body());
            Map<String, String> values = json.scalarParameters();
            if (!Signatures.verifyRsa(values, platform.getPublic(), json.text("sign"))) {
              throw EPayException.signature();
            }
            assertEquals("1.00", json.text("amount"));
            assertEquals(canonical, Signatures.canonical(values));
            return GatewayResult.success(
                "200",
                new OrderResult(
                    "t",
                    "o",
                    "1001",
                    new BigDecimal(json.requireText("amount")),
                    OrderStatus.UNKNOWN,
                    "extra",
                    "custom"));
          }
        };
    String valid =
        "{\"amount\":1.00,\"name\":\"中文 &+=\",\"zero\":0,\"empty\":\"\",\"nil\":null,"
            + "\"sign_type\":\"RSA\",\"sign\":\""
            + signed
            + "\"}";
    List<String> bodies =
        new ArrayList<>(
            List.of(
                valid,
                valid.replace("1.00", "1.01"),
                "{\"a\":1,\"\\u0061\":2}",
                "{\"x\":{\"a\":1,\"a\":2}}",
                "{\"a\":01}",
                "{\"a\":1,}"));
    AtomicInteger index = new AtomicInteger();
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .transport(request -> new TransportResponse(200, bodies.get(index.getAndIncrement())))
            .build()) {
      assertEquals(
          new BigDecimal("1.00"), client.queryOrder(OrderReference.byTradeNo("x")).data().amount());
      assertEquals(
          EPayException.Kind.SIGNATURE,
          assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("x")))
              .kind());
      for (int i = 2; i < bodies.size(); i++) {
        assertEquals(
            EPayException.Kind.PROTOCOL,
            assertThrows(
                    EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("x")))
                .kind());
      }
    }
  }

  @Test
  void md5FixedVectorUsesOriginalValuesAndIncludesZero() {
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            Map<String, String> wire = new LinkedHashMap<>();
            wire.put("name", "中文 &+=");
            wire.put("zero", "0");
            wire.put("empty", "");
            wire.put("nil", null);
            wire.put("sign", "被排除");
            wire.put("sign_type", "MD5");
            String sign = Signatures.md5(wire, (Md5Credentials) context.config().credentials());
            wire.remove("nil");
            wire.put("sign", sign);
            context.send("POST", "pay", wire);
            return GatewayResult.rejected("DENIED", null);
          }
        };
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .transport(
                request -> {
                  // 预期值由独立 Python hashlib 固定生成，而非调用生产签名工具回算。
                  assertEquals(
                      "98bda6e6a18f16ecfddd8ee6314f2de9", request.parameters().get("sign"));
                  assertEquals("中文 &+=", request.parameters().get("name"));
                  assertThrows(
                      UnsupportedOperationException.class, () -> request.parameters().clear());
                  return new TransportResponse(200, "");
                })
            .build()) {
      assertFalse(client.createPayment(payment()).success());
    }
  }

  @Test
  void rsaRequestSignatureCanBeVerifiedByIndependentJcaVerifier() throws Exception {
    MerchantConfig merchant = rsaConfig(2048);
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair keys = generator.generateKeyPair();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            Map<String, String> wire = new LinkedHashMap<>(Map.of("amount", "1.00", "zero", "0"));
            wire.put("sign", Signatures.rsa(wire, keys.getPrivate()));
            context.send("POST", "pay", wire);
            return GatewayResult.rejected("DENIED", null);
          }
        };
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .transport(
                request -> {
                  try {
                    Signature verifier = Signature.getInstance("SHA256withRSA");
                    verifier.initVerify(keys.getPublic());
                    verifier.update("amount=1.00&zero=0".getBytes(StandardCharsets.UTF_8));
                    assertTrue(
                        verifier.verify(
                            Base64.getDecoder().decode(request.parameters().get("sign"))));
                    return new TransportResponse(200, "");
                  } catch (Exception e) {
                    throw new AssertionError("独立验签失败");
                  }
                })
            .build()) {
      assertFalse(client.createPayment(payment()).success());
      assertFalse(merchant.credentials().toString().contains("PRIVATE"));
    }
  }

  @Test
  void deploymentPrefixesArePreservedAndTraversalIsRejectedBeforeSending() {
    MerchantConfig merchant =
        MerchantConfig.builder()
            .baseUrl("https://pay.example/a%20b/中文")
            .merchantId("1001")
            .credentials(new Md5Credentials("secret-token"))
            .build();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            context.send("GET", request.value(), Map.of());
            return GatewayResult.rejected("DENIED", null);
          }
        };
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        EPayClient.builder()
            .config(merchant)
            .adapter(adapter)
            .transport(
                request -> {
                  sent.incrementAndGet();
                  assertEquals(
                      "https://pay.example/a%20b/%E4%B8%AD%E6%96%87/order",
                      request.uri().toASCIIString());
                  return new TransportResponse(200, "");
                })
            .build()) {
      client.queryOrder(OrderReference.byTradeNo("/order"));
      for (String path :
          List.of(
              "../secret",
              "%2e%2e/secret",
              "//foreign.example",
              "https://foreign.example",
              "order?key=secret",
              "a%2fb",
              "a%252fb")) {
        assertEquals(
            EPayException.Kind.CONFIGURATION,
            assertThrows(
                    EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo(path)))
                .kind());
      }
      assertEquals(1, sent.get());
    }
  }

  @Test
  void httpTransportPreservesInterruptionWithoutSending() {
    try (HttpTransport transport = new ApacheHttpTransport()) {
      Thread.currentThread().interrupt();
      try {
        EPayException error =
            assertThrows(
                EPayException.class,
                () ->
                    transport.execute(
                        new TransportRequest(
                            "POST",
                            URI.create("https://never-contact.example"),
                            Map.of(),
                            Duration.ofSeconds(1))));
        assertEquals(EPayException.Kind.TRANSPORT, error.kind());
        assertFalse(error.retryable());
        assertTrue(Thread.currentThread().isInterrupted());
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  void htmlPaymentIsLocalEscapedAndNeverExecuted() {
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            return GatewayResult.success(
                "LOCAL",
                new PaymentResult(
                    null,
                    request.outTradeNo(),
                    List.of(
                        new PaymentAction.Form(
                            FormCodec.buildHtmlForm(
                                context.resolve("submit"), Map.of("name", request.name()))))));
          }
        };
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(adapter)
            .transport(
                request -> {
                  throw new AssertionError("表单不应联网");
                })
            .build()) {
      PaymentResult result =
          client
              .createPayment(
                  PaymentRequest.builder()
                      .outTradeNo("o")
                      .name("\"><script>alert(1)</script>")
                      .amount(BigDecimal.ONE)
                      .scene(PaymentScene.BROWSER_FORM)
                      .build())
              .data();
      String html = ((PaymentAction.Form) result.actions().get(0)).html();
      assertFalse(html.contains("<script>"));
      assertTrue(html.contains("&quot;&gt;&lt;script&gt;"));
      assertThrows(UnsupportedOperationException.class, () -> result.actions().clear());
      assertFalse(result.toString().contains("script"));
    }
  }

  @Test
  void httpTransportEncodesUtf8DisablesCookiesAndDoesNotFollowRedirects() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger targets = new AtomicInteger();
    server.createContext(
        "/form",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Set-Cookie", "session=private; Path=/");
          respond(exchange, 200, body);
        });
    server.createContext(
        "/redirect",
        exchange -> {
          exchange.getResponseHeaders().set("Location", "/target");
          respond(exchange, 302, "");
        });
    server.createContext(
        "/target",
        exchange -> {
          targets.incrementAndGet();
          respond(exchange, 200, "");
        });
    server.createContext(
        "/cookie",
        exchange ->
            respond(
                exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Cookie"))));
    server.start();
    try (HttpTransport transport = new ApacheHttpTransport()) {
      URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      TransportResponse result =
          transport.execute(
              new TransportRequest(
                  "POST", base.resolve("/form"), Map.of("name", "中文 &+="), Duration.ofSeconds(2)));
      assertEquals("name=%E4%B8%AD%E6%96%87+%26%2B%3D", result.body());
      assertEquals(
          302,
          transport
              .execute(
                  new TransportRequest(
                      "GET", base.resolve("/redirect"), Map.of(), Duration.ofSeconds(2)))
              .status());
      assertEquals(0, targets.get());
      assertEquals(
          "null",
          transport
              .execute(
                  new TransportRequest(
                      "GET", base.resolve("/cookie"), Map.of(), Duration.ofSeconds(2)))
              .body());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void httpTransportRejectsOversizedResponseAndRemainsUsable() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/large", exchange -> respond(exchange, 200, "x".repeat(1024 * 1024 + 1)));
    server.createContext("/ok", exchange -> respond(exchange, 200, "ok"));
    server.createContext(
        "/chunked",
        exchange -> {
          try (exchange) {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(new byte[1024 * 1024 + 1]);
          } catch (IOException ignored) {
            /* 客户端达到上限后主动关闭连接。 */
          }
        });
    server.createContext(
        "/invalid",
        exchange -> {
          try (exchange) {
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write(new byte[] {(byte) 0xc3, 0x28});
          }
        });
    server.start();
    try (HttpTransport transport = new ApacheHttpTransport()) {
      URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      EPayException error =
          assertThrows(
              EPayException.class,
              () ->
                  transport.execute(
                      new TransportRequest(
                          "GET", base.resolve("/large"), Map.of(), Duration.ofSeconds(2))));
      assertEquals(EPayException.Kind.PROTOCOL, error.kind());
      assertNull(error.getCause());
      for (String path : List.of("/chunked", "/invalid")) {
        assertEquals(
            EPayException.Kind.PROTOCOL,
            assertThrows(
                    EPayException.class,
                    () ->
                        transport.execute(
                            new TransportRequest(
                                "GET", base.resolve(path), Map.of(), Duration.ofSeconds(2))))
                .kind());
      }
      assertEquals(
          "ok",
          transport
              .execute(
                  new TransportRequest("GET", base.resolve("/ok"), Map.of(), Duration.ofSeconds(2)))
              .body());
      transport.close();
      assertEquals(
          EPayException.Kind.CLOSED,
          assertThrows(
                  EPayException.class,
                  () ->
                      transport.execute(
                          new TransportRequest(
                              "GET", base.resolve("/ok"), Map.of(), Duration.ofSeconds(2))))
              .kind());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void requestDeadlineBoundsDrippingResponseWithoutRetry() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger attempts = new AtomicInteger();
    server.createContext(
        "/slow",
        exchange -> {
          attempts.incrementAndGet();
          try (exchange) {
            exchange.sendResponseHeaders(200, 0);
            for (int i = 0; i < 100; i++) {
              exchange.getResponseBody().write('x');
              exchange.getResponseBody().flush();
              try {
                Thread.sleep(20);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
              }
            }
          } catch (IOException ignored) {
            /* 超时取消后服务端连接关闭属于预期行为。 */
          }
        });
    server.start();
    try (HttpTransport transport = new ApacheHttpTransport()) {
      URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow");
      long start = System.nanoTime();
      EPayException error =
          assertThrows(
              EPayException.class,
              () ->
                  transport.execute(
                      new TransportRequest("POST", uri, Map.of(), Duration.ofMillis(150))));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(2)) < 0);
      assertEquals(1, attempts.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void operationBudgetIncludesPreprocessingAndEveryTransmission() {
    List<TransportRequest> sent = new ArrayList<>();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            pause(Duration.ofMillis(100));
            context.send(
                new TransportRequest(
                    "POST",
                    context.resolve("first"),
                    Map.of("id", request.value()),
                    Duration.ofSeconds(1),
                    Map.of("act", "first")));
            context
                .transport()
                .execute(
                    new TransportRequest(
                        "GET",
                        context.resolve("second"),
                        Map.of("id", request.value()),
                        Duration.ofSeconds(10),
                        Map.of("act", "second")));
            return GatewayResult.rejected("DENIED", null);
          }
        };
    try (EPayClient client =
        budgetClient(
            adapter,
            request -> {
              sent.add(request);
              if (sent.size() == 1) pause(Duration.ofMillis(100));
              return new TransportResponse(200, "");
            },
            Duration.ofSeconds(2))) {
      assertFalse(client.queryOrder(OrderReference.byTradeNo("x")).success());
      assertEquals(2, sent.size());
      assertEquals(Duration.ofSeconds(1), sent.get(0).timeout());
      assertTrue(sent.get(1).timeout().compareTo(Duration.ofMillis(1800)) <= 0);
      assertEquals("GET", sent.get(1).method());
      assertEquals("https://pay.example/prefix/second", sent.get(1).uri().toString());
      assertEquals(Map.of("id", "x"), sent.get(1).parameters());
      assertEquals(Map.of("act", "first"), sent.get(0).queryParameters());
      assertEquals(Map.of("act", "second"), sent.get(1).queryParameters());
    }
  }

  @Test
  void expiredPreprocessingNeverSendsOrMarksWriteUncertain() {
    AtomicInteger sent = new AtomicInteger();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            pause(Duration.ofMillis(50));
            return super.createPayment(context, request);
          }
        };
    try (EPayClient client =
        budgetClient(
            adapter,
            request -> {
              sent.incrementAndGet();
              return new TransportResponse(200, "");
            },
            Duration.ofMillis(20))) {
      EPayException error =
          assertThrows(EPayException.class, () -> client.createPayment(payment()));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertFalse(error.executionUncertain());
      assertEquals(0, sent.get());
    }
  }

  @Test
  void subMillisecondOperationRemainderIsNotRoundedUpForTransport() {
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        budgetClient(
            new TestAdapter(),
            request -> {
              sent.incrementAndGet();
              return new TransportResponse(200, "");
            },
            Duration.ofMillis(1))) {
      EPayException error =
          assertThrows(EPayException.class, () -> client.createPayment(payment()));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertFalse(error.executionUncertain());
      assertEquals(0, sent.get());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void lateCustomTransportResponseIsNotParsedOrReturned(boolean write) {
    AtomicInteger parsed = new AtomicInteger();
    AtomicInteger sent = new AtomicInteger();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            GatewayResult<PaymentResult> result = super.createPayment(context, request);
            parsed.incrementAndGet();
            return result;
          }

          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            GatewayResult<OrderResult> result = super.queryOrder(context, request);
            parsed.incrementAndGet();
            return result;
          }
        };
    Thread caller = Thread.currentThread();
    try (EPayClient client =
        budgetClient(
            adapter,
            request -> {
              assertSame(caller, Thread.currentThread());
              sent.incrementAndGet();
              pause(request.timeout().plusMillis(50));
              return new TransportResponse(200, "");
            },
            Duration.ofMillis(100))) {
      EPayException error =
          assertThrows(
              EPayException.class,
              () -> {
                if (write) client.createPayment(payment());
                else client.queryOrder(OrderReference.byTradeNo("x"));
              });
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertEquals(write, error.executionUncertain());
      assertEquals(1, sent.get());
      assertEquals(0, parsed.get());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"READ", "WRITE", "LOCAL"})
  void expiredParsingAndLocalResultsAreNotReturned(String operation) {
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            if (!operation.equals("LOCAL")) super.createPayment(context, request);
            pause(context.requestTimeout().plusMillis(50));
            return GatewayResult.rejected("DENIED", null);
          }

          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            super.queryOrder(context, request);
            pause(context.requestTimeout().plusMillis(50));
            return GatewayResult.rejected("DENIED", null);
          }
        };
    try (EPayClient client =
        budgetClient(adapter, request -> new TransportResponse(200, ""), Duration.ofMillis(100))) {
      EPayException error =
          assertThrows(
              EPayException.class,
              () -> {
                if (operation.equals("READ")) client.queryOrder(OrderReference.byTradeNo("x"));
                else client.createPayment(payment());
              });
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertEquals(operation.equals("WRITE"), error.executionUncertain());
    }
  }

  @Test
  void expiredBudgetCannotStartASecondWrite() {
    AtomicInteger sent = new AtomicInteger();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            super.createPayment(context, request);
            pause(context.requestTimeout().plusMillis(50));
            return super.createPayment(context, request);
          }
        };
    try (EPayClient client =
        budgetClient(
            adapter,
            request -> {
              sent.incrementAndGet();
              return new TransportResponse(200, "");
            },
            Duration.ofMillis(100))) {
      EPayException error =
          assertThrows(EPayException.class, () -> client.createPayment(payment()));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertTrue(error.executionUncertain());
      assertEquals(1, sent.get());
    }
  }

  @ParameterizedTest
  @CsvSource({"SIGNATURE,false", "VALIDATION,false", "SIGNATURE,true", "VALIDATION,true"})
  void deadlineDoesNotMaskSignatureOrParameterFailures(EPayException.Kind kind, boolean send) {
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            if (send) super.createPayment(context, request);
            pause(context.requestTimeout().plusMillis(50));
            throw new EPayException(kind);
          }
        };
    try (EPayClient client =
        budgetClient(adapter, request -> new TransportResponse(200, ""), Duration.ofMillis(100))) {
      EPayException error =
          assertThrows(EPayException.class, () -> client.createPayment(payment()));
      assertEquals(kind, error.kind());
      assertEquals(send, error.executionUncertain());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"BEFORE", "PREPROCESSING", "TRANSPORT", "PARSING"})
  void clientPreservesInterruptionAndNeverAcceptsInterruptedResults(String stage) {
    AtomicInteger sent = new AtomicInteger();
    TestAdapter adapter =
        new TestAdapter() {
          @Override
          public GatewayResult<PaymentResult> createPayment(
              ProtocolContext context, PaymentRequest request) {
            if (stage.equals("PREPROCESSING")) Thread.currentThread().interrupt();
            GatewayResult<PaymentResult> result = super.createPayment(context, request);
            if (stage.equals("PARSING")) Thread.currentThread().interrupt();
            return result;
          }
        };
    try (EPayClient client =
        budgetClient(
            adapter,
            request -> {
              sent.incrementAndGet();
              if (stage.equals("TRANSPORT")) Thread.currentThread().interrupt();
              return new TransportResponse(200, "");
            },
            Duration.ofSeconds(2))) {
      if (stage.equals("BEFORE")) Thread.currentThread().interrupt();
      try {
        EPayException error =
            assertThrows(EPayException.class, () -> client.createPayment(payment()));
        assertEquals(EPayException.Kind.TRANSPORT, error.kind());
        assertFalse(error.retryable());
        assertTrue(Thread.currentThread().isInterrupted());
        boolean attempted = stage.equals("TRANSPORT") || stage.equals("PARSING");
        assertEquals(attempted, error.executionUncertain());
        assertEquals(attempted ? 1 : 0, sent.get());
      } finally {
        Thread.interrupted();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void waitingForClientLifecycleLockIsBoundedAndInterruptible(boolean interrupt) throws Exception {
    CountDownLatch closing = new CountDownLatch(1);
    CountDownLatch releaseClose = new CountDownLatch(1);
    CountDownLatch finished = new CountDownLatch(1);
    AtomicBoolean interrupted = new AtomicBoolean();
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public TransportResponse execute(TransportRequest request) {
            throw new AssertionError("等待关闭时不应发送请求");
          }

          @Override
          public void close() {
            closing.countDown();
            try {
              assertTrue(releaseClose.await(10, TimeUnit.SECONDS));
            } catch (InterruptedException error) {
              Thread.currentThread().interrupt();
              throw new AssertionError(error);
            }
          }
        };
    EPayClient client =
        EPayClient.builder()
            .config(config())
            .adapter(new TestAdapter())
            .transport(transport, true)
            .httpOptions(
                new HttpOptions(
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1),
                    interrupt ? Duration.ofSeconds(10) : Duration.ofMillis(100)))
            .build();
    FutureTask<Void> closeTask =
        new FutureTask<>(
            () -> {
              client.close();
              return null;
            });
    Thread closer = new Thread(closeTask);
    FutureTask<EPayException> queryTask =
        new FutureTask<>(
            () -> {
              try {
                return assertThrows(EPayException.class, () -> client.createPayment(payment()));
              } finally {
                interrupted.set(Thread.currentThread().isInterrupted());
                finished.countDown();
              }
            });
    Thread caller = new Thread(queryTask);
    closer.start();
    try {
      assertTrue(closing.await(2, TimeUnit.SECONDS));
      caller.start();
      if (interrupt) {
        long until = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (caller.getState() != Thread.State.WAITING
            && caller.getState() != Thread.State.TIMED_WAITING
            && System.nanoTime() - until < 0) {
          Thread.sleep(1);
        }
        caller.interrupt();
      }
      assertTrue(finished.await(2, TimeUnit.SECONDS), "拿锁等待必须在关闭释放之前超时或响应中断");
      EPayException error = queryTask.get(1, TimeUnit.SECONDS);
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertFalse(error.executionUncertain());
      assertEquals(interrupt, interrupted.get());
      if (interrupt) assertFalse(error.retryable());
    } finally {
      releaseClose.countDown();
      closer.join(3000);
      caller.join(3000);
      closeTask.get(1, TimeUnit.SECONDS);
      client.close();
    }
  }

  private static EPayClient budgetClient(
      ProtocolAdapter adapter, HttpTransport transport, Duration timeout) {
    return EPayClient.builder()
        .config(config())
        .adapter(adapter)
        .transport(transport)
        .clock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
        .httpOptions(
            new HttpOptions(
                Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), timeout))
        .build();
  }

  private static void pause(Duration duration) {
    try {
      TimeUnit.NANOSECONDS.sleep(duration.toNanos());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
  }

  private static MerchantConfig rsaConfig(int bits) throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    KeyPair merchant = generator.generateKeyPair();
    KeyPair platform = generator.generateKeyPair();
    String privatePem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(merchant.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";
    String publicPem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(platform.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
    return MerchantConfig.builder()
        .baseUrl("https://pay.example")
        .merchantId("1001")
        .credentials(new RsaCredentials(privatePem, publicPem))
        .build();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    try (exchange) {
      exchange.getRequestBody().readAllBytes();
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length != 0) exchange.getResponseBody().write(bytes);
    }
  }

  private static MerchantConfig config() {
    return MerchantConfig.builder()
        .baseUrl("https://pay.example/prefix")
        .merchantId("1001")
        .credentials(new Md5Credentials("secret-token"))
        .build();
  }

  private static PaymentRequest payment() {
    return PaymentRequest.builder()
        .outTradeNo("private-order")
        .name("私人名称")
        .amount(new BigDecimal("1.00"))
        .paymentMethod("custom-channel")
        .build();
  }

  /** 测试专用完整操作适配器，用于观察门面的能力、生命周期和错误边界。 */
  private static class TestAdapter implements ProtocolAdapter {
    @Override
    public String id() {
      return "test";
    }

    @Override
    public Set<Capability> capabilities() {
      return EnumSet.allOf(Capability.class);
    }

    @Override
    public void validateCredentials(Credentials credentials) {
      if (!(credentials instanceof Md5Credentials)) throw EPayException.configuration();
    }

    @Override
    public GatewayResult<PaymentResult> createPayment(
        ProtocolContext context, PaymentRequest request) {
      context.send("POST", "create", Map.of("amount", request.amount().toPlainString()));
      return GatewayResult.rejected("DENIED", "原始消息");
    }

    @Override
    public GatewayResult<OrderResult> queryOrder(ProtocolContext context, OrderReference request) {
      context.send("GET", "/order", Map.of("id", request.value()));
      return GatewayResult.rejected("DENIED", "原始消息");
    }

    @Override
    public GatewayResult<VerifiedNotification> verifyNotification(
        ProtocolContext context, NotificationRequest request) {
      return GatewayResult.rejected("DENIED", "原始消息");
    }
  }
}
