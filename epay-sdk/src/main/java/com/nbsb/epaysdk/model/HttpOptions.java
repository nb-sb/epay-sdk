package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.time.Duration;

/** 租借、连接、读取均受单次请求总预算约束；所有超时必须为正且不超过一天。 */
public record HttpOptions(
    Duration connectTimeout,
    Duration connectionRequestTimeout,
    Duration responseTimeout,
    Duration requestTimeout) {
  public HttpOptions {
    connectTimeout = Checks.duration(connectTimeout);
    connectionRequestTimeout = Checks.duration(connectionRequestTimeout);
    responseTimeout = Checks.duration(responseTimeout);
    requestTimeout = Checks.duration(requestTimeout);
  }

  public static HttpOptions defaults() {
    return new HttpOptions(
        Duration.ofSeconds(5),
        Duration.ofSeconds(5),
        Duration.ofSeconds(15),
        Duration.ofSeconds(20));
  }
}
