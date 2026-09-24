package com.nbsb.epaysdk.spi;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/** 参数是签名后的不可变原值；GET 编入查询串，POST 编入 UTF-8 表单。 */
public record TransportRequest(
    String method,
    URI uri,
    Map<String, String> parameters,
    Duration timeout,
    Map<String, String> queryParameters) {
  public TransportRequest {
    method = Checks.text(method).toUpperCase(Locale.ROOT);
    if (!method.equals("GET") && !method.equals("POST")) throw EPayException.validation();
    uri = Checks.httpUri(uri);
    if (uri.getRawQuery() != null) throw EPayException.validation();
    parameters = Checks.parameters(parameters);
    queryParameters = Checks.parameters(queryParameters);
    if (queryParameters.keySet().stream().anyMatch(parameters::containsKey)) {
      throw EPayException.validation();
    }
    timeout = Checks.duration(timeout);
  }

  /** 常规请求；额外查询参数仅用于协议规定的路由字段，不能与表单字段重名。 */
  public TransportRequest(
      String method, URI uri, Map<String, String> parameters, Duration timeout) {
    this(method, uri, parameters, timeout, Map.of());
  }

  @Override
  public String toString() {
    return "TransportRequest[method=" + method + ", 其余已遮蔽]";
  }
}
