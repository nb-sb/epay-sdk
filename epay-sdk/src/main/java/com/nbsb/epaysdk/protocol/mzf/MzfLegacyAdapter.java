package com.nbsb.epaysdk.protocol.mzf;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.Signatures;
import com.nbsb.epaysdk.internal.StrictJson;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import com.nbsb.epaysdk.spi.ProtocolContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实验性（experimental）MZF legacy201，仅支付与查单，不声明通知或浏览器表单能力。 契约依据本仓库旧 EPayMZF、MZFExecute、EPayBody 与
 * EPayClientHttpTest；并非完整官方协议。 200 版资料不足，不实施、不探测，不因路径相同就将 1、200、201 混为同一协议。
 */
public final class MzfLegacyAdapter implements ProtocolAdapter {
  public static final String ID = "mzf-legacy201";

  public MzfLegacyAdapter() {}

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Set<Capability> capabilities() {
    return Set.of(Capability.CREATE_PAYMENT, Capability.QUERY_ORDER);
  }

  @Override
  public void validateCredentials(Credentials credentials) {
    if (!(credentials instanceof Md5Credentials)) throw EPayException.configuration();
  }

  @Override
  public GatewayResult<PaymentResult> createPayment(
      ProtocolContext context, PaymentRequest request) {
    Checks.required(request);
    if (request.scene() != PaymentScene.API) throw EPayException.unsupported();
    PaymentOptions options = request.options();
    // 旧源码未发送这些参数；显式拒绝而不是签名后裁剪或悄悄接受 V2 语义。
    if (!"web".equals(options.method())
        || options.openId() != null
        || options.authCode() != null
        || options.subAppId() != null
        || options.subOpenId() != null
        || options.isApplet() != null
        || request.device() != null
        || request.clientIp() != null
        || request.param() != null) throw EPayException.unsupported();
    Checks.text(request.paymentMethod());
    Checks.required(request.notifyUrl());
    Checks.required(request.returnUrl());
    if (!StandardCharsets.UTF_8.newEncoder().canEncode(request.name())
        || request.name().getBytes(StandardCharsets.UTF_8).length > 127)
      throw EPayException.validation();
    Map<String, String> wire = new LinkedHashMap<>();
    wire.put("pid", context.config().merchantId());
    wire.put("type", request.paymentMethod());
    wire.put("out_trade_no", request.outTradeNo());
    wire.put("notify_url", request.notifyUrl().toASCIIString());
    wire.put("return_url", request.returnUrl().toASCIIString());
    wire.put("name", request.name());
    wire.put("money", request.amount().toPlainString());
    wire.put("sign_type", "MD5");
    validateCredentials(context.config().credentials());
    wire.put("sign", Signatures.md5(wire, (Md5Credentials) context.config().credentials()));
    StrictJson.ObjectValue json =
        StrictJson.parseObject(context.send("POST", "pay/apisubmit", wire).body());
    String code = requiredText(json, "code");
    if (!"201".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    try {
      List<PaymentAction> actions = new ArrayList<>();
      String payurl = json.text("payurl");
      String qrcode = json.text("qrcode");
      String scheme = json.text("urlscheme");
      String h5 = json.text("h5_qrurl");
      String image = json.text("code_url");
      if (present(payurl)) actions.add(new PaymentAction.Redirect(Checks.httpUri(payurl)));
      if (present(qrcode)) actions.add(new PaymentAction.QrCode(qrcode));
      if (present(scheme)) actions.add(new PaymentAction.UrlScheme(scheme));
      if (present(h5)) actions.add(new PaymentAction.Redirect(Checks.httpUri(h5)));
      if (present(image)) actions.add(new PaymentAction.QrImage(Checks.httpUri(image)));
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
    validateCredentials(context.config().credentials());
    // 忠实保留旧 MZFExecute 的两个参数；不能凭空增加 pid/key 或易支付的 act。
    Map<String, String> wire =
        Map.of(
            "order_no",
            request.value(),
            "type",
            request.type() == OrderReference.Type.TRADE_NO ? "1" : "2");
    StrictJson.ObjectValue json =
        StrictJson.parseObject(context.send("GET", "pay/chaorder", wire).body());
    String code = requiredText(json, "code");
    // 先判业务码再解 data；拒绝响应允许完全没有 data。
    if (!"201".equals(code)) return GatewayResult.rejected(code, json.text("msg"));
    StrictJson.Value data = json.get("data");
    StrictJson.ObjectValue order;
    if (data instanceof StrictJson.ObjectValue object) order = object;
    else if (data instanceof StrictJson.Scalar scalar
        && scalar.type() == StrictJson.Scalar.Type.STRING) {
      // 旧解析路径接受 JSON 字符串，严格限制为合法对象，不能接受数组或二次字符串包装。
      order = StrictJson.parseObject(scalar.text());
    } else throw EPayException.protocol();
    String tradeNo = order.text("trade_no");
    String outTradeNo = order.text("out_trade_no");
    String returned = request.type() == OrderReference.Type.TRADE_NO ? tradeNo : outTradeNo;
    if (!request.value().equals(returned)) throw EPayException.protocol();
    String pid = order.text("pid");
    if (pid != null && !context.config().merchantId().equals(pid)) throw EPayException.protocol();
    String rawStatus = requiredText(order, "status");
    OrderStatus status =
        switch (rawStatus) {
          case "0" -> OrderStatus.PENDING;
          case "1" -> OrderStatus.PAID;
          default -> OrderStatus.UNKNOWN;
        };
    // 缺少金额不能据单个状态值确认订单；也不以请求商户号伪造网关返回的归属。
    BigDecimal amount = amount(requiredText(order, "money"));
    return GatewayResult.success(
        code,
        json.text("msg"),
        new OrderResult(tradeNo, outTradeNo, pid, amount, status, rawStatus, order.text("type")));
  }

  @Override
  public GatewayResult<VerifiedNotification> verifyNotification(
      ProtocolContext context, NotificationRequest request) {
    // legacy 仅支付、查单：没有可信通知契约，不把易支付 MD5 通知规则冒充为 MZF 规则。
    throw EPayException.unsupported();
  }

  private static boolean present(String value) {
    return value != null && !value.isBlank();
  }

  private static String requiredText(StrictJson.ObjectValue json, String key) {
    String value = json.requireText(key);
    if (value.isBlank()) throw EPayException.protocol();
    return value;
  }

  private static BigDecimal amount(String value) {
    if (value.length() > 104 || !value.matches("[0-9]+(?:\\.[0-9]{1,2})?"))
      throw EPayException.protocol();
    try {
      return Checks.amount(new BigDecimal(value));
    } catch (RuntimeException e) {
      throw EPayException.protocol();
    }
  }
}
