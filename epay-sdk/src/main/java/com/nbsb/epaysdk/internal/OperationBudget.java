package com.nbsb.epaysdk.internal;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public final class OperationBudget {
  private static final long MIN_REQUEST_NANOS = TimeUnit.MILLISECONDS.toNanos(1);
  private final long deadline;

  public OperationBudget(Duration timeout, long startedNanos) {
    deadline = startedNanos + Checks.duration(timeout).toNanos();
  }

  public long remainingNanos() {
    if (Thread.currentThread().isInterrupted()) throw EPayException.transport(false);
    // 预算至多一天，使用差值比较兼容 nanoTime 的负值与回绕。
    long remaining = deadline - System.nanoTime();
    if (remaining <= 0) throw EPayException.transport(true);
    return remaining;
  }

  public void check() {
    remainingNanos();
  }

  public void checkCanSend() {
    if (remainingNanos() < MIN_REQUEST_NANOS) throw EPayException.transport(true);
  }

  public Duration transmissionTimeout(Duration requested) {
    long remaining = Math.min(Checks.duration(requested).toNanos(), remainingNanos());
    if (remaining < MIN_REQUEST_NANOS) throw EPayException.transport(true);
    return Duration.ofNanos(remaining);
  }
}
