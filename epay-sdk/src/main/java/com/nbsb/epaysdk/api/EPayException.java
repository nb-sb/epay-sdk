package com.nbsb.epaysdk.api;

/** 稳定的错误分类；消息仅由分类生成，不接受报文、密钥或原始异常链。 */
public final class EPayException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public enum Kind {
    CONFIGURATION,
    VALIDATION,
    PROTOCOL,
    SIGNATURE,
    TRANSPORT,
    UNSUPPORTED,
    CLOSED,
    TIMEOUT
  }

  private final Kind kind;
  private final boolean executionUncertain;
  private final boolean retryable;

  public EPayException(Kind kind) {
    this(kind, false, false);
  }

  public EPayException(Kind kind, boolean executionUncertain, boolean retryable) {
    super(message(kind), null, false, true);
    this.kind = kind == null ? Kind.CONFIGURATION : kind;
    this.executionUncertain = executionUncertain;
    this.retryable = kind == Kind.TRANSPORT && retryable;
  }

  public Kind kind() {
    return kind;
  }

  public boolean executionUncertain() {
    return executionUncertain;
  }

  public boolean retryable() {
    return retryable;
  }

  public EPayException withExecutionUncertain(boolean uncertain) {
    return new EPayException(kind, executionUncertain || uncertain, retryable);
  }

  public static EPayException configuration() {
    return new EPayException(Kind.CONFIGURATION);
  }

  public static EPayException validation() {
    return new EPayException(Kind.VALIDATION);
  }

  public static EPayException protocol() {
    return new EPayException(Kind.PROTOCOL);
  }

  public static EPayException signature() {
    return new EPayException(Kind.SIGNATURE);
  }

  public static EPayException transport(boolean retryable) {
    return new EPayException(Kind.TRANSPORT, false, retryable);
  }

  public static EPayException unsupported() {
    return new EPayException(Kind.UNSUPPORTED);
  }

  public static EPayException closed() {
    return new EPayException(Kind.CLOSED);
  }

  public static EPayException timeout() {
    return new EPayException(Kind.TIMEOUT);
  }

  private static String message(Kind kind) {
    if (kind == null) return "SDK 错误分类缺失";
    return switch (kind) {
      case CONFIGURATION -> "商户或客户端配置无效";
      case VALIDATION -> "业务请求参数无效";
      case PROTOCOL -> "网关响应不符合协议契约";
      case SIGNATURE -> "签名生成或验证失败";
      case TRANSPORT -> "网关传输失败";
      case UNSUPPORTED -> "当前协议或站点未启用此能力";
      case CLOSED -> "客户端或传输已关闭";
      case TIMEOUT -> "订单轮询等待超时";
    };
  }
}
