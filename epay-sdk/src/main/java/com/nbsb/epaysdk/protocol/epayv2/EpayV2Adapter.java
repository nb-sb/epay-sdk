package com.nbsb.epaysdk.protocol.epayv2;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.FormCodec;
import com.nbsb.epaysdk.internal.Signatures;
import com.nbsb.epaysdk.internal.StrictJson;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import com.nbsb.epaysdk.spi.ProtocolContext;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** V2 双向 RSA 协议；方言显式选择，绝不探测或降级。 */
public final class EpayV2Adapter implements ProtocolAdapter {
  public enum Dialect {
    REFERENCE,
    XARR
  }

  public static final String REFERENCE_ID = "epay-v2";
  public static final String XARR_ID = "epay-v2-xarr";

  private final Dialect dialect;
  private final Duration timeTolerance;

  public EpayV2Adapter() {
    this(Dialect.REFERENCE);
  }

  public EpayV2Adapter(Dialect dialect) {
    this(dialect, Duration.ofSeconds(300));
  }

  public EpayV2Adapter(Dialect dialect, Duration timeTolerance) {
    if (dialect == null || timeTolerance == null || timeTolerance.isNegative()) {
      throw EPayException.configuration();
    }
    this.dialect = dialect;
    this.timeTolerance = timeTolerance;
  }

  @Override
  public String id() {
    return dialect == Dialect.REFERENCE ? REFERENCE_ID : XARR_ID;
  }

  @Override
  public Set<Capability> capabilities() {
    return dialect == Dialect.REFERENCE
        ? Set.of(
            Capability.CREATE_PAYMENT,
            Capability.QUERY_ORDER,
            Capability.VERIFY_NOTIFICATION,
            Capability.REFUND,
            Capability.QUERY_REFUND)
        : Set.of(Capability.CREATE_PAYMENT, Capability.QUERY_ORDER, Capability.VERIFY_NOTIFICATION);
  }

  @Override
  public void validateCredentials(Credentials credentials) {
    if (!(credentials instanceof RsaCredentials)) throw EPayException.configuration();
  }

  @Override
  public GatewayResult<PaymentResult> createPayment(
      ProtocolContext context, PaymentRequest request) {
    Map<String, String> parameters = paymentParameters(context, request);
    if (request.scene() == PaymentScene.BROWSER_FORM) {
      PaymentAction form =
          new PaymentAction.Form(
              FormCodec.buildHtmlForm(
                  context.resolve("api/pay/submit"), signed(context, parameters)));
      return GatewayResult.success(
          null, new PaymentResult(null, request.outTradeNo(), List.of(form)));
    }
    StrictJson.ObjectValue response = post(context, "create", parameters);
    String code = response.requireText("code");
    if (!code.equals("0")) return GatewayResult.rejected(code, response.text("msg"));
    String tradeNo = required(response.text("trade_no"));
    List<PaymentAction> actions = paymentActions(context, response, request.options().method());
    return GatewayResult.success(
        code, response.text("msg"), new PaymentResult(tradeNo, request.outTradeNo(), actions));
  }

  private Map<String, String> paymentParameters(ProtocolContext context, PaymentRequest request) {
    boolean api = request.scene() == PaymentScene.API;
    PaymentOptions options = request.options();
    String method = options.method();
    if (!Set.of("web", "jump", "jsapi", "app", "scan", "applet").contains(method)
        || request.name().getBytes(StandardCharsets.UTF_8).length > 127)
      throw EPayException.validation();
    Checks.required(request.notifyUrl());
    if (dialect == Dialect.REFERENCE) Checks.required(request.returnUrl());
    if (api && (dialect == Dialect.XARR || !method.equals("scan")))
      Checks.text(request.paymentMethod());
    if (api && dialect == Dialect.REFERENCE) Checks.text(request.clientIp());
    if (request.device() != null
        && !Set.of("pc", "mobile", "qq", "wechat", "alipay").contains(request.device())) {
      throw EPayException.validation();
    }
    if (api && !method.equals("web") && request.device() != null && dialect == Dialect.REFERENCE) {
      throw EPayException.validation();
    }
    // V2 使用 sub_openid，不将其他协议的 openid 暗中改名或覆盖。
    if (options.openId() != null) throw EPayException.validation();
    if (api && method.equals("scan")) Checks.text(options.authCode());
    else if (options.authCode() != null) throw EPayException.validation();
    if (api && method.equals("jsapi")) {
      Checks.text(options.subOpenId());
      if ("wxpay".equals(request.paymentMethod())) Checks.text(options.subAppId());
    } else if (options.subOpenId() != null
        || options.subAppId() != null
        || options.isApplet() != null) {
      throw EPayException.validation();
    }
    if (!api && !method.equals("web")) throw EPayException.validation();

    Map<String, String> parameters = common(context);
    parameters.put("out_trade_no", request.outTradeNo());
    parameters.put("name", request.name());
    parameters.put("money", request.amount().toPlainString());
    parameters.put("notify_url", request.notifyUrl().toASCIIString());
    if (request.returnUrl() != null)
      parameters.put("return_url", request.returnUrl().toASCIIString());
    optional(parameters, "type", request.paymentMethod());
    optional(parameters, "param", request.param());
    // REFERENCE 页面接口未声明设备/IP；XARR 明确允许，两者不猜测互通。
    if (api || dialect == Dialect.XARR) {
      optional(parameters, "clientip", request.clientIp());
      optional(parameters, "device", request.device());
    }
    if (api) {
      parameters.put("method", method);
      optional(parameters, "auth_code", options.authCode());
      optional(parameters, "sub_openid", options.subOpenId());
      optional(parameters, "sub_appid", options.subAppId());
      // 来源：参考 V2 统一下单字段表，仅 API JSAPI 支持 is_applet，0 也参与签名。
      if (options.isApplet() != null) parameters.put("is_applet", options.isApplet() ? "1" : "0");
    }
    return parameters;
  }

  private static void optional(Map<String, String> parameters, String key, String value) {
    if (value != null) parameters.put(key, value);
  }

  private static List<PaymentAction> paymentActions(
      ProtocolContext context, StrictJson.ObjectValue response, String method) {
    String type = required(response.text("pay_type"));
    // 对象和数组不在签名覆盖内，绝不能 stringify 后冒充可信 JSAPI/APP 载荷。
    if (!(response.get("pay_info") instanceof StrictJson.Scalar info)
        || info.type() != StrictJson.Scalar.Type.STRING) throw EPayException.protocol();
    String payload = required(info.text());
    if (type.equals("scan")) {
      if (!method.equals("scan")) throw EPayException.protocol();
      // 文档为 scan + 字符串订单信息，不是缺少 pay_type/pay_info 的成功响应。
      // 保留签名覆盖的原文供调用方核对；不根据未知字段自动记账。
      return List.of(new PaymentAction.ScanResult(payload));
    }
    PaymentAction action =
        switch (type) {
          case "jump" -> new PaymentAction.Redirect(paymentUri(context, payload, true));
          // 参考 V2 的 qrcode 是任意二维码内容；不按 URI 解析、补前缀或改写签名原文。
          case "qrcode" -> new PaymentAction.QrCode(payload);
          case "urlscheme" ->
              new PaymentAction.UrlScheme(paymentUri(context, payload, false).toString());
          case "html" -> new PaymentAction.Html(payload);
          case "jsapi" -> new PaymentAction.JsApi(payload);
          case "app" -> new PaymentAction.App(payload);
          // 来源：参考 V2 统一下单 pay_type 示例；pay_info 是已签名的 JSON 字符串。
          // wxplugin 为 appId/supplierId/shopId/orderId，wxapp 为 appId/miniProgramId/path/extraData。
          // 仅交付原文，不解析重建、联网或执行平台插件/小程序。
          case "wxplugin" -> new PaymentAction.WechatPlugin(payload);
          case "wxapp" -> new PaymentAction.MiniProgram(payload);
          default -> throw EPayException.protocol();
        };
    return List.of(action);
  }

  private static URI paymentUri(ProtocolContext context, String value, boolean httpOnly) {
    try {
      URI uri = URI.create(value);
      if (uri.isAbsolute()) {
        if (httpOnly
            || "http".equalsIgnoreCase(uri.getScheme())
            || "https".equalsIgnoreCase(uri.getScheme())) {
          return Checks.httpUri(uri);
        }
        if (Set.of("javascript", "data", "vbscript", "file")
            .contains(uri.getScheme().toLowerCase(java.util.Locale.ROOT))) {
          throw EPayException.protocol();
        }
        return uri;
      }
      if (uri.getRawAuthority() != null || value.startsWith("//") || uri.getRawFragment() != null) {
        throw EPayException.protocol();
      }
      URI resolved = context.resolve(uri.getRawPath());
      return URI.create(
          resolved.toASCIIString() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery()));
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }

  @Override
  public GatewayResult<VerifiedNotification> verifyNotification(
      ProtocolContext context, NotificationRequest request) {
    Map<String, String> parameters = request.parameters();
    verifySignature(context, parameters);
    // 回调允许平台延迟重试，不设置最大年龄；仅拒绝非法秒值和超容差未来值。
    verifyTimestamp(context, parameters.get("timestamp"), true);
    String pid = required(parameters.get("pid"));
    if (!pid.equals(context.config().merchantId())) throw EPayException.protocol();
    String tradeNo = required(parameters.get("trade_no"));
    String outTradeNo = required(parameters.get("out_trade_no"));
    String status = required(parameters.get("trade_status"));
    // 调用方仍需对照本地订单核对金额，并在事务中落实幂等后才返回 ACK。
    VerifiedNotification result =
        new VerifiedNotification(
            pid,
            tradeNo,
            outTradeNo,
            amount(parameters.get("money")),
            "TRADE_SUCCESS".equals(status) ? OrderStatus.PAID : OrderStatus.UNKNOWN,
            status,
            parameters.get("type"),
            parameters.get("param"));
    return GatewayResult.success(null, result);
  }

  @Override
  public GatewayResult<OrderResult> queryOrder(ProtocolContext context, OrderReference request) {
    Map<String, String> parameters = common(context);
    String key = orderKey(request);
    parameters.put(key, request.value());
    StrictJson.ObjectValue response = post(context, "query", parameters);
    String code = response.requireText("code");
    if (!code.equals("0")) return GatewayResult.rejected(code, response.text("msg"));
    String pid = response.text("pid");
    if ((dialect == Dialect.REFERENCE && pid == null)
        || (pid != null && !pid.equals(context.config().merchantId()))) {
      throw EPayException.protocol();
    }
    String tradeNo = required(response.text("trade_no"));
    String outTradeNo = required(response.text("out_trade_no"));
    if (!request.value().equals(response.text(key))) throw EPayException.protocol();
    String rawStatus = required(response.text("status"));
    OrderResult result =
        new OrderResult(
            tradeNo,
            outTradeNo,
            pid,
            amount(response.requireText("money")),
            orderStatus(rawStatus),
            rawStatus,
            response.text("type"));
    return GatewayResult.success(code, response.text("msg"), result);
  }

  @Override
  public GatewayResult<RefundResult> refund(ProtocolContext context, RefundRequest request) {
    requireRefundCapability();
    Map<String, String> parameters = common(context);
    String key = orderKey(request.orderReference());
    parameters.put(key, request.orderReference().value());
    parameters.put("money", request.amount().toPlainString());
    // 文档允许省略；传入时原样用于幂等，绝不替调用方生成随机退款号。
    optional(parameters, "out_refund_no", request.outRefundNo());
    StrictJson.ObjectValue response = post(context, "refund", parameters);
    String code = response.requireText("code");
    if (!code.equals("0")) return GatewayResult.rejected(code, response.text("msg"));
    BigDecimal money = amount(response.requireText("money"));
    if (money.compareTo(request.amount()) != 0) throw EPayException.protocol();
    if (request.outRefundNo() != null
        && !request.outRefundNo().equals(response.text("out_refund_no"))) {
      throw EPayException.protocol();
    }
    // 退款提交未声明 out_trade_no 回传；有回传时核对，不能伪造平台返回值。
    String echoedOrder = response.text(key);
    if (echoedOrder != null && !request.orderReference().value().equals(echoedOrder))
      throw EPayException.protocol();
    RefundResult result =
        new RefundResult(
            required(response.text("refund_no")),
            response.text("out_refund_no"),
            required(response.text("trade_no")),
            response.text("out_trade_no"),
            money,
            RefundStatus.ACCEPTED,
            null);
    return GatewayResult.success(code, response.text("msg"), result);
  }

  @Override
  public GatewayResult<RefundResult> queryRefund(ProtocolContext context, RefundReference request) {
    requireRefundCapability();
    Map<String, String> parameters = common(context);
    String key = request.type() == RefundReference.Type.REFUND_NO ? "refund_no" : "out_refund_no";
    parameters.put(key, request.value());
    StrictJson.ObjectValue response = post(context, "refundquery", parameters);
    String code = response.requireText("code");
    if (!code.equals("0")) return GatewayResult.rejected(code, response.text("msg"));
    if (!request.value().equals(response.text(key))) throw EPayException.protocol();
    String rawStatus = required(response.text("status"));
    // 此处是退款查询状态表，与订单查询的支付状态表无关。
    RefundStatus status =
        switch (rawStatus) {
          case "0" -> RefundStatus.FAILED;
          case "1" -> RefundStatus.SUCCEEDED;
          default -> RefundStatus.UNKNOWN;
        };
    RefundResult result =
        new RefundResult(
            required(response.text("refund_no")),
            response.text("out_refund_no"),
            required(response.text("trade_no")),
            required(response.text("out_trade_no")),
            amount(response.requireText("money")),
            status,
            rawStatus);
    return GatewayResult.success(code, response.text("msg"), result);
  }

  private void requireRefundCapability() {
    // XARR 虽注册路由，但文档明确未实现，不发请求也不尝试其他协议。
    if (dialect == Dialect.XARR) throw EPayException.unsupported();
  }

  private OrderStatus orderStatus(String raw) {
    // REFERENCE 的 2 是已退款，不是待支付；未建模的状态保留 UNKNOWN 和原值。
    if (dialect == Dialect.REFERENCE) {
      return switch (raw) {
        case "0" -> OrderStatus.PENDING;
        case "1" -> OrderStatus.PAID;
        default -> OrderStatus.UNKNOWN;
      };
    }
    return switch (raw) {
      case "1" -> OrderStatus.PENDING;
      case "2" -> OrderStatus.PAID;
      default -> OrderStatus.UNKNOWN;
    };
  }

  private static String orderKey(OrderReference reference) {
    return reference.type() == OrderReference.Type.TRADE_NO ? "trade_no" : "out_trade_no";
  }

  private static Map<String, String> common(ProtocolContext context) {
    String timestamp = Long.toString(context.clock().instant().getEpochSecond());
    if (!timestamp.matches("[1-9][0-9]{9}")) throw EPayException.configuration();
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put("pid", context.config().merchantId());
    parameters.put("timestamp", timestamp);
    parameters.put("sign_type", "RSA");
    return parameters;
  }

  private static RsaCredentials credentials(ProtocolContext context) {
    if (context.config().credentials() instanceof RsaCredentials rsa) return rsa;
    throw EPayException.configuration();
  }

  private static Map<String, String> signed(
      ProtocolContext context, Map<String, String> parameters) {
    // 完整业务 wire 先快照再签名，签名后仅附加 sign，发送不可变快照。
    Map<String, String> snapshot = Map.copyOf(parameters);
    Map<String, String> wire = new LinkedHashMap<>(snapshot);
    wire.put("sign", Signatures.rsa(snapshot, credentials(context)));
    return Map.copyOf(wire);
  }

  private StrictJson.ObjectValue post(
      ProtocolContext context, String route, Map<String, String> parameters) {
    StrictJson.ObjectValue response =
        StrictJson.parseObject(
            context.send("POST", "api/pay/" + route, signed(context, parameters)).body());
    Map<String, String> scalars = new LinkedHashMap<>();
    // V2 规范排除数组/对象；它们不受签名保护，后续只能消费显式标量字段。
    response
        .fields()
        .forEach(
            (key, value) -> {
              if (value instanceof StrictJson.Scalar scalar) scalars.put(key, scalar.text());
            });
    verifySignature(context, scalars);
    // V2 秒值在文档/实现中可为字符串或整数；二者签名原文相同，不做数值归一化。
    if (!(response.get("timestamp") instanceof StrictJson.Scalar time)
        || (time.type() != StrictJson.Scalar.Type.STRING
            && time.type() != StrictJson.Scalar.Type.NUMBER)) {
      throw EPayException.protocol();
    }
    // 继续校验十位秒原文，小数、指数、布尔值及复合值均不可冒充时间戳。
    verifyTimestamp(context, time.text(), false);
    return response;
  }

  private static void verifySignature(ProtocolContext context, Map<String, String> parameters) {
    if (!"RSA".equals(parameters.get("sign_type"))
        || !Signatures.verifyRsa(parameters, credentials(context), parameters.get("sign"))) {
      throw EPayException.signature();
    }
  }

  private void verifyTimestamp(ProtocolContext context, String timestamp, boolean notification) {
    if (timestamp == null || !timestamp.matches("[1-9][0-9]{9}")) throw EPayException.protocol();
    Instant instant = Instant.ofEpochSecond(Long.parseLong(timestamp));
    Duration difference = Duration.between(context.clock().instant(), instant);
    if (difference.compareTo(timeTolerance) > 0
        || (!notification && difference.negated().compareTo(timeTolerance) > 0)) {
      throw EPayException.protocol();
    }
  }

  private static String required(String value) {
    if (value == null || value.isBlank()) throw EPayException.protocol();
    return value;
  }

  private static BigDecimal amount(String value) {
    try {
      if (value == null || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?"))
        throw EPayException.protocol();
      return Checks.amount(new BigDecimal(value));
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }
}
