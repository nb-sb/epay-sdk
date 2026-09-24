package com.nbsb.epaysdk.internal;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.HttpOptions;
import com.nbsb.epaysdk.spi.HttpTransport;
import com.nbsb.epaysdk.spi.TransportRequest;
import com.nbsb.epaysdk.spi.TransportResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLException;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

/** Apache 5.5.1 传输：有界并发、整体等待预算、有限响应、无自动重试或重定向。 */
public final class ApacheHttpTransport implements HttpTransport {
  public static final int MAX_RESPONSE_BYTES = 1024 * 1024;
  private final CloseableHttpClient client;
  private final HttpOptions options;
  private final ExecutorService workers;
  private final Set<HttpUriRequestBase> active = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean closed = new AtomicBoolean();

  public ApacheHttpTransport() {
    this(HttpOptions.defaults());
  }

  public ApacheHttpTransport(HttpOptions options) {
    if (options == null) throw EPayException.configuration();
    this.options = options;
    try {
      client =
          HttpClients.custom()
              .disableAutomaticRetries()
              .disableRedirectHandling()
              .disableCookieManagement()
              .build();
      AtomicInteger sequence = new AtomicInteger();
      workers =
          new ThreadPoolExecutor(
              0,
              32,
              30,
              TimeUnit.SECONDS,
              new SynchronousQueue<>(),
              task -> {
                Thread thread = new Thread(task, "epay-http-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
              },
              new ThreadPoolExecutor.AbortPolicy());
    } catch (RuntimeException e) {
      throw EPayException.configuration();
    }
  }

  @Override
  public TransportResponse execute(TransportRequest request) {
    if (closed.get()) throw EPayException.closed();
    Checks.required(request);
    if (Thread.currentThread().isInterrupted()) throw EPayException.transport(false);
    long started = System.nanoTime();
    long budget = request.timeout().toNanos();
    HttpUriRequestBase wire = buildRequest(request);
    active.add(wire);
    Future<TransportResponse> pending = null;
    try {
      if (closed.get()) throw EPayException.closed();
      pending = workers.submit(() -> exchange(wire));
      long remaining = budget - (System.nanoTime() - started);
      if (remaining <= 0) throw new TimeoutException();
      return pending.get(remaining, TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw EPayException.transport(false);
    } catch (TimeoutException e) {
      throw EPayException.transport(true);
    } catch (RejectedExecutionException e) {
      throw closed.get() ? EPayException.closed() : EPayException.transport(true);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof EPayException safe) throw safe.withExecutionUncertain(false);
      throw EPayException.transport(false);
    } finally {
      // 取消底层请求，避免调用者已超时而连接仍继续占用；DNS 等系统调用只能尽力中断。
      if (pending == null || !pending.isDone()) {
        wire.cancel();
        if (pending != null) pending.cancel(true);
      }
      active.remove(wire);
    }
  }

  @SuppressWarnings("deprecation")
  private HttpUriRequestBase buildRequest(TransportRequest request) {
    try {
      String encoded = FormCodec.encode(request.parameters());
      URI uri = request.uri();
      String query = FormCodec.encode(request.queryParameters());
      if (request.method().equals("GET") && !encoded.isEmpty()) {
        query = query.isEmpty() ? encoded : query + "&" + encoded;
      }
      if (!query.isEmpty()) uri = URI.create(uri.toASCIIString() + "?" + query);
      HttpUriRequestBase wire = new HttpUriRequestBase(request.method(), uri);
      wire.setConfig(
          RequestConfig.custom()
              .setConnectionRequestTimeout(
                  timeout(options.connectionRequestTimeout(), request.timeout()))
              .setConnectTimeout(timeout(options.connectTimeout(), request.timeout()))
              .setResponseTimeout(timeout(options.responseTimeout(), request.timeout()))
              .setRedirectsEnabled(false)
              .build());
      if (request.method().equals("POST")) {
        wire.setEntity(
            new StringEntity(
                encoded,
                ContentType.create("application/x-www-form-urlencoded", StandardCharsets.UTF_8)));
      }
      wire.setHeader("Accept", "application/json, text/plain;q=0.9");
      return wire;
    } catch (RuntimeException e) {
      throw EPayException.validation();
    }
  }

  @SuppressWarnings("deprecation")
  private TransportResponse exchange(HttpUriRequestBase request) {
    try (CloseableHttpResponse response = client.execute(request)) {
      HttpEntity entity = response.getEntity();
      if (entity == null) return new TransportResponse(response.getCode(), "");
      if (entity.getContentLength() > MAX_RESPONSE_BYTES) {
        request.cancel();
        throw EPayException.protocol();
      }
      byte[] bytes;
      try (InputStream input = entity.getContent()) {
        bytes = input.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) {
          request.cancel();
          throw EPayException.protocol();
        }
      }
      String body =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
      return new TransportResponse(response.getCode(), body);
    } catch (EPayException e) {
      throw e.withExecutionUncertain(false);
    } catch (java.nio.charset.CharacterCodingException e) {
      throw EPayException.protocol();
    } catch (SSLException e) {
      throw EPayException.transport(false);
    } catch (IOException e) {
      throw EPayException.transport(true);
    } catch (RuntimeException e) {
      throw EPayException.transport(false);
    }
  }

  private static Timeout timeout(Duration configured, Duration budget) {
    return Timeout.ofMilliseconds(Math.max(1, Math.min(configured.toMillis(), budget.toMillis())));
  }

  @Override
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    active.forEach(HttpUriRequestBase::cancel);
    workers.shutdownNow();
    try {
      client.close();
    } catch (IOException | RuntimeException e) {
      throw EPayException.transport(false);
    }
  }

  @Override
  public String toString() {
    return "ApacheHttpTransport[已遮蔽]";
  }
}
