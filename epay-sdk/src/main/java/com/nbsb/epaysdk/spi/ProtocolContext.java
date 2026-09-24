package com.nbsb.epaysdk.spi;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.OperationBudget;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.MerchantConfig;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单次操作上下文，只处理地址、预算和传输，不含协议业务分支。 */
public final class ProtocolContext {
  private final MerchantConfig config;
  private final HttpTransport transport;
  private final Clock clock;
  private final Duration requestTimeout;
  private final AtomicBoolean requestAttempted = new AtomicBoolean();

  public ProtocolContext(
      MerchantConfig config, HttpTransport transport, Clock clock, Duration requestTimeout) {
    this(config, transport, clock, requestTimeout, System.nanoTime());
  }

  /** startedNanos 与客户端加锁前的起点相同，避免把等锁时间排除在请求预算之外。 */
  public ProtocolContext(
      MerchantConfig config,
      HttpTransport transport,
      Clock clock,
      Duration requestTimeout,
      long startedNanos) {
    this.config = Checks.required(config);
    this.clock = Checks.required(clock);
    this.requestTimeout = Checks.duration(requestTimeout);
    HttpTransport delegate = Checks.required(transport);
    OperationBudget budget = new OperationBudget(requestTimeout, startedNanos);
    this.transport =
        request -> {
          Checks.required(request);
          TransportRequest bounded =
              new TransportRequest(
                  request.method(),
                  request.uri(),
                  request.parameters(),
                  budget.transmissionTimeout(request.timeout()),
                  request.queryParameters());
          budget.checkCanSend();
          requestAttempted.set(true);
          try {
            TransportResponse response = delegate.execute(bounded);
            if (response == null) throw EPayException.protocol();
            budget.check();
            return response;
          } catch (EPayException e) {
            throw e.withExecutionUncertain(false);
          } catch (RuntimeException e) {
            throw EPayException.transport(false);
          }
        };
  }

  public MerchantConfig config() {
    return config;
  }

  public HttpTransport transport() {
    return transport;
  }

  public Clock clock() {
    return clock;
  }

  public Duration requestTimeout() {
    return requestTimeout;
  }

  public boolean requestAttempted() {
    return requestAttempted.get();
  }

  /** 单个前导斜杠仍表示部署前缀之下；拒绝绝对地址、越级和歧义路径。 */
  public URI resolve(String path) {
    try {
      Checks.text(path);
      if (path.startsWith("//") || path.contains("\\")) {
        throw EPayException.configuration();
      }
      String relative = path.startsWith("/") ? path.substring(1) : path;
      URI uri = Checks.safePath(URI.create(relative));
      if (uri.isAbsolute()
          || uri.getRawAuthority() != null
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !uri.equals(uri.normalize())
          || relative.isEmpty()) throw EPayException.configuration();
      return config.baseUrl().resolve(uri);
    } catch (RuntimeException e) {
      throw EPayException.configuration();
    }
  }

  public TransportResponse send(String method, String path, Map<String, String> parameters) {
    return send(new TransportRequest(method, resolve(path), parameters, requestTimeout));
  }

  public TransportResponse send(TransportRequest request) {
    TransportResponse response = transport.execute(request);
    if (response.status() < 200 || response.status() >= 300) {
      throw EPayException.transport(
          response.status() == 408 || response.status() == 429 || response.status() >= 500);
    }
    return response;
  }

  @Override
  public String toString() {
    return "ProtocolContext[已遮蔽]";
  }
}
