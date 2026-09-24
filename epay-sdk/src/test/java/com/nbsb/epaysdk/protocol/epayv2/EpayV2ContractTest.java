package com.nbsb.epaysdk.protocol.epayv2;

import static org.junit.jupiter.api.Assertions.*;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.protocol.epayv1.EpayV1Adapter;
import com.nbsb.epaysdk.protocol.mzf.MzfLegacyAdapter;
import com.nbsb.epaysdk.spi.HttpTransport;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import com.nbsb.epaysdk.spi.TransportRequest;
import com.nbsb.epaysdk.spi.TransportResponse;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 通过 EPayClient / HttpTransport seam 验证 RSA 协议，不借用生产签名实现生成期望。
 *
 * <p>取证日期：2026-09-24。以下全部是文档派生合成样本，非真实联调、非线上抓包， 不证明任意运营方部署兼容。域名、订单、身份、密钥均为公开测试材料，绝非商户凭据。
 *
 * <p>逐操作来源 A：https://mazhifupay.com/28/6/568.html （运营方文档，非原厂取证） -
 * submit：章节“页面跳转支付”请求表/其他说明；本地表单样本，不模拟真实收银台。 - create：章节“统一下单接口”请求/返回表、接口类型列表、发起支付类型说明； 含
 * web/jump/jsapi/app/scan/applet 及 wxplugin/wxapp 样本，is_applet=0 为边界合成值。 -
 * query：章节“订单查询”请求/返回表及“支付状态列表”；0 未支付、1 已支付。 -
 * notify：章节“支付结果通知”请求表、返回内容说明/其他说明；TRADE_SUCCESS、success。 - refund：章节“订单退款”请求/返回表；提交仅接受，不冒充退款完成。 -
 * refundquery：章节“订单退款查询”请求/返回表；0 失败、1 成功。 - 全部签名：章节“接口说明及规范”“签名步骤”“验签步骤”。未知字段、空值、
 * 畸形值、状态篡改及并发交错是本地安全边界样本，不是文档成功响应原件。
 *
 * <p>逐操作来源 B：https://docs.xarr.cn/merchant/api/epayn （XArr 官方文档） -
 * submit/create/query/notify：章节“一、页面跳转支付”“二、统一下单” “三、订单查询”“四、异步回调”；query 的 1 待支付、2 已支付不同于 A。 -
 * 基址/双角色签名：章节“网关地址”“密钥与签名 / 待签名串构造”。 - refund/refundquery：章节“五、暂不支持的接口”，只做不发送断言，无成功样本。 -
 * queryMerchant/listOrders 的 UNSUPPORTED 是当前 SDK 能力边界，不声明运营方无此接口。
 *
 * <p>固定向量位于 /epayv2/public-rsa/fixtures.json，由同目录 generate-fixtures.py 使用 OpenSSL 3.6.2
 * 离线生成：genpkey RSA 2048 / pkey -pubout / dgst -sha256 -sigopt rsa_padding_mode:pkcs1。原文 UTF-8、无
 * BOM/末尾换行、不 URL 编码；签名为 Base64。 fixture 中含故意公开的测试私钥，禁止用于生产。Java 只消费硬编码向量，不动态生成预期。
 */
class EpayV2ContractTest {
  private static final long NOW = 1790208000L;
  private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
  private static final String BASE = "https://pay.example/xpay/epayn/";
  private static KeyPair merchant;
  private static KeyPair platform;

  @BeforeAll
  static void keys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    merchant = generator.generateKeyPair();
    platform = generator.generateKeyPair();
  }

  @Test
  void rsaConfigurationAutomaticallySelectsV2WithoutProbeAndSignsExactQuery() {
    AtomicInteger calls = new AtomicInteger();
    try (EPayClient client =
        client(
            null,
            request -> {
              calls.incrementAndGet();
              assertWire(
                  request,
                  "query",
                  Map.of(
                      "out_trade_no",
                      "订单 &+=",
                      "pid",
                      "1001",
                      "timestamp",
                      Long.toString(NOW),
                      "sign_type",
                      "RSA"));
              // 固定待签名串独立验证，含中文和保留字符，不做 URL 编码。
              assertTrue(
                  verify(
                      "out_trade_no=订单 &+=&pid=1001&timestamp=1790208000",
                      request.parameters().get("sign"),
                      merchant.getPublic()));
              assertFalse(
                  verify(
                      canonical(request.parameters()),
                      request.parameters().get("sign"),
                      platform.getPublic()));
              Map<String, Object> response = order();
              response.put("out_trade_no", "订单 &+=");
              return response(response);
            })) {
      assertEquals(0, calls.get());
      assertEquals("epay-v2", client.protocolId());
      GatewayResult<OrderResult> result = client.queryOrder(OrderReference.byOutTradeNo("订单 &+="));
      assertTrue(result.success());
      assertEquals("0", result.code());
      assertEquals(new BigDecimal("1.00"), result.data().amount());
      assertEquals(OrderStatus.PAID, result.data().status());
      assertEquals(1, calls.get());
    }
  }

  @Test
  void explicitV2RejectsMd5ConfigurationBeforeTransport() {
    MerchantConfig config =
        MerchantConfig.builder()
            .baseUrl(BASE)
            .merchantId("1001")
            .credentials(new Md5Credentials("not-rsa"))
            .build();
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(
                EPayException.class,
                () ->
                    EPayClient.builder()
                        .config(config)
                        .adapter(new EpayV2Adapter())
                        .transport(neverSend())
                        .build())
            .kind());
  }

  @Test
  void zeroCodeMayBeNumberOrStringAndUnknownScalarLexemesAreSignedUnchanged() {
    for (Object code : List.of(0, "0")) {
      Map<String, Object> data = order();
      data.put("code", code);
      data.put("money", new BigDecimal("1.00"));
      data.put("new_number", new BigDecimal("1.2300E+8"));
      data.put("new_zero", 0);
      data.put("new_boolean", true);
      data.put("empty", "");
      data.put("nil", null);
      try (EPayClient client = client(null, request -> response(data))) {
        assertTrue(client.queryOrder(OrderReference.byTradeNo("T1")).success());
      }
    }
  }

  @Test
  void dialectsHaveDifferentStatusTablesAndNeverGuessUnknownStates() {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      for (String raw : List.of("0", "1", "2", "3", "4", "999", "1.00")) {
        Map<String, Object> data = order();
        data.put("status", raw);
        try (EPayClient client = client(new EpayV2Adapter(dialect), request -> response(data))) {
          assertEquals(
              dialect == EpayV2Adapter.Dialect.REFERENCE ? "epay-v2" : "epay-v2-xarr",
              client.protocolId());
          OrderResult result = client.queryOrder(OrderReference.byTradeNo("T1")).data();
          OrderStatus expected =
              dialect == EpayV2Adapter.Dialect.REFERENCE
                  ? (raw.equals("0")
                      ? OrderStatus.PENDING
                      : raw.equals("1") ? OrderStatus.PAID : OrderStatus.UNKNOWN)
                  : (raw.equals("1")
                      ? OrderStatus.PENDING
                      : raw.equals("2") ? OrderStatus.PAID : OrderStatus.UNKNOWN);
          assertEquals(expected, result.status());
          assertEquals(raw, result.rawStatus());
        }
      }
    }
  }

  @Test
  void onlySignedBusinessRejectionsAreReturnedAsGatewayRejections() {
    Map<String, Object> data = base();
    data.put("code", "DENIED");
    data.put("msg", "商户未开通");
    try (EPayClient client = client(null, request -> response(data))) {
      GatewayResult<OrderResult> result = client.queryOrder(OrderReference.byTradeNo("T1"));
      assertFalse(result.success());
      assertEquals("DENIED", result.code());
      assertEquals("商户未开通", result.message());
      assertNull(result.data());
    }
    assertQueryError(json(data), EPayException.Kind.SIGNATURE);
  }

  @Test
  void rejectsMissingBadAndWrongRoleSignaturesAndTamperedAmount() {
    assertQueryError(json(order()), EPayException.Kind.SIGNATURE);
    Map<String, Object> bad = signed(order(), platform.getPrivate());
    bad.put("sign", "not-base64");
    assertQueryError(json(bad), EPayException.Kind.SIGNATURE);
    assertQueryError(json(signed(order(), merchant.getPrivate())), EPayException.Kind.SIGNATURE);
    Map<String, Object> changed = signed(order(), platform.getPrivate());
    changed.put("money", "999.00");
    assertQueryError(json(changed), EPayException.Kind.SIGNATURE);
    for (String type : List.of("MD5", "rsa", "")) {
      Map<String, Object> wrongType = order();
      wrongType.put("sign_type", type);
      assertQueryError(
          json(signed(wrongType, platform.getPrivate())), EPayException.Kind.SIGNATURE);
    }
    Map<String, Object> missingType = order();
    missingType.remove("sign_type");
    assertQueryError(
        json(signed(missingType, platform.getPrivate())), EPayException.Kind.SIGNATURE);
  }

  @Test
  void duplicateJsonKeysAreRejectedEvenWhenEscapedOrNested() {
    String valid = json(signed(order(), platform.getPrivate()));
    assertQueryError("{\"code\":0," + valid.substring(1), EPayException.Kind.PROTOCOL);
    assertQueryError("{\"\\u0063ode\":0," + valid.substring(1), EPayException.Kind.PROTOCOL);
    assertQueryError(
        "{\"unused\":{\"x\":1,\"x\":2}," + valid.substring(1), EPayException.Kind.PROTOCOL);
  }

  @Test
  void excludesUnknownNestedFieldsButNeverTrustsNestedBusinessValues() {
    Map<String, Object> data = order();
    data.put("unused", Map.of("money", "999.00", "status", 1));
    data.put("array", List.of("not signed"));
    try (EPayClient client = client(null, request -> response(data))) {
      assertEquals(
          new BigDecimal("1.00"),
          client.queryOrder(OrderReference.byTradeNo("T1")).data().amount());
    }
    for (String key : List.of("money", "status", "trade_no", "code", "timestamp")) {
      Map<String, Object> nested = order();
      nested.put(key, Map.of("value", "1"));
      assertQueryError(json(signed(nested, platform.getPrivate())), EPayException.Kind.PROTOCOL);
    }
  }

  @Test
  void responseTimestampMustBeReasonableSecondsAndWithinConfiguredTolerance() {
    for (Object time :
        List.of(
            Long.toString(NOW - 301),
            Long.toString(NOW + 301),
            "1790208000000",
            "1790208000000000",
            "1790208000.0",
            "1.790208E9",
            "-1790208000",
            "")) {
      Map<String, Object> data = order();
      data.put("timestamp", time);
      assertQueryError(json(signed(data, platform.getPrivate())), EPayException.Kind.PROTOCOL);
    }
    Map<String, Object> data = order();
    data.put("timestamp", Long.toString(NOW - 600));
    try (EPayClient client =
        client(
            new EpayV2Adapter(EpayV2Adapter.Dialect.REFERENCE, Duration.ofSeconds(600)),
            request -> response(data))) {
      assertTrue(client.queryOrder(OrderReference.byTradeNo("T1")).success());
    }
    for (long offset : List.of(-300L, 300L)) {
      data.put("timestamp", Long.toString(NOW + offset));
      try (EPayClient client = client(null, request -> response(data))) {
        assertTrue(client.queryOrder(OrderReference.byTradeNo("T1")).success());
      }
    }
  }

  @Test
  void queryRejectsSignedMerchantOrOrderMismatchAndInvalidAmount() {
    for (Map.Entry<String, Object> invalid :
        Map.<String, Object>of(
                "pid", "other", "trade_no", "wrong", "money", "-1.00", "out_trade_no", "")
            .entrySet()) {
      Map<String, Object> data = order();
      data.put(invalid.getKey(), invalid.getValue());
      assertQueryError(json(signed(data, platform.getPrivate())), EPayException.Kind.PROTOCOL);
    }
  }

  @Test
  void createsPaymentWithEveryWireFieldSignedAndNoAuthenticationOverrides() {
    try (EPayClient client =
        client(
            null,
            request -> {
              Map<String, String> expected = paymentWire();
              expected.put("param", "pid=evil&money=999&sign_type=MD5");
              assertWire(request, "create", expected);
              return response(paymentResponse("jump", "checkout?id=T1"));
            })) {
      PaymentResult result =
          client.createPayment(payment().param("pid=evil&money=999&sign_type=MD5").build()).data();
      assertEquals("T1", result.tradeNo());
      assertEquals("O1", result.outTradeNo());
      assertEquals(
          BASE + "checkout?id=T1",
          ((PaymentAction.Redirect) result.actions().get(0)).url().toString());
      assertThrows(UnsupportedOperationException.class, () -> result.actions().clear());
    }
  }

  @Test
  void browserFormIsLocalEscapedSignedAndPreservesPrefixWithoutRequiringType() {
    try (EPayClient client = client(null, neverSend())) {
      GatewayResult<PaymentResult> created =
          client.createPayment(
              payment()
                  .scene(PaymentScene.BROWSER_FORM)
                  .paymentMethod(null)
                  .clientIp(null)
                  .device(null)
                  .name("商品 &\"安全")
                  .build());
      assertTrue(created.success());
      assertNull(created.code());
      PaymentResult result = created.data();
      String html = ((PaymentAction.Form) result.actions().get(0)).html();
      assertTrue(html.contains("action=\"" + BASE + "api/pay/submit\""));
      assertTrue(html.contains("method=\"post\""));
      assertTrue(html.contains("商品 &amp;&quot;安全"));
      Map<String, String> fields = new LinkedHashMap<>();
      java.util.regex.Matcher matcher =
          java.util.regex.Pattern.compile("name=\"([^\"]+)\" value=\"([^\"]*)\"").matcher(html);
      while (matcher.find())
        fields.put(
            matcher.group(1),
            matcher
                .group(2)
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&"));
      assertEquals("商品 &\"安全", fields.get("name"));
      assertTrue(verify(canonical(fields), fields.get("sign"), merchant.getPublic()));
      assertFalse(fields.containsKey("method"));
      assertFalse(fields.containsKey("type"));
      assertNull(result.tradeNo());
    }
  }

  @Test
  void validatesDocumentedSceneConstraintsBeforeAnyNetworkCall() {
    List<PaymentRequest> invalid =
        List.of(
            payment().notifyUrl(null).build(),
            payment().returnUrl(null).build(),
            payment().clientIp(null).build(),
            payment().paymentMethod(null).build(),
            payment().name("中".repeat(43)).build(),
            payment().device("unknown").build(),
            payment().options(PaymentOptions.builder().method("unknown").build()).build(),
            payment().options(PaymentOptions.builder().method("scan").build()).device(null).build(),
            payment()
                .options(PaymentOptions.builder().method("jsapi").build())
                .device(null)
                .build(),
            payment().options(PaymentOptions.builder().authCode("123").build()).build(),
            payment().options(PaymentOptions.builder().method("app").build()).build(),
            payment().scene(PaymentScene.BROWSER_FORM).returnUrl(null).build());
    try (EPayClient client = client(null, neverSend())) {
      for (PaymentRequest request : invalid) {
        EPayException error =
            assertThrows(EPayException.class, () -> client.createPayment(request));
        assertEquals(EPayException.Kind.VALIDATION, error.kind());
        assertFalse(error.executionUncertain());
      }
    }
  }

  @Test
  void xarrReturnUrlAndClientIpAreOptionalButApiTypeIsRequired() {
    EpayV2Adapter adapter = new EpayV2Adapter(EpayV2Adapter.Dialect.XARR);
    try (EPayClient client =
        client(
            adapter,
            request -> {
              assertFalse(request.parameters().containsKey("return_url"));
              assertFalse(request.parameters().containsKey("clientip"));
              return response(paymentResponse("qrcode", "weixin://wxpay/bizpayurl?pr=abc"));
            })) {
      assertTrue(client.createPayment(payment().returnUrl(null).clientIp(null).build()).success());
      assertTrue(
          client
                  .createPayment(
                      payment()
                          .scene(PaymentScene.BROWSER_FORM)
                          .paymentMethod(null)
                          .returnUrl(null)
                          .clientIp(null)
                          .build())
                  .data()
                  .actions()
                  .get(0)
              instanceof PaymentAction.Form);
    }
    try (EPayClient client = client(adapter, neverSend())) {
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(
                  EPayException.class,
                  () ->
                      client.createPayment(
                          payment()
                              .paymentMethod(null)
                              .device(null)
                              .options(
                                  PaymentOptions.builder()
                                      .method("scan")
                                      .authCode("123456")
                                      .build())
                              .build()))
              .kind());
    }
  }

  @Test
  void allSixCreateMethodsUseDocumentedConditionalParameters() {
    for (String method : List.of("web", "jump", "jsapi", "app", "scan", "applet")) {
      PaymentOptions.Builder options = PaymentOptions.builder().method(method);
      if (method.equals("scan")) options.authCode("123456");
      if (method.equals("jsapi")) options.subOpenId("openid").subAppId("wx-app");
      PaymentRequest request =
          payment()
              .name("中".repeat(42) + "a")
              .device(method.equals("web") ? "mobile" : null)
              .options(options.build())
              .paymentMethod(method.equals("scan") ? null : "wxpay")
              .build();
      try (EPayClient client =
          client(
              null,
              wire -> {
                Map<String, String> expected = paymentWire();
                expected.put("method", method);
                expected.put("name", "中".repeat(42) + "a");
                if (method.equals("web")) expected.put("device", "mobile");
                else expected.remove("device");
                if (method.equals("scan")) {
                  expected.put("auth_code", "123456");
                  expected.remove("type");
                }
                if (method.equals("jsapi")) {
                  expected.put("sub_openid", "openid");
                  expected.put("sub_appid", "wx-app");
                }
                assertWire(wire, "create", expected);
                return response(
                    paymentResponse(
                        method.equals("scan") ? "scan" : "jump",
                        method.equals("scan")
                            ? "{\"money\":\"1.00\",\"trade_no\":\"T1\"}"
                            : "https://pay.example/pay"));
              })) {
        PaymentResult result = client.createPayment(request).data();
        assertEquals(1, result.actions().size());
      }
    }
  }

  @Test
  void jsapiAppletFlagSignsOneOrZeroAndNullIsOmittedWithOldConstructorCompatibility() {
    assertNull(PaymentOptions.defaults().isApplet());
    assertEquals(PaymentOptions.defaults(), new PaymentOptions(null, null, null, null, null));
    for (Boolean isApplet : new Boolean[] {true, false, null}) {
      PaymentOptions options =
          PaymentOptions.builder()
              .method("jsapi")
              .subAppId("wx-app")
              .subOpenId("openid")
              .isApplet(isApplet)
              .build();
      assertEquals(new PaymentOptions("jsapi", null, null, "wx-app", "openid", isApplet), options);
      try (EPayClient client =
          client(
              null,
              wire -> {
                Map<String, String> expected = paymentWire();
                expected.remove("device");
                expected.put("method", "jsapi");
                expected.put("sub_appid", "wx-app");
                expected.put("sub_openid", "openid");
                if (isApplet != null) expected.put("is_applet", isApplet ? "1" : "0");
                assertWire(wire, "create", expected);
                // 按参考 V2 请求字段表独立列出待签名串，确保 0 不被当作空值排除。
                String text =
                    "clientip=192.0.2.1"
                        + (isApplet == null ? "" : "&is_applet=" + (isApplet ? "1" : "0"))
                        + "&method=jsapi&money=1.00&name=商品&notify_url=https://shop.example/notify"
                        + "&out_trade_no=O1&pid=1001&return_url=https://shop.example/return"
                        + "&sub_appid=wx-app&sub_openid=openid&timestamp=1790208000&type=wxpay";
                assertTrue(verify(text, wire.parameters().get("sign"), merchant.getPublic()));
                if (isApplet != null) {
                  Map<String, String> changed = new LinkedHashMap<>(wire.parameters());
                  changed.remove("is_applet");
                  assertFalse(
                      verify(
                          canonical(changed), wire.parameters().get("sign"), merchant.getPublic()));
                }
                return response(paymentResponse("jsapi", "{\"appId\":\"wx-app\"}"));
              })) {
        assertTrue(client.createPayment(payment().device(null).options(options).build()).success());
      }
    }
  }

  @Test
  void appletFlagIsRejectedOutsideApiJsapiBeforeSending() {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      try (EPayClient client = client(new EpayV2Adapter(dialect), neverSend())) {
        for (PaymentScene scene : PaymentScene.values()) {
          for (String method : List.of("web", "jump", "jsapi", "app", "scan", "applet")) {
            if (scene == PaymentScene.API && method.equals("jsapi")) continue;
            for (boolean isApplet : List.of(true, false)) {
              PaymentOptions options =
                  PaymentOptions.builder()
                      .method(method)
                      .isApplet(isApplet)
                      .authCode(method.equals("scan") ? "123456" : null)
                      .subAppId(method.equals("jsapi") ? "wx-app" : null)
                      .subOpenId(method.equals("jsapi") ? "openid" : null)
                      .build();
              PaymentRequest request =
                  payment()
                      .scene(scene)
                      .device(null)
                      .clientIp(scene == PaymentScene.API ? "192.0.2.1" : null)
                      .options(options)
                      .build();
              EPayException error =
                  assertThrows(EPayException.class, () -> client.createPayment(request));
              assertEquals(EPayException.Kind.VALIDATION, error.kind());
              assertFalse(error.executionUncertain());
            }
          }
        }
      }
    }
  }

  @Test
  void legacyProtocolsRejectBothAppletFlagValuesInsteadOfSilentlyDroppingThem() {
    MerchantConfig config =
        MerchantConfig.builder()
            .baseUrl(BASE)
            .merchantId("1001")
            .credentials(new Md5Credentials("legacy-test-secret"))
            .build();
    for (ProtocolAdapter adapter : List.of(new EpayV1Adapter(), new MzfLegacyAdapter())) {
      try (EPayClient client =
          EPayClient.builder().config(config).adapter(adapter).transport(neverSend()).build()) {
        for (boolean isApplet : List.of(true, false)) {
          PaymentRequest request =
              payment()
                  .device(null)
                  .clientIp(null)
                  .options(PaymentOptions.builder().isApplet(isApplet).build())
                  .build();
          EPayException error =
              assertThrows(EPayException.class, () -> client.createPayment(request));
          assertEquals(EPayException.Kind.UNSUPPORTED, error.kind());
          assertFalse(error.executionUncertain());
        }
      }
    }
  }

  @Test
  void mapsOnlyRecognizedSignedPaymentActionsAndKeepsJsonStringsWhole() {
    Map<String, Class<?>> types =
        Map.of(
            "jump",
            PaymentAction.Redirect.class,
            "qrcode",
            PaymentAction.QrCode.class,
            "urlscheme",
            PaymentAction.UrlScheme.class,
            "html",
            PaymentAction.Html.class,
            "jsapi",
            PaymentAction.JsApi.class,
            "app",
            PaymentAction.App.class);
    for (Map.Entry<String, Class<?>> entry : types.entrySet()) {
      String info =
          switch (entry.getKey()) {
            case "jump" -> "/cashier?order=T1";
            case "qrcode", "urlscheme" -> "weixin://wxpay/bizpayurl?pr=abc";
            case "html" -> "<form>已签名但不执行</form>";
            default -> "{\"appId\":\"wx1\",\"amount\":1.00}";
          };
      try (EPayClient client =
          client(null, request -> response(paymentResponse(entry.getKey(), info)))) {
        PaymentAction action = client.createPayment(payment().build()).data().actions().get(0);
        assertEquals(entry.getValue(), action.getClass());
        if (action instanceof PaymentAction.Redirect redirect)
          assertEquals(BASE + "cashier?order=T1", redirect.url().toString());
        if (action instanceof PaymentAction.JsApi jsApi) assertEquals(info, jsApi.payload());
        if (action instanceof PaymentAction.App app) assertEquals(info, app.payload());
      }
    }
  }

  @Test
  void qrCodeIsOpaqueSignedContentAndIsNeverParsedAsOrRebasedToAUri() {
    // 参考 V2 的 qrcode 表示二维码内容，不是图片地址，也不由 SDK 执行。
    for (String payload :
        List.of(
            "普通二维码内容 空格 &+=",
            " \n二维码原文\n ",
            "not a URI % [ ]",
            "/cashier?order=T1",
            "weixin://wxpay/bizpayurl?pr=abc",
            "javascript:alert(1)",
            "data:text/plain,qr")) {
      try (EPayClient client = client(null, wire -> response(paymentResponse("qrcode", payload)))) {
        PaymentAction action = client.createPayment(payment().build()).data().actions().get(0);
        assertEquals(payload, assertInstanceOf(PaymentAction.QrCode.class, action).content());
      }
    }
  }

  @Test
  void executableUrlActionsStillRejectDangerousSchemesAndPreserveRelativeUrlPrefix() {
    for (String type : List.of("jump", "urlscheme")) {
      for (String payload :
          List.of(
              "javascript:alert(1)",
              "JaVaScRiPt:alert(1)",
              "data:text/html,unsafe",
              "vbscript:msgbox(1)",
              "file:///private/secret",
              "//evil.example/pay",
              "../pay",
              "%2e%2e/pay")) {
        try (EPayClient client = client(null, wire -> response(paymentResponse(type, payload)))) {
          assertEquals(
              EPayException.Kind.PROTOCOL,
              assertThrows(EPayException.class, () -> client.createPayment(payment().build()))
                  .kind());
        }
      }
      for (String path : List.of("cashier?token=a%2Bb&n=1", "/cashier?token=a%2Bb&n=1")) {
        try (EPayClient client = client(null, wire -> response(paymentResponse(type, path)))) {
          PaymentAction action = client.createPayment(payment().build()).data().actions().get(0);
          String url =
              type.equals("jump")
                  ? assertInstanceOf(PaymentAction.Redirect.class, action).url().toString()
                  : assertInstanceOf(PaymentAction.UrlScheme.class, action).url();
          assertEquals(BASE + "cashier?token=a%2Bb&n=1", url);
        }
      }
    }
  }

  @Test
  void rejectsUnsignedObjectsUnknownActionsAndPrefixEscapesWithoutExecutingThem() {
    List<Map<String, Object>> invalid = new ArrayList<>();
    invalid.add(paymentResponse("mystery", "<script>禁止执行</script>"));
    invalid.add(paymentResponse("jsapi", Map.of("appId", "unsigned")));
    invalid.add(paymentResponse("app", Map.of("amount", "999")));
    invalid.add(paymentResponse("qrcode", List.of("unsigned")));
    invalid.add(paymentResponse("jump", "//evil.example/checkout"));
    invalid.add(paymentResponse("jump", "../checkout"));
    invalid.add(paymentResponse("jump", "javascript:alert(1)"));
    invalid.add(paymentResponse("jump", "%2e%2e/checkout"));
    Map<String, Object> missing = base();
    missing.put("trade_no", "T1");
    invalid.add(missing);
    for (Map<String, Object> data : invalid) {
      try (EPayClient client = client(null, request -> response(data))) {
        EPayException error =
            assertThrows(EPayException.class, () -> client.createPayment(payment().build()));
        assertEquals(EPayException.Kind.PROTOCOL, error.kind());
        assertTrue(error.executionUncertain());
      }
    }
  }

  @Test
  void notificationVerifiesAllNewFieldsPreservesParamAndAcceptsDelayedRetries() {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      for (long offset : List.of(0L, -301L, -86400L, -604800L, 300L)) {
        Map<String, Object> values = notification();
        values.put("timestamp", Long.toString(NOW + offset));
        values.put("future_field", "新字段 &+=");
        NotificationRequest request = notificationRequest(signed(values, platform.getPrivate()));
        try (EPayClient client = client(new EpayV2Adapter(dialect), neverSend())) {
          GatewayResult<VerifiedNotification> verified = client.verifyNotification(request);
          assertNull(verified.code());
          VerifiedNotification result = verified.data();
          assertEquals("1001", result.merchantId());
          assertEquals("T1", result.tradeNo());
          assertEquals("O1", result.outTradeNo());
          assertEquals(new BigDecimal("1.00"), result.amount());
          assertEquals("原样参数 &+=", result.param());
          assertEquals(OrderStatus.PAID, result.status());
          assertEquals("TRADE_SUCCESS", result.rawStatus());
          assertEquals("success", result.successAck());
          // 重复回调仍会通过验签，业务方必须在事务内按订单幂等处理。
          assertEquals(result.outTradeNo(), client.verifyNotification(request).data().outTradeNo());
        }
      }
    }
  }

  @Test
  void notificationFutureOrMalformedTimestampIsRejectedButUnknownTradeStatusIsNotPaid() {
    for (String timestamp :
        List.of(Long.toString(NOW + 301), "1790208000000000", "1790208000000", "1.790208E9", "")) {
      Map<String, Object> data = notification();
      data.put("timestamp", timestamp);
      assertNotificationError(signed(data, platform.getPrivate()), EPayException.Kind.PROTOCOL);
    }
    Map<String, Object> data = notification();
    data.put("trade_status", "TRADE_PENDING_NEW");
    try (EPayClient client = client(null, neverSend())) {
      VerifiedNotification result =
          client
              .verifyNotification(notificationRequest(signed(data, platform.getPrivate())))
              .data();
      assertEquals(OrderStatus.UNKNOWN, result.status());
      assertEquals("TRADE_PENDING_NEW", result.rawStatus());
    }
  }

  @Test
  void notificationRequiresMerchantBothOrderIdentifiersPositiveAmountAndStatus() {
    for (String key :
        List.of("pid", "trade_no", "out_trade_no", "money", "trade_status", "timestamp")) {
      Map<String, Object> data = notification();
      data.remove(key);
      assertNotificationError(signed(data, platform.getPrivate()), EPayException.Kind.PROTOCOL);
    }
    for (Map.Entry<String, String> entry :
        Map.of("pid", "another", "money", "0", "out_trade_no", " ").entrySet()) {
      Map<String, Object> data = notification();
      data.put(entry.getKey(), entry.getValue());
      assertNotificationError(signed(data, platform.getPrivate()), EPayException.Kind.PROTOCOL);
    }
  }

  @Test
  void notificationNeverAcceptsWrongRoleUnsignedOrTamperedNewFields() {
    assertNotificationError(notification(), EPayException.Kind.SIGNATURE);
    assertNotificationError(
        signed(notification(), merchant.getPrivate()), EPayException.Kind.SIGNATURE);
    for (String key : List.of("money", "param", "future_field", "pid", "sign", "sign_type")) {
      Map<String, Object> data = notification();
      data.put("future_field", "original");
      data = signed(data, platform.getPrivate());
      data.put(key, "tampered");
      assertNotificationError(data, EPayException.Kind.SIGNATURE);
    }
  }

  @Test
  void refundKeepsCallerIdempotencyKeyAndOnlyReportsAccepted() {
    List<Map<String, String>> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            null,
            request -> {
              Map<String, String> expected =
                  new LinkedHashMap<>(
                      Map.of(
                          "pid",
                          "1001",
                          "timestamp",
                          Long.toString(NOW),
                          "sign_type",
                          "RSA",
                          "out_trade_no",
                          "O1",
                          "money",
                          "0.50",
                          "out_refund_no",
                          "R-out-1"));
              assertWire(request, "refund", expected);
              sent.add(request.parameters());
              Map<String, Object> data = refundResponse();
              // 退款提交契约没有最终状态，不能借未知新增 status 推断资金已退回。
              data.put("status", 1);
              return response(data);
            })) {
      RefundRequest request =
          new RefundRequest(OrderReference.byOutTradeNo("O1"), new BigDecimal("0.50"), "R-out-1");
      for (int i = 0; i < 2; i++) {
        RefundResult result = client.refund(request).data();
        assertEquals(RefundStatus.ACCEPTED, result.status());
        assertNull(result.rawStatus());
        assertEquals("R1", result.refundNo());
        assertEquals("R-out-1", result.outRefundNo());
        assertEquals(new BigDecimal("0.50"), result.amount());
      }
      assertEquals(sent.get(0), sent.get(1));
    }
  }

  @Test
  void optionalRefundIdIsNeverInventedAndTradeReferenceIsNotConfusedWithRefundReference() {
    try (EPayClient client =
        client(
            null,
            request -> {
              assertWire(
                  request,
                  "refund",
                  Map.of(
                      "pid",
                      "1001",
                      "timestamp",
                      Long.toString(NOW),
                      "sign_type",
                      "RSA",
                      "trade_no",
                      "T1",
                      "money",
                      "0.50"));
              Map<String, Object> data = refundResponse();
              data.remove("out_refund_no");
              return response(data);
            })) {
      assertEquals(
          RefundStatus.ACCEPTED,
          client
              .refund(
                  new RefundRequest(OrderReference.byTradeNo("T1"), new BigDecimal("0.50"), null))
              .data()
              .status());
    }
  }

  @Test
  void queryRefundUsesDocumentedRefundFieldsAndItsOwnStatusTable() {
    for (RefundReference reference :
        List.of(RefundReference.byRefundNo("R1"), RefundReference.byOutRefundNo("R-out-1"))) {
      for (String raw : List.of("0", "1", "2", "999")) {
        try (EPayClient client =
            client(
                null,
                request -> {
                  Map<String, String> expected =
                      new LinkedHashMap<>(
                          Map.of(
                              "pid", "1001", "timestamp", Long.toString(NOW), "sign_type", "RSA"));
                  expected.put(
                      reference.type() == RefundReference.Type.REFUND_NO
                          ? "refund_no"
                          : "out_refund_no",
                      reference.value());
                  assertWire(request, "refundquery", expected);
                  Map<String, Object> data = refundResponse();
                  data.put("status", raw);
                  data.put("addtime", "2026-09-24 10:00:00");
                  return response(data);
                })) {
          RefundResult result = client.queryRefund(reference).data();
          assertEquals(
              raw.equals("0")
                  ? RefundStatus.FAILED
                  : raw.equals("1") ? RefundStatus.SUCCEEDED : RefundStatus.UNKNOWN,
              result.status());
          assertEquals(raw, result.rawStatus());
          assertEquals("O1", result.outTradeNo());
        }
      }
    }
  }

  @Test
  void xarrDisablesRefundsAndBothDialectsDisableUnverifiedMerchantAndListContracts() {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      try (EPayClient client = client(new EpayV2Adapter(dialect), neverSend())) {
        Set<Capability> expected =
            dialect == EpayV2Adapter.Dialect.REFERENCE
                ? Set.of(
                    Capability.CREATE_PAYMENT,
                    Capability.QUERY_ORDER,
                    Capability.VERIFY_NOTIFICATION,
                    Capability.REFUND,
                    Capability.QUERY_REFUND)
                : Set.of(
                    Capability.CREATE_PAYMENT,
                    Capability.QUERY_ORDER,
                    Capability.VERIFY_NOTIFICATION);
        assertEquals(expected, client.capabilities());
        assertEquals(
            EPayException.Kind.UNSUPPORTED,
            assertThrows(EPayException.class, client::queryMerchant).kind());
        assertEquals(
            EPayException.Kind.UNSUPPORTED,
            assertThrows(EPayException.class, () -> client.listOrders(null)).kind());
        if (dialect == EpayV2Adapter.Dialect.XARR) {
          assertEquals(
              EPayException.Kind.UNSUPPORTED,
              assertThrows(EPayException.class, () -> client.refund(refund())).kind());
          assertEquals(
              EPayException.Kind.UNSUPPORTED,
              assertThrows(
                      EPayException.class,
                      () -> client.queryRefund(RefundReference.byRefundNo("R1")))
                  .kind());
        }
      }
    }
  }

  @Test
  void allBusinessResponsesIncludingRefundRejectionsAreAuthenticatedBeforeUse() {
    for (int operation = 0; operation < 4; operation++) {
      final int selected = operation;
      Map<String, Object> denied = base();
      denied.put("code", 403);
      denied.put("msg", "未开通");
      AtomicInteger sent = new AtomicInteger();
      try (EPayClient client =
          client(
              null,
              request -> {
                sent.incrementAndGet();
                return response(denied);
              })) {
        GatewayResult<?> result = operation(client, selected);
        assertFalse(result.success());
        assertEquals("403", result.code());
        assertEquals("未开通", result.message());
        assertEquals(1, sent.get());
      }
      for (String body : List.of(json(denied), json(signed(denied, merchant.getPrivate())))) {
        try (EPayClient client = client(null, request -> new TransportResponse(200, body))) {
          EPayException error =
              assertThrows(EPayException.class, () -> operation(client, selected));
          assertEquals(EPayException.Kind.SIGNATURE, error.kind());
          assertEquals(selected == 0 || selected == 2, error.executionUncertain());
        }
      }
    }
  }

  @Test
  void refundConnectionLossIsUncertainNotFailedOrRetriedAndDoesNotLeakSecrets() {
    AtomicInteger attempts = new AtomicInteger();
    try (EPayClient client =
        client(
            null,
            request -> {
              attempts.incrementAndGet();
              throw new IllegalStateException("private-token-and-request");
            })) {
      EPayException error = assertThrows(EPayException.class, () -> client.refund(refund()));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertTrue(error.executionUncertain());
      assertNull(error.getCause());
      assertEquals(0, error.getSuppressed().length);
      assertFalse(error.toString().contains("private-token"));
      assertEquals(1, attempts.get());
    }
  }

  @Test
  void refundRejectsSignedWrongAmountOrReferenceAndNeverReadsNestedMoney() {
    for (Object invalid : List.of("0.60", Map.of("money", "0.50"))) {
      Map<String, Object> data = refundResponse();
      data.put("money", invalid);
      try (EPayClient client = client(null, request -> response(data))) {
        EPayException error = assertThrows(EPayException.class, () -> client.refund(refund()));
        assertEquals(EPayException.Kind.PROTOCOL, error.kind());
        assertTrue(error.executionUncertain());
      }
    }
    Map<String, Object> data = refundResponse();
    data.put("refund_no", "another");
    data.put("status", 1);
    try (EPayClient client = client(null, request -> response(data))) {
      assertEquals(
          EPayException.Kind.PROTOCOL,
          assertThrows(
                  EPayException.class, () -> client.queryRefund(RefundReference.byRefundNo("R1")))
              .kind());
    }
  }

  @Test
  void incorrectlyConfiguredRsaRolesCannotMasqueradeAsCorrectMerchantOrPlatform() {
    MerchantConfig wrongRequestKey =
        MerchantConfig.builder()
            .baseUrl(BASE)
            .merchantId("1001")
            .credentials(
                new RsaCredentials(
                    pem("PRIVATE KEY", platform.getPrivate().getEncoded()),
                    pem("PUBLIC KEY", platform.getPublic().getEncoded())))
            .build();
    try (EPayClient client =
        EPayClient.builder()
            .config(wrongRequestKey)
            .clock(CLOCK)
            .transport(
                request -> {
                  assertFalse(
                      verify(
                          canonical(request.parameters()),
                          request.parameters().get("sign"),
                          merchant.getPublic()));
                  Map<String, Object> denied = base();
                  denied.put("code", "BAD_MERCHANT_SIGNATURE");
                  return response(denied);
                })
            .build()) {
      assertFalse(client.queryOrder(OrderReference.byTradeNo("T1")).success());
    }
    MerchantConfig wrongResponseKey =
        MerchantConfig.builder()
            .baseUrl(BASE)
            .merchantId("1001")
            .credentials(
                new RsaCredentials(
                    pem("PRIVATE KEY", merchant.getPrivate().getEncoded()),
                    pem("PUBLIC KEY", merchant.getPublic().getEncoded())))
            .build();
    try (EPayClient client =
        EPayClient.builder()
            .config(wrongResponseKey)
            .clock(CLOCK)
            .transport(request -> response(order()))
            .build()) {
      assertEquals(
          EPayException.Kind.SIGNATURE,
          assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("T1")))
              .kind());
    }
  }

  @Test
  void querySignsTradeReferenceAndXarrDoesNotInventAnUndocumentedMerchantField() {
    try (EPayClient client =
        client(
            new EpayV2Adapter(EpayV2Adapter.Dialect.XARR),
            request -> {
              assertWire(
                  request,
                  "query",
                  Map.of(
                      "pid",
                      "1001",
                      "timestamp",
                      Long.toString(NOW),
                      "sign_type",
                      "RSA",
                      "trade_no",
                      "T1"));
              Map<String, Object> data = order();
              data.remove("pid");
              data.put("status", 2);
              return response(data);
            })) {
      OrderResult result = client.queryOrder(OrderReference.byTradeNo("T1")).data();
      assertEquals(OrderStatus.PAID, result.status());
      assertNull(result.merchantId());
    }
  }

  @Test
  void timeConfigurationRejectsInvalidValuesAndNonIntegerTimestampLexemes() {
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(EPayException.class, () -> new EpayV2Adapter(null)).kind());
    assertEquals(
        EPayException.Kind.CONFIGURATION,
        assertThrows(
                EPayException.class,
                () -> new EpayV2Adapter(EpayV2Adapter.Dialect.REFERENCE, Duration.ofSeconds(-1)))
            .kind());
    for (Object invalid :
        List.of(
            true,
            false,
            new BigDecimal("1790208000.0"),
            new BigDecimal("1.790208E+9"),
            List.of(NOW),
            Map.of("value", NOW),
            1790208000000L,
            -NOW,
            0)) {
      Map<String, Object> data = order();
      data.put("timestamp", invalid);
      assertQueryError(json(signed(data, platform.getPrivate())), EPayException.Kind.PROTOCOL);
    }
    Map<String, Object> missing = order();
    missing.remove("timestamp");
    assertQueryError(json(signed(missing, platform.getPrivate())), EPayException.Kind.PROTOCOL);
    try (EPayClient client =
        EPayClient.builder()
            .config(config())
            .clock(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))
            .transport(neverSend())
            .build()) {
      assertEquals(
          EPayException.Kind.CONFIGURATION,
          assertThrows(EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("T1")))
              .kind());
    }
  }

  @Test
  void integerAndStringResponseTimestampsShareSignedSecondsButTamperingStillFails() {
    for (int index = 0; index < 4; index++) {
      final int selected = index;
      Map<String, Object> data =
          index == 0
              ? paymentResponse("jump", "/cashier")
              : index == 1 ? order() : refundResponse();
      if (index == 3) data.put("status", 1);
      Map<String, Object> signed = signed(data, platform.getPrivate());
      for (Object timestamp : List.of(NOW, Long.toString(NOW))) {
        // 不重新签名：整数和字符串的待签名秒值原文一致。
        Map<String, Object> converted = new LinkedHashMap<>(signed);
        converted.put("timestamp", timestamp);
        try (EPayClient client =
            client(null, wire -> new TransportResponse(200, json(converted)))) {
          assertTrue(operation(client, selected).success());
        }
      }
      Map<String, Object> changed = new LinkedHashMap<>(signed);
      changed.put("timestamp", NOW + 1);
      try (EPayClient client = client(null, wire -> new TransportResponse(200, json(changed)))) {
        assertEquals(
            EPayException.Kind.SIGNATURE,
            assertThrows(EPayException.class, () -> operation(client, selected)).kind());
      }
    }
    String valid = json(signed(order(), platform.getPrivate()));
    assertQueryError(
        "{\"timestamp\":1790208000," + valid.substring(1), EPayException.Kind.PROTOCOL);
  }

  @Test
  void unsignedExtrasStayPrivateAndCannotInvalidateSignatureByBeingIgnoredIncorrectly() {
    Map<String, Object> data = paymentResponse("jsapi", "{\"appId\":\"wx-safe\"}");
    data.put("unknown", Map.of("money", "999", "status", "PAID"));
    data.put("unknown_array", List.of("unsigned"));
    try (EPayClient client = client(null, request -> response(data))) {
      PaymentResult result = client.createPayment(payment().build()).data();
      assertEquals(
          "{\"appId\":\"wx-safe\"}", ((PaymentAction.JsApi) result.actions().get(0)).payload());
    }
    Map<String, Object> tampered = signed(data, platform.getPrivate());
    tampered.put("trade_no", "tampered");
    String changed = json(tampered);
    try (EPayClient client = client(null, request -> new TransportResponse(200, changed))) {
      assertEquals(
          EPayException.Kind.SIGNATURE,
          assertThrows(EPayException.class, () -> client.createPayment(payment().build())).kind());
    }
  }

  @Test
  void documentedMiniProgramAndScanActionsPreserveSignedStringsWithoutExecutingOrInferringStatus() {
    // 参考 V2 统一下单示例：wxplugin/appId/supplierId/shopId/orderId，wxapp/extraData 是字符串。
    Map<String, String> payloads =
        Map.of(
            "wxplugin",
                " {\"shopId\":\"123456\", \"appId\":\"wx1\",\"supplierId\":\"supplier-secret\",\"orderId\":\"T1\"} ",
            "wxapp",
                "{\"appId\":\"wx1\",\"miniProgramId\":\"gh_1\",\"path\":\"pages/pay?orderid=T1&x=1\",\"extraData\":\"{\\\"token\\\":\\\"private\\\"}\"}",
            "scan",
                "{\"money\":\"1.00\", \"trade_no\":\"inner-T1\",\"buyer\":\"private-buyer\",\"status\":\"FUTURE_STATE\"}");
    for (String type : List.of("wxplugin", "wxapp", "scan")) {
      String payload = payloads.get(type);
      for (String method :
          type.equals("scan")
              ? List.of("scan")
              : type.equals("wxplugin")
                  ? List.of("applet", "web")
                  : List.of("app", "applet", "web")) {
        PaymentRequest request =
            payment()
                .device(null)
                .options(
                    PaymentOptions.builder()
                        .method(method)
                        .authCode(method.equals("scan") ? "123456" : null)
                        .build())
                .build();
        Map<String, Object> data = paymentResponse(type, payload);
        data.put("status", "PAID");
        data.put("unknown", Map.of("money", "999", "status", "PAID"));
        AtomicInteger calls = new AtomicInteger();
        try (EPayClient client =
            client(
                null,
                wire -> {
                  calls.incrementAndGet();
                  return response(data);
                })) {
          GatewayResult<PaymentResult> response = client.createPayment(request);
          assertTrue(response.success());
          PaymentResult result = response.data();
          assertEquals(1, result.actions().size());
          PaymentAction action = result.actions().get(0);
          String actual =
              switch (type) {
                case "wxplugin" ->
                    assertInstanceOf(PaymentAction.WechatPlugin.class, action).payload();
                case "wxapp" -> assertInstanceOf(PaymentAction.MiniProgram.class, action).payload();
                default -> assertInstanceOf(PaymentAction.ScanResult.class, action).payload();
              };
          assertEquals(payload, actual);
          assertTrue(action.toString().endsWith("[已遮蔽]"));
          assertFalse(action.toString().contains(payload));
          assertEquals("T1", result.tradeNo());
          assertEquals("O1", result.outTradeNo());
          assertEquals(1, calls.get());
        }
        for (Object invalid :
            List.of(Map.of("appId", "unsigned"), List.of("unsigned"), true, 1, "")) {
          try (EPayClient client = client(null, wire -> response(paymentResponse(type, invalid)))) {
            assertEquals(
                EPayException.Kind.PROTOCOL,
                assertThrows(EPayException.class, () -> client.createPayment(request)).kind());
          }
        }
        Map<String, Object> tampered = signed(data, platform.getPrivate());
        tampered.put("pay_info", payload + " ");
        try (EPayClient client = client(null, wire -> new TransportResponse(200, json(tampered)))) {
          assertEquals(
              EPayException.Kind.SIGNATURE,
              assertThrows(EPayException.class, () -> client.createPayment(request)).kind());
        }
      }
    }
  }

  @Test
  void scanRequiresSignedInfoAndCannotMasqueradeAsAnotherMethod() {
    try (EPayClient client =
        client(null, request -> response(paymentResponse("scan", "{\"money\":\"1.00\"}")))) {
      assertEquals(
          EPayException.Kind.PROTOCOL,
          assertThrows(EPayException.class, () -> client.createPayment(payment().build())).kind());
    }
    Map<String, Object> missing = base();
    missing.put("trade_no", "T1");
    try (EPayClient client = client(null, request -> response(missing))) {
      assertEquals(
          EPayException.Kind.PROTOCOL,
          assertThrows(
                  EPayException.class,
                  () ->
                      client.createPayment(
                          payment()
                              .device(null)
                              .options(
                                  PaymentOptions.builder()
                                      .method("scan")
                                      .authCode("123456")
                                      .build())
                              .build()))
              .kind());
    }
  }

  @Test
  void fixedOpenSslRequestsMatchLiteralSignaturesAndSignedResponsesVerify() {
    for (String route : List.of("create", "query", "refund", "refundquery")) {
      AtomicInteger calls = new AtomicInteger();
      try (EPayClient client =
          fixedClient(
              "a",
              new EpayV2Adapter(),
              wire -> {
                calls.incrementAndGet();
                assertEquals("POST", wire.method());
                assertEquals(BASE + "api/pay/" + route, wire.uri().toString());
                assertFixedWire(route + "-request", wire.parameters());
                return fixedResponse(route + "-response");
              })) {
        GatewayResult<?> result = fixedOperation(client, route);
        assertTrue(result.success(), route);
        assertEquals("0", result.code());
        switch (route) {
          case "create" -> {
            PaymentResult payment = (PaymentResult) result.data();
            assertEquals("T1", payment.tradeNo());
            assertEquals("订单 &+=", payment.outTradeNo());
            assertEquals(
                "{\"appId\":\"wx-test\",\"zero\":0,\"note\":\"中文 &+=\"}",
                assertInstanceOf(PaymentAction.JsApi.class, payment.actions().get(0)).payload());
          }
          case "query" -> {
            OrderResult order = (OrderResult) result.data();
            assertEquals("1001", order.merchantId());
            assertEquals("订单 &+=", order.outTradeNo());
            assertEquals(new BigDecimal("1.00"), order.amount());
            assertEquals(OrderStatus.PAID, order.status());
          }
          default -> {
            RefundResult refund = (RefundResult) result.data();
            assertEquals("R1", refund.refundNo());
            assertEquals("退款 &+=", refund.outRefundNo());
            assertEquals(new BigDecimal("0.50"), refund.amount());
            assertEquals(
                route.equals("refund") ? RefundStatus.ACCEPTED : RefundStatus.SUCCEEDED,
                refund.status());
          }
        }
        assertEquals(1, calls.get());
      }
    }
  }

  @Test
  void fixedOpenSslBrowserFormSignsUnescapedValuesAndExcludesAbsentAndEmptyValues() {
    try (EPayClient client = fixedClient("a", new EpayV2Adapter(), neverSend())) {
      GatewayResult<PaymentResult> created =
          client.createPayment(
              fixedPayment()
                  .scene(PaymentScene.BROWSER_FORM)
                  .options(PaymentOptions.defaults())
                  .paymentMethod(null)
                  .clientIp(null)
                  .build());
      assertNull(created.code());
      PaymentResult result = created.data();
      String html = assertInstanceOf(PaymentAction.Form.class, result.actions().get(0)).html();
      assertTrue(html.contains("action=\"" + BASE + "api/pay/submit\""));
      Map<String, String> fields = new LinkedHashMap<>();
      java.util.regex.Matcher matcher =
          java.util.regex.Pattern.compile("name=\"([^\"]+)\" value=\"([^\"]*)\"").matcher(html);
      while (matcher.find())
        fields.put(
            matcher.group(1),
            matcher
                .group(2)
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&"));
      assertEquals("", fields.get("param"));
      assertFalse(fields.containsKey("type"));
      assertFixedWire("submit-request", fields);
    }
  }

  @Test
  void fixedOpenSslNotificationVerifiesChineseZeroEmptyAndSpecialCharacters() {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      try (EPayClient client = fixedClient("a", new EpayV2Adapter(dialect), neverSend())) {
        VerifiedNotification result =
            client.verifyNotification(fixedNotification("notify-a")).data();
        assertEquals("1001", result.merchantId());
        assertEquals("T1", result.tradeNo());
        assertEquals("订单 &+=", result.outTradeNo());
        assertEquals(new BigDecimal("1.00"), result.amount());
        assertEquals("原样参数 &+=/%", result.param());
        assertEquals(OrderStatus.PAID, result.status());
        assertEquals("TRADE_SUCCESS", result.rawStatus());
        assertEquals("success", result.successAck());
      }
    }
  }

  @Test
  void fixedSignedOrderAndRefundResponseStatusTamperingFailsBeforeMapping() {
    for (String route : List.of("query", "refundquery")) {
      String original = vector(route + "-response").getString("body");
      String changed = original.replace("\"status\":1", "\"status\":0");
      assertNotEquals(original, changed, "必须实际改变状态，不能重新签名");
      for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
        if (route.equals("refundquery") && dialect == EpayV2Adapter.Dialect.XARR) continue;
        try (EPayClient client =
            fixedClient(
                "a", new EpayV2Adapter(dialect), wire -> new TransportResponse(200, changed))) {
          EPayException error =
              assertThrows(EPayException.class, () -> fixedOperation(client, route));
          assertEquals(EPayException.Kind.SIGNATURE, error.kind());
          assertFalse(error.executionUncertain());
        }
      }
    }
  }

  @Test
  void fixedSignedNotificationStatusTamperingFailsRatherThanReturningUnknown() {
    Map<String, String> changed = new LinkedHashMap<>(fixedNotification("notify-a").parameters());
    assertEquals("TRADE_SUCCESS", changed.put("trade_status", "TRADE_CLOSED"));
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      try (EPayClient client = fixedClient("a", new EpayV2Adapter(dialect), neverSend())) {
        EPayException error =
            assertThrows(
                EPayException.class,
                () -> client.verifyNotification(NotificationRequest.fromParameters(changed)));
        assertEquals(EPayException.Kind.SIGNATURE, error.kind());
        assertFalse(error.executionUncertain());
      }
    }
  }

  @Test
  void sharedV2AdapterKeepsConcurrentMerchantKeysBasesClocksAndNotificationsIsolated()
      throws Exception {
    for (EpayV2Adapter.Dialect dialect : EpayV2Adapter.Dialect.values()) {
      EpayV2Adapter shared = new EpayV2Adapter(dialect);
      CyclicBarrier inTransport = new CyclicBarrier(2);
      ExecutorService executor = Executors.newFixedThreadPool(2);
      AtomicInteger callsA = new AtomicInteger();
      AtomicInteger callsB = new AtomicInteger();
      try (EPayClient a = fixedClient("a", shared, isolatedTransport("a", inTransport, callsA));
          EPayClient b = fixedClient("b", shared, isolatedTransport("b", inTransport, callsB))) {
        // 每轮双方同时停在传输边界，再各自用不同平台公钥验签；无需 sleep 赌调度。
        Future<?> first = executor.submit(() -> exerciseIsolatedMerchant(a, "a", dialect));
        Future<?> second = executor.submit(() -> exerciseIsolatedMerchant(b, "b", dialect));
        first.get(30, TimeUnit.SECONDS);
        second.get(30, TimeUnit.SECONDS);
        assertEquals(16, callsA.get());
        assertEquals(16, callsB.get());
      } finally {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "并发测试线程必须退出");
      }
    }
  }

  private static HttpTransport isolatedTransport(
      String tenant, CyclicBarrier barrier, AtomicInteger calls) {
    return wire -> {
      calls.incrementAndGet();
      assertEquals("POST", wire.method());
      assertEquals(fixedBase(tenant) + "api/pay/query", wire.uri().toString());
      assertFixedWire(tenant.equals("a") ? "query-request" : "query-b-request", wire.parameters());
      try {
        barrier.await(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        throw new AssertionError("两个商户必须确实并发进入传输边界", e);
      }
      return fixedResponse(tenant.equals("a") ? "query-response" : "query-b-response");
    };
  }

  private static void exerciseIsolatedMerchant(
      EPayClient client, String tenant, EpayV2Adapter.Dialect dialect) {
    boolean a = tenant.equals("a");
    for (int i = 0; i < 16; i++) {
      OrderResult result =
          client.queryOrder(OrderReference.byOutTradeNo(a ? "订单 &+=" : "B-订单 &+=")).data();
      assertEquals(a ? "1001" : "1002", result.merchantId());
      assertEquals(a ? "T1" : "T2", result.tradeNo());
      assertEquals(a ? "订单 &+=" : "B-订单 &+=", result.outTradeNo());
      assertEquals(new BigDecimal(a ? "1.00" : "2.00"), result.amount());
      assertEquals(
          dialect == EpayV2Adapter.Dialect.REFERENCE ? OrderStatus.PAID : OrderStatus.PENDING,
          result.status());
      VerifiedNotification notification =
          client.verifyNotification(fixedNotification("notify-" + tenant)).data();
      assertEquals(a ? "1001" : "1002", notification.merchantId());
      assertEquals(result.outTradeNo(), notification.outTradeNo());
      assertEquals(result.amount(), notification.amount());
      assertEquals(OrderStatus.PAID, notification.status());
      EPayException wrongPlatform =
          assertThrows(
              EPayException.class,
              () -> client.verifyNotification(fixedNotification(a ? "notify-b" : "notify-a")));
      assertEquals(EPayException.Kind.SIGNATURE, wrongPlatform.kind());
    }
  }

  private static GatewayResult<?> fixedOperation(EPayClient client, String route) {
    return switch (route) {
      case "create" -> client.createPayment(fixedPayment().build());
      case "query" -> client.queryOrder(OrderReference.byOutTradeNo("订单 &+="));
      case "refund" ->
          client.refund(
              new RefundRequest(OrderReference.byTradeNo("T1"), new BigDecimal("0.50"), "退款 &+="));
      case "refundquery" -> client.queryRefund(RefundReference.byOutRefundNo("退款 &+="));
      default -> throw new AssertionError("没有该操作的固定样本：" + route);
    };
  }

  private static PaymentRequest.Builder fixedPayment() {
    return PaymentRequest.builder()
        .outTradeNo("订单 &+=")
        .name("商品 &+\"测试\"=/%")
        .amount(new BigDecimal("1.00"))
        .paymentMethod("wxpay")
        .param("")
        .notifyUrl("https://shop.example/notify?a=1&b=%2B")
        .returnUrl("https://shop.example/return")
        .clientIp("192.0.2.1")
        .device(null)
        .options(
            PaymentOptions.builder()
                .method("jsapi")
                .subAppId("wx-test")
                .subOpenId("用户 &+=")
                .isApplet(false)
                .build());
  }

  private static String fixedBase(String tenant) {
    return tenant.equals("a") ? BASE : "https://other.example/tenant-b/gateway/";
  }

  private static EPayClient fixedClient(
      String tenant, EpayV2Adapter adapter, HttpTransport transport) {
    JSONObject fixture = fixedFixture();
    JSONObject keys = fixture.getJSONObject("keys");
    MerchantConfig config =
        MerchantConfig.builder()
            .baseUrl(fixedBase(tenant))
            .merchantId(tenant.equals("a") ? "1001" : "1002")
            .credentials(
                new RsaCredentials(
                    keys.getJSONObject("merchant-" + tenant).getString("privatePem"),
                    keys.getJSONObject("platform-" + tenant).getString("publicPem")))
            .build();
    return EPayClient.builder()
        .config(config)
        .adapter(adapter)
        .transport(transport)
        .clock(
            Clock.fixed(Instant.ofEpochSecond(tenant.equals("a") ? NOW : NOW + 60), ZoneOffset.UTC))
        .build();
  }

  private static JSONObject fixedFixture() {
    try (InputStream input =
        EpayV2ContractTest.class.getResourceAsStream("/epayv2/public-rsa/fixtures.json")) {
      assertNotNull(input, "缺少公开 OpenSSL 固定向量，禁止退回动态 JCA 签名");
      return JSON.parseObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
    } catch (java.io.IOException e) {
      throw new AssertionError("无法读取公开测试向量", e);
    }
  }

  private static JSONObject vector(String id) {
    JSONObject result = fixedFixture().getJSONObject("vectors").getJSONObject(id);
    assertNotNull(result, "缺少固定向量：" + id);
    return result;
  }

  private static void assertFixedWire(String id, Map<String, String> actual) {
    JSONObject expected = vector(id);
    assertEquals(expected.getJSONObject("parameters"), actual, id);
    assertEquals(expected.getString("canonical"), canonical(actual), id);
    byte[] expectedBytes = Base64.getDecoder().decode(expected.getString("signature"));
    assertEquals(256, expectedBytes.length, "RSA 2048 位签名");
    assertArrayEquals(expectedBytes, Base64.getDecoder().decode(actual.get("sign")), id);
  }

  private static TransportResponse fixedResponse(String id) {
    // 已签名的固定 JSON 原文字节；不经过 signed()/response() 或重新序列化。
    return new TransportResponse(200, vector(id).getString("body"));
  }

  private static NotificationRequest fixedNotification(String id) {
    Map<String, String> parameters = new LinkedHashMap<>();
    vector(id)
        .getJSONObject("parameters")
        .forEach((name, value) -> parameters.put(name, (String) value));
    return NotificationRequest.fromParameters(parameters);
  }

  private static GatewayResult<?> operation(EPayClient client, int index) {
    return switch (index) {
      case 0 -> client.createPayment(payment().build());
      case 1 -> client.queryOrder(OrderReference.byTradeNo("T1"));
      case 2 -> client.refund(refund());
      default -> client.queryRefund(RefundReference.byRefundNo("R1"));
    };
  }

  private static RefundRequest refund() {
    return new RefundRequest(OrderReference.byTradeNo("T1"), new BigDecimal("0.50"), "R-out-1");
  }

  private static Map<String, Object> refundResponse() {
    Map<String, Object> data = base();
    data.putAll(
        Map.of(
            "refund_no",
            "R1",
            "out_refund_no",
            "R-out-1",
            "trade_no",
            "T1",
            "out_trade_no",
            "O1",
            "money",
            "0.50",
            "reducemoney",
            "0.50"));
    return data;
  }

  private static Map<String, Object> notification() {
    Map<String, Object> values = order();
    values.remove("code");
    values.remove("status");
    values.put("trade_status", "TRADE_SUCCESS");
    values.put("param", "原样参数 &+=");
    return values;
  }

  private static NotificationRequest notificationRequest(Map<String, Object> data) {
    Map<String, String> parameters = new LinkedHashMap<>();
    data.forEach((key, value) -> parameters.put(key, value.toString()));
    return NotificationRequest.fromParameters(parameters);
  }

  private static void assertNotificationError(Map<String, Object> data, EPayException.Kind kind) {
    try (EPayClient client = client(null, neverSend())) {
      EPayException error =
          assertThrows(
              EPayException.class, () -> client.verifyNotification(notificationRequest(data)));
      assertEquals(kind, error.kind());
      assertFalse(error.executionUncertain());
    }
  }

  private static PaymentRequest.Builder payment() {
    return PaymentRequest.builder()
        .outTradeNo("O1")
        .name("商品")
        .amount(new BigDecimal("1.00"))
        .paymentMethod("wxpay")
        .notifyUrl("https://shop.example/notify")
        .returnUrl("https://shop.example/return")
        .clientIp("192.0.2.1")
        .device("pc");
  }

  private static Map<String, String> paymentWire() {
    Map<String, String> expected =
        new LinkedHashMap<>(
            Map.of(
                "pid",
                "1001",
                "timestamp",
                Long.toString(NOW),
                "sign_type",
                "RSA",
                "out_trade_no",
                "O1",
                "money",
                "1.00",
                "name",
                "商品",
                "type",
                "wxpay",
                "notify_url",
                "https://shop.example/notify",
                "return_url",
                "https://shop.example/return",
                "method",
                "web"));
    expected.put("device", "pc");
    expected.put("clientip", "192.0.2.1");
    return expected;
  }

  private static Map<String, Object> paymentResponse(String type, Object info) {
    Map<String, Object> data = base();
    data.putAll(Map.of("trade_no", "T1", "pay_type", type, "pay_info", info));
    return data;
  }

  private static EPayClient client(EpayV2Adapter adapter, HttpTransport transport) {
    return EPayClient.builder()
        .config(config())
        .adapter(adapter)
        .clock(CLOCK)
        .transport(transport)
        .build();
  }

  private static MerchantConfig config() {
    return MerchantConfig.builder()
        .baseUrl(BASE)
        .merchantId("1001")
        .credentials(
            new RsaCredentials(
                pem("PRIVATE KEY", merchant.getPrivate().getEncoded()),
                pem("PUBLIC KEY", platform.getPublic().getEncoded())))
        .build();
  }

  private static String pem(String label, byte[] bytes) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getEncoder().encodeToString(bytes)
        + "\n-----END "
        + label
        + "-----";
  }

  private static HttpTransport neverSend() {
    return request -> {
      throw new AssertionError("禁止联网或协议探测");
    };
  }

  private static Map<String, Object> base() {
    return new LinkedHashMap<>(
        Map.of("code", 0, "timestamp", Long.toString(NOW), "sign_type", "RSA"));
  }

  private static Map<String, Object> order() {
    Map<String, Object> data = base();
    data.putAll(
        Map.of(
            "pid",
            "1001",
            "trade_no",
            "T1",
            "out_trade_no",
            "O1",
            "money",
            "1.00",
            "status",
            1,
            "type",
            "wxpay"));
    return data;
  }

  private static TransportResponse response(Map<String, Object> values) {
    return new TransportResponse(200, json(signed(values, platform.getPrivate())));
  }

  private static Map<String, Object> signed(Map<String, Object> values, PrivateKey key) {
    Map<String, Object> result = new LinkedHashMap<>(values);
    Map<String, String> scalars = new LinkedHashMap<>();
    values.forEach(
        (name, value) -> {
          if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            scalars.put(name, value.toString());
          }
        });
    try {
      Signature signer = Signature.getInstance("SHA256withRSA");
      signer.initSign(key);
      signer.update(canonical(scalars).getBytes(StandardCharsets.UTF_8));
      result.put("sign", Base64.getEncoder().encodeToString(signer.sign()));
      return result;
    } catch (Exception e) {
      throw new AssertionError("独立签名失败", e);
    }
  }

  private static String canonical(Map<String, String> values) {
    return values.entrySet().stream()
        .filter(
            e ->
                !Set.of("sign", "sign_type").contains(e.getKey())
                    && e.getValue() != null
                    && !e.getValue().isEmpty())
        .sorted(Map.Entry.comparingByKey())
        .map(e -> e.getKey() + "=" + e.getValue())
        .collect(Collectors.joining("&"));
  }

  private static boolean verify(String text, String signed, PublicKey key) {
    try {
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(key);
      verifier.update(text.getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(signed));
    } catch (Exception e) {
      throw new AssertionError("独立验签失败", e);
    }
  }

  private static String json(Map<String, Object> values) {
    // 数值用原始词素输出，避免通用序列化器归一化指数和尾零。
    return values.entrySet().stream()
        .map(
            e ->
                JSON.toJSONString(e.getKey())
                    + ":"
                    + (e.getValue() instanceof Number
                        ? e.getValue().toString()
                        : JSON.toJSONString(e.getValue())))
        .collect(Collectors.joining(",", "{", "}"));
  }

  private static void assertWire(
      TransportRequest request, String route, Map<String, String> expected) {
    assertEquals("POST", request.method());
    assertEquals(BASE + "api/pay/" + route, request.uri().toString());
    Map<String, String> actual = new LinkedHashMap<>(request.parameters());
    String signature = actual.remove("sign");
    assertEquals(expected, actual);
    assertTrue(verify(canonical(actual), signature, merchant.getPublic()));
    assertThrows(
        UnsupportedOperationException.class, () -> request.parameters().put("money", "99"));
  }

  private static void assertQueryError(String body, EPayException.Kind kind) {
    try (EPayClient client = client(null, request -> new TransportResponse(200, body))) {
      EPayException error =
          assertThrows(
              EPayException.class, () -> client.queryOrder(OrderReference.byTradeNo("T1")));
      assertEquals(kind, error.kind());
      assertFalse(error.executionUncertain());
    }
  }
}
