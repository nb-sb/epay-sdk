package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.OrderResult;
import com.nbsb.epaysdk.model.OrderStatus;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** 独立的同步订单轮询工具，借用客户端而不改变其配置或生命周期。 */
public final class OrderPoller {
  private static final long MIN_REQUEST_NANOS = Duration.ofMillis(1).toNanos();

  private OrderPoller() {}

  /**
   * 仅对 PENDING 和明确可重试的读传输故障继续查询，其余结果原样返回。 timeout 与 interval 均须为 1 毫秒至一天，maxAttempts 为 1 至
   * 1,000,000（包含首次查询）。非法 timeout、interval 与 maxAttempts 属于参数错误，不是商户配置错误。 总预算包含查询及间隔等待；不足 1
   * 毫秒时不再发起请求。 超时或次数耗尽抛 TIMEOUT；中断保留标志并抛不可重试的 TRANSPORT。 自定义传输须遵守请求预算；无法强制打断它，但不会接受其过期结果。
   */
  public static GatewayResult<OrderResult> awaitPaid(
      EPayClient client,
      OrderReference reference,
      Duration timeout,
      Duration interval,
      int maxAttempts) {
    return awaitPaid(
        client,
        reference,
        timeout,
        interval,
        maxAttempts,
        System::nanoTime,
        TimeUnit.NANOSECONDS::sleep);
  }

  static GatewayResult<OrderResult> awaitPaid(
      EPayClient client,
      OrderReference reference,
      Duration timeout,
      Duration interval,
      int maxAttempts,
      LongSupplier nanoTime,
      Sleeper sleeper) {
    Checks.required(client);
    Checks.required(reference);
    Checks.requestDuration(timeout);
    Checks.requestDuration(interval);
    if (maxAttempts < 1 || maxAttempts > 1_000_000) throw EPayException.validation();
    Checks.required(nanoTime);
    Checks.required(sleeper);
    long started = nanoTime.getAsLong();
    long budget = timeout.toNanos();
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      checkInterrupted();
      long remaining = budget - (nanoTime.getAsLong() - started);
      if (remaining < MIN_REQUEST_NANOS) throw EPayException.timeout();
      GatewayResult<OrderResult> result = null;
      try {
        result = client.queryOrder(reference, Duration.ofNanos(remaining));
      } catch (EPayException error) {
        checkInterrupted();
        if (error.kind() != EPayException.Kind.TRANSPORT || !error.retryable()) throw error;
      }
      checkInterrupted();
      remaining = budget - (nanoTime.getAsLong() - started);
      if (remaining <= 0) throw EPayException.timeout();
      if (result != null && (!result.success() || result.data().status() != OrderStatus.PENDING)) {
        return result;
      }
      if (attempt + 1 == maxAttempts || remaining < MIN_REQUEST_NANOS)
        throw EPayException.timeout();
      try {
        sleeper.sleep(Math.min(interval.toNanos(), remaining));
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw EPayException.transport(false);
      }
    }
    throw EPayException.timeout();
  }

  private static void checkInterrupted() {
    if (Thread.currentThread().isInterrupted()) throw EPayException.transport(false);
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long nanos) throws InterruptedException;
  }
}
