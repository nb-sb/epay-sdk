package com.nbsb.epaysdk.spi;

import com.nbsb.epaysdk.api.EPayException;

/** 响应体可包含敏感数据，只允许协议内部显式读取。 */
public record TransportResponse(int status, String body) {
  public TransportResponse {
    if (status < 100 || status > 599 || body == null) throw EPayException.protocol();
  }

  @Override
  public String toString() {
    return "TransportResponse[status=" + status + ", body=已遮蔽]";
  }
}
