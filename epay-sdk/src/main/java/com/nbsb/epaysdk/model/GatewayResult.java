package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;

/** 业务拒绝是正常结果；本地和信任链错误使用异常，不伪造网关业务码。 */
public record GatewayResult<T>(boolean success, String code, String message, T data) {
  public GatewayResult {
    if (success && data == null) throw EPayException.protocol();
    if (!success && data != null) throw EPayException.protocol();
  }

  public static <T> GatewayResult<T> success(String code, T data) {
    return new GatewayResult<>(true, code, null, data);
  }

  public static <T> GatewayResult<T> success(String code, String message, T data) {
    return new GatewayResult<>(true, code, message, data);
  }

  public static <T> GatewayResult<T> rejected(String code, String message) {
    return new GatewayResult<>(false, code, message, null);
  }

  @Override
  public String toString() {
    return "GatewayResult[success=" + success + ", 其余已遮蔽]";
  }
}
