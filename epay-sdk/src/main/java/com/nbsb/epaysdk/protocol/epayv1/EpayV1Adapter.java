package com.nbsb.epaysdk.protocol.epayv1;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.FormCodec;
import com.nbsb.epaysdk.internal.Signatures;
import com.nbsb.epaysdk.internal.StrictJson;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import com.nbsb.epaysdk.spi.ProtocolContext;
import com.nbsb.epaysdk.spi.TransportRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 易支付 V1；方言显式选择，不探测、不降级，也不保存任何商户状态。 */
public final class EpayV1Adapter implements ProtocolAdapter {
  /** 参考实现退款以 0 成功；MPAY 退款以 1 成功，其他接口均只接受 1。 */
  public enum Dialect {
    REFERENCE,
    MPAY
  }

  public static final String REFERENCE_ID = "epay-v1";
  public static final String MPAY_ID = "epay-v1-mpay";

  private final Dialect dialect;

  public EpayV1Adapter() {
    this(Dialect.REFERENCE);
  }

  public EpayV1Adapter(Dialect dialect) {
    if (dialect == null) throw EPayException.configuration();
    this.dialect = dialect;
  }

  @Override
  public String id() {
    return dialect == Dialect.REFERENCE ? REFERENCE_ID : MPAY_ID;
  }

  @Override
  public Set<Capability> capabilities() {
    return Set.of(
        Capability.CREATE_PAYMENT,
        Capability.QUERY_ORDER,
        Capability.VERIFY_NOTIFICATION,
        Capability.REFUND,
        Capability.QUERY_MERCHANT,
        Capability.LIST_ORDERS);
  }

  @Override
  public void validateCredentials(Credentials credentials) {
    if (!(credentials instanceof Md5Credentials)) throw EPayException.configuration();
  }

  @Override
  public GatewayResult<PaymentResult> createPayment(
      ProtocolContext context, PaymentRequest request) {
    Checks.required(request);
    validateOptions(request.options());
    if (!StandardCharsets.UTF_8.newEncoder().canEncode(request.name())
        || request.name().getBytes(StandardCharsets.UTF_8).length > 127) {
      throw EPayException.validation();
    }
    Checks.required(request.notifyUrl());
    boolean form = request.scene() == PaymentScene.BROWSER_FORM;
    if (form) Checks.required(request.returnUrl());
    else {
      Checks.text(request.paymentMethod());
      // 客户 IP 只能由调用方提供；不能用 SDK 所在机器或环回地址冒充。
      Checks.text(request.clientIp());
    }
    Map<String, String> wire = new LinkedHashMap<>();
    wire.put("pid", context.config().merchantId());
    putText(wire, "type", request.paymentMethod());
    wire.put("out_trade_no", request.outTradeNo());
    wire.put("notify_url", request.notifyUrl().toASCIIString());
    if (request.returnUrl() != null) wire.put("return_url", request.returnUrl().toASCIIString());
    wire.put("name", request.name());
    wire.put("money", request.amount().toPlainString());
    putText(wire, "clientip", request.clientIp());
    putText(wire, "device", request.device());
    if (request.param() != null) wire.put("param", request.param());
    wire.put("sign_type", "MD5");
    // 同一个原值参数集合先签名再交给传输编码，不能签名后裁剪业务字段。
    wire.put("sign", Signatures.md5(wire, credentials(context)));
    if (form) {
      PaymentAction action =
          new PaymentAction.Form(FormCodec.buildHtmlForm(context.resolve("submit.php"), wire));
      return GatewayResult.success(
          null, new PaymentResult(null, request.outTradeNo(), List.of(action)));
    }
    StrictJson.ObjectValue json = send(context, "POST", "mapi.php", wire);
    String code = requiredText(json, "code");
    if (!"1".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    try {
      List<PaymentAction> actions = new ArrayList<>();
      String payurl = json.text("payurl");
      String qrcode = json.text("qrcode");
      String scheme = json.text("urlscheme");
      if (present(payurl)) actions.add(new PaymentAction.Redirect(Checks.httpUri(payurl)));
      if (present(qrcode)) actions.add(new PaymentAction.QrCode(qrcode));
      if (present(scheme)) actions.add(new PaymentAction.UrlScheme(scheme));
      if (actions.isEmpty()) throw EPayException.protocol();
      String outTradeNo = json.text("out_trade_no");
      if (outTradeNo != null && !request.outTradeNo().equals(outTradeNo))
        throw EPayException.protocol();
      return GatewayResult.success(
          code,
          json.text("msg"),
          new PaymentResult(json.text("trade_no"), request.outTradeNo(), actions));
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }

  @Override
  public GatewayResult<OrderResult> queryOrder(ProtocolContext context, OrderReference request) {
    Checks.required(request);
    Map<String, String> wire = authenticated(context, "order");
    wire.put(orderField(request), request.value());
    StrictJson.ObjectValue json = send(context, "GET", "api.php", wire);
    String code = requiredText(json, "code");
    if (!"1".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    OrderResult result = order(json, context.config().merchantId());
    String returned =
        request.type() == OrderReference.Type.TRADE_NO ? result.tradeNo() : result.outTradeNo();
    if (!request.value().equals(returned)) throw EPayException.protocol();
    return GatewayResult.success(code, json.text("msg"), result);
  }

  @Override
  public GatewayResult<RefundResult> refund(ProtocolContext context, RefundRequest request) {
    Checks.required(request);
    // V1 没有商户退款号，也没有相应的幂等保证；必须在发送之前拒绝该承诺。
    if (request.outRefundNo() != null) throw EPayException.unsupported();
    Map<String, String> wire = authenticated(context, "refund");
    wire.remove("act");
    wire.put(orderField(request.orderReference()), request.orderReference().value());
    wire.put("money", request.amount().toPlainString());
    // 参考文档将 act 放在 URL，凭据仅放 POST 表单；MPAY 同样接受此形状。
    StrictJson.ObjectValue json =
        StrictJson.parseObject(
            context
                .send(
                    new TransportRequest(
                        "POST",
                        context.resolve("api.php"),
                        wire,
                        context.requestTimeout(),
                        Map.of("act", "refund")))
                .body());
    String code = requiredText(json, "code");
    String successCode = dialect == Dialect.REFERENCE ? "0" : "1";
    if (!successCode.equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    // 成功码只确认受理，不能凭空断言资金已经退回。
    RefundResult result =
        new RefundResult(
            null,
            null,
            wire.get("trade_no"),
            wire.get("out_trade_no"),
            request.amount(),
            RefundStatus.ACCEPTED,
            null);
    return GatewayResult.success(code, json.text("msg"), result);
  }

  @Override
  public GatewayResult<MerchantResult> queryMerchant(ProtocolContext context) {
    StrictJson.ObjectValue json = send(context, "GET", "api.php", authenticated(context, "query"));
    String code = requiredText(json, "code");
    if (!"1".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    String pid = requiredText(json, "pid");
    if (!context.config().merchantId().equals(pid)) throw EPayException.protocol();
    // 网关可能返回 key；只读取业务白名单，绝不复制完整报文或密钥到结果。
    return GatewayResult.success(
        code, new MerchantResult(pid, decimal(requiredText(json, "money")), json.text("active")));
  }

  @Override
  public GatewayResult<OrderListResult> listOrders(
      ProtocolContext context, OrderListRequest request) {
    Checks.required(request);
    if (request.limit() > 50 || request.offset() % request.limit() != 0)
      throw EPayException.validation();
    Map<String, String> wire = authenticated(context, "orders");
    // 用 long 运算避免最大 int 偏移量加一溢出；不把 offset 冒充 page。
    wire.put("page", Long.toString((long) request.offset() / request.limit() + 1));
    wire.put("limit", Integer.toString(request.limit()));
    StrictJson.ObjectValue json = send(context, "GET", "api.php", wire);
    String code = requiredText(json, "code");
    if (!"1".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    List<OrderResult> orders = new ArrayList<>();
    for (StrictJson.Value value : json.array("data").values()) {
      if (!(value instanceof StrictJson.ObjectValue item)) throw EPayException.protocol();
      orders.add(order(item, context.config().merchantId()));
    }
    Long total = null;
    String count = json.text("count");
    if (count != null) {
      if (!count.matches("[0-9]+")) throw EPayException.protocol();
      try {
        total = Long.valueOf(count);
      } catch (NumberFormatException e) {
        throw EPayException.protocol();
      }
    }
    return GatewayResult.success(code, json.text("msg"), new OrderListResult(orders, total));
  }

  @Override
  public GatewayResult<VerifiedNotification> verifyNotification(
      ProtocolContext context, NotificationRequest request) {
    Map<String, String> original = Checks.required(request).parameters();
    // 必须先验原始快照：包括未知新增参数，不经 DTO 重建或白名单过滤。
    if (!Signatures.verifyMd5(original, credentials(context), original.get("sign"))) {
      throw EPayException.signature();
    }
    if (!"MD5".equals(original.get("sign_type"))) throw EPayException.signature();
    String pid = notificationText(original, "pid");
    if (!context.config().merchantId().equals(pid)) throw EPayException.signature();
    String tradeNo = notificationText(original, "trade_no");
    String outTradeNo = notificationText(original, "out_trade_no");
    String rawStatus = notificationText(original, "trade_status");
    BigDecimal amount = positiveAmount(notificationText(original, "money"));
    String paymentMethod = notificationText(original, "type");
    notificationText(original, "name");
    OrderStatus status = "TRADE_SUCCESS".equals(rawStatus) ? OrderStatus.PAID : OrderStatus.UNKNOWN;
    VerifiedNotification verified =
        new VerifiedNotification(
            pid,
            tradeNo,
            outTradeNo,
            amount,
            status,
            rawStatus,
            paymentMethod,
            original.get("param"));
    // 验证通过与支付成功是两回事；非成功交易状态仍是已验证通知，而非业务拒绝。
    return GatewayResult.success(null, verified);
  }

  private Md5Credentials credentials(ProtocolContext context) {
    Credentials value = context.config().credentials();
    validateCredentials(value);
    return (Md5Credentials) value;
  }

  private Map<String, String> authenticated(ProtocolContext context, String act) {
    Map<String, String> wire = new LinkedHashMap<>();
    wire.put("act", act);
    wire.put("pid", context.config().merchantId());
    wire.put("key", credentials(context).secret());
    return wire;
  }

  private static void validateOptions(PaymentOptions options) {
    if (!"web".equals(options.method())
        || options.openId() != null
        || options.authCode() != null
        || options.subAppId() != null
        || options.subOpenId() != null
        || options.isApplet() != null) throw EPayException.unsupported();
  }

  private static void putText(Map<String, String> wire, String key, String value) {
    if (value != null) wire.put(key, Checks.text(value));
  }

  private static String orderField(OrderReference reference) {
    return reference.type() == OrderReference.Type.TRADE_NO ? "trade_no" : "out_trade_no";
  }

  private static StrictJson.ObjectValue send(
      ProtocolContext context, String method, String path, Map<String, String> wire) {
    return StrictJson.parseObject(context.send(method, path, wire).body());
  }

  private static OrderResult order(StrictJson.ObjectValue json, String merchantId) {
    String pid = requiredText(json, "pid");
    if (!merchantId.equals(pid)) throw EPayException.protocol();
    String tradeNo = requiredText(json, "trade_no");
    String outTradeNo = requiredText(json, "out_trade_no");
    String rawStatus = requiredText(json, "status");
    OrderStatus status =
        switch (rawStatus) {
          case "0" -> OrderStatus.PENDING;
          case "1" -> OrderStatus.PAID;
          default -> OrderStatus.UNKNOWN;
        };
    return new OrderResult(
        tradeNo,
        outTradeNo,
        pid,
        positiveAmount(requiredText(json, "money")),
        status,
        rawStatus,
        json.text("type"));
  }

  private static String requiredText(StrictJson.ObjectValue json, String key) {
    String value = json.requireText(key);
    if (value.isBlank()) throw EPayException.protocol();
    return value;
  }

  private static String notificationText(Map<String, String> values, String key) {
    String value = values.get(key);
    if (!present(value)) throw EPayException.protocol();
    return value;
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static BigDecimal decimal(String value) {
    if (value == null || value.length() > 104 || !value.matches("-?[0-9]+(?:\\.[0-9]{1,2})?")) {
      throw EPayException.protocol();
    }
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException e) {
      throw EPayException.protocol();
    }
  }

  private static BigDecimal positiveAmount(String value) {
    try {
      return Checks.amount(decimal(value));
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }
}
