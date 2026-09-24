package com.nbsb.epaysdk.api;

import static org.junit.jupiter.api.Assertions.*;

import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.spi.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** 通过客户端与传输接缝验证轮询，不连接真实支付网关。 */
class OrderPollerTest {
  private static final OrderReference REFERENCE = OrderReference.byOutTradeNo("private-order");
  private static final Duration SECOND = Duration.ofSeconds(1);

  @Test
  void queryBudgetReachesTransportIsCappedAndDoesNotChangeDefault() {
    List<Duration> budgets = new ArrayList<>();
    try (EPay client =
        client(
            request -> {
              budgets.add(request.timeout());
              assertEquals("private-order", request.parameters().get("id"));
              return new TransportResponse(200, "PAID");
            })) {
      assertEquals(
          OrderStatus.PAID, client.queryOrder(REFERENCE, Duration.ofSeconds(2)).data().status());
      client.queryOrder(REFERENCE, Duration.ofSeconds(20));
      client.queryOrder(REFERENCE);
      assertBudgetsWithin(
          budgets, Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofSeconds(5));
      assertTrue(budgets.get(2).compareTo(Duration.ofSeconds(2)) > 0);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PAID", "CLOSED", "FAILED", "UNKNOWN", "DENIED"})
  void terminalResultsAndRejectionReturnImmediatelyWithoutLosingData(String status) {
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              return new TransportResponse(200, status);
            })) {
      GatewayResult<OrderResult> result =
          OrderPoller.awaitPaid(client, REFERENCE, Duration.ofMinutes(1), SECOND, 3);
      assertEquals(1, sent.size());
      assertEquals("private-message", result.message());
      if (status.equals("DENIED")) {
        assertFalse(result.success());
        assertEquals("DENIED", result.code());
        assertNull(result.data());
      } else {
        assertTrue(result.success());
        assertEquals("OK", result.code());
        assertEquals(OrderStatus.valueOf(status), result.data().status());
        assertEquals(status, result.data().rawStatus());
        assertEquals("private-order", result.data().outTradeNo());
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pendingAndExplicitlyRetryableReadFailuresUseRemainingBudget(boolean transportFailure) {
    ManualTime time = new ManualTime();
    List<Duration> budgets = new ArrayList<>();
    Thread caller = Thread.currentThread();
    try (EPayClient client =
        client(
            request -> {
              assertSame(caller, Thread.currentThread());
              budgets.add(request.timeout());
              time.advance(SECOND);
              if (budgets.size() == 1) {
                time.advance(SECOND);
                if (transportFailure) throw EPayException.transport(true);
                return new TransportResponse(200, "PENDING");
              }
              return new TransportResponse(200, "PAID");
            })) {
      GatewayResult<OrderResult> result =
          OrderPoller.awaitPaid(
              client,
              REFERENCE,
              Duration.ofSeconds(8),
              Duration.ofSeconds(2),
              3,
              time,
              time::sleep);
      assertEquals(OrderStatus.PAID, result.data().status());
      assertBudgetsWithin(budgets, Duration.ofSeconds(5), Duration.ofSeconds(4));
      assertEquals(List.of(Duration.ofSeconds(2)), time.sleeps);
    }
  }

  @ParameterizedTest
  @CsvSource({"PENDING,1", "PENDING,3", "RETRY,1", "RETRY,3"})
  void attemptLimitIncludesFailedReadsAndDoesNotSleepAfterLastAttempt(String status, int limit) {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              if (sent.size() > limit) return new TransportResponse(200, "PAID");
              if (status.equals("RETRY")) throw EPayException.transport(true);
              return new TransportResponse(200, status);
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client, REFERENCE, Duration.ofMinutes(1), SECOND, limit, time, time::sleep)));
      assertEquals(limit, sent.size());
      assertEquals(limit - 1, time.sleeps.size());
      assertEquals(OrderStatus.PAID, client.queryOrder(REFERENCE).data().status());
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PAID", "CLOSED", "FAILED", "UNKNOWN", "DENIED", "PENDING", "RETRY"})
  void aTransportThatReturnsAtOrAfterDeadlineCannotProduceAResult(String status) {
    ManualTime time = new ManualTime();
    List<Duration> budgets = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              budgets.add(request.timeout());
              time.advance(Duration.ofSeconds(10));
              if (status.equals("RETRY")) throw EPayException.transport(true);
              return new TransportResponse(200, status);
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client, REFERENCE, Duration.ofSeconds(10), SECOND, 3, time, time::sleep)));
      assertBudgetsWithin(budgets, Duration.ofSeconds(5));
      assertTrue(time.sleeps.isEmpty());
    }
  }

  @Test
  void sleepIsCappedToRemainingBudgetAndNoRequestStartsAfterDeadline() {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              time.advance(Duration.ofSeconds(2));
              return new TransportResponse(200, "PENDING");
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client,
                      REFERENCE,
                      Duration.ofSeconds(5),
                      Duration.ofSeconds(20),
                      3,
                      time,
                      time::sleep)));
      assertEquals(List.of(Duration.ofSeconds(3)), time.sleeps);
      assertEquals(1, sent.size());
    }
  }

  @Test
  void subMillisecondRemainderIsNeverRoundedUpIntoANewRequest() {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              time.advance(Duration.ofNanos(999_500_000));
              return new TransportResponse(200, "PENDING");
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(client, REFERENCE, SECOND, SECOND, 3, time, time::sleep)));
      assertEquals(1, sent.size());
      assertTrue(time.sleeps.isEmpty());
    }
  }

  @ParameterizedTest
  @EnumSource(value = EPayException.Kind.class, mode = EnumSource.Mode.EXCLUDE, names = "TRANSPORT")
  void nonTransportFailuresAreNeverRetried(EPayException.Kind kind) {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              throw new EPayException(kind, false, true);
            })) {
      EPayException error =
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client, REFERENCE, Duration.ofMinutes(1), SECOND, 3, time, time::sleep));
      assertEquals(kind, error.kind());
      assertFalse(error.retryable());
      assertEquals(1, sent.size());
      assertTrue(time.sleeps.isEmpty());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void unclassifiedOrNonRetryableTransportFailuresAreNeverRetried(boolean unclassified) {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              if (unclassified) throw new IllegalStateException("private-secret");
              throw EPayException.transport(false);
            })) {
      EPayException error =
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client, REFERENCE, Duration.ofMinutes(1), SECOND, 3, time, time::sleep));
      assertEquals(EPayException.Kind.TRANSPORT, error.kind());
      assertFalse(error.retryable());
      assertNull(error.getCause());
      assertEquals(1, sent.size());
      assertTrue(time.sleeps.isEmpty());
    }
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void invalidPollingDurationsAreRejectedBeforeQuery(Duration invalid) {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不应发送请求");
            })) {
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(
                  EPayException.class,
                  () -> OrderPoller.awaitPaid(client, REFERENCE, invalid, SECOND, 1))
              .kind());
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(
                  EPayException.class,
                  () -> OrderPoller.awaitPaid(client, REFERENCE, SECOND, invalid, 1))
              .kind());
    }
  }

  @ParameterizedTest
  @MethodSource("invalidDurations")
  void invalidQueryOverridesAreRejectedBeforeQuery(Duration invalid) {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不应发送请求");
            })) {
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(EPayException.class, () -> client.queryOrder(REFERENCE, invalid)).kind());
    }
  }

  private static Stream<Duration> invalidDurations() {
    return Stream.of(
        null,
        Duration.ZERO,
        Duration.ofNanos(-1),
        Duration.ofSeconds(-1),
        Duration.ofNanos(999_999),
        Duration.ofDays(1).plusNanos(1),
        Duration.ofSeconds(Long.MAX_VALUE));
  }

  @ParameterizedTest
  @ValueSource(ints = {0, -1, 1_000_001, Integer.MAX_VALUE})
  void invalidAttemptLimitsAreRejectedBeforeQuery(int limit) {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不应发送请求");
            })) {
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(
                  EPayException.class,
                  () -> OrderPoller.awaitPaid(client, REFERENCE, SECOND, SECOND, limit))
              .kind());
    }
  }

  @Test
  void nullClientAndReferencesAreRejectedBeforeQuery() {
    assertEquals(
        EPayException.Kind.VALIDATION,
        assertThrows(
                EPayException.class,
                () -> OrderPoller.awaitPaid(null, REFERENCE, SECOND, SECOND, 1))
            .kind());
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不应发送请求");
            })) {
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(
                  EPayException.class, () -> OrderPoller.awaitPaid(client, null, SECOND, SECOND, 1))
              .kind());
      assertEquals(
          EPayException.Kind.VALIDATION,
          assertThrows(EPayException.class, () -> client.queryOrder(null, SECOND)).kind());
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {1, 86_400_000})
  void inclusiveDurationBoundsValidateButSubMillisecondRemainderNeverSends(long millis) {
    ManualTime time = new ManualTime();
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        client(
            request -> {
              sent.incrementAndGet();
              assertBudgetsWithin(
                  List.of(request.timeout()), Duration.ofMillis(Math.min(millis, 5000)));
              return new TransportResponse(200, "PAID");
            })) {
      Duration duration = Duration.ofMillis(millis);
      if (millis == 1) {
        EPayException error =
            assertThrows(EPayException.class, () -> client.queryOrder(REFERENCE, duration));
        assertEquals(EPayException.Kind.TRANSPORT, error.kind());
        assertTrue(error.retryable());
        assertTimeoutFailure(
            assertThrows(
                EPayException.class,
                () ->
                    OrderPoller.awaitPaid(
                        client, REFERENCE, duration, duration, 1_000_000, time, time::sleep)));
        assertEquals(0, sent.get());
      } else {
        assertEquals(
            OrderStatus.PAID,
            OrderPoller.awaitPaid(
                    client, REFERENCE, duration, duration, 1_000_000, time, time::sleep)
                .data()
                .status());
        assertEquals(OrderStatus.PAID, client.queryOrder(REFERENCE, duration).data().status());
      }
    }
  }

  @Test
  void alreadyInterruptedCallerDoesNotQueryAndKeepsFlag() {
    try (EPayClient client =
        client(
            request -> {
              throw new AssertionError("不应发送请求");
            })) {
      Thread.currentThread().interrupt();
      try {
        assertInterruptedFailure(
            assertThrows(
                EPayException.class,
                () -> OrderPoller.awaitPaid(client, REFERENCE, SECOND, SECOND, 3)));
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Test
  void interruptedSleepRestoresFlagAndDoesNotQueryAgain() {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              return new TransportResponse(200, "PENDING");
            })) {
      try {
        assertInterruptedFailure(
            assertThrows(
                EPayException.class,
                () ->
                    OrderPoller.awaitPaid(
                        client,
                        REFERENCE,
                        Duration.ofSeconds(10),
                        SECOND,
                        3,
                        time,
                        nanos -> {
                          throw new InterruptedException("private-secret");
                        })));
        assertEquals(1, sent.size());
      } finally {
        Thread.interrupted();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"PAID", "PENDING", "DENIED", "RETRY"})
  void interruptDuringQueryPreventsReturningOrRetrying(String status) {
    ManualTime time = new ManualTime();
    List<TransportRequest> sent = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              sent.add(request);
              Thread.currentThread().interrupt();
              if (status.equals("RETRY")) throw EPayException.transport(true);
              return new TransportResponse(200, status);
            })) {
      try {
        assertInterruptedFailure(
            assertThrows(
                EPayException.class,
                () ->
                    OrderPoller.awaitPaid(
                        client, REFERENCE, Duration.ofSeconds(10), SECOND, 3, time, time::sleep)));
        assertEquals(1, sent.size());
        assertTrue(time.sleeps.isEmpty());
      } finally {
        Thread.interrupted();
      }
    }
  }

  @ParameterizedTest
  @ValueSource(longs = {-5_000_000_000L, Long.MAX_VALUE - 1_000_000_000L})
  void monotonicElapsedBudgetSurvivesNegativeValuesAndNanoTimeWrap(long initialNanos) {
    ManualTime time = new ManualTime();
    time.nanos = initialNanos;
    List<Duration> budgets = new ArrayList<>();
    try (EPayClient client =
        client(
            request -> {
              budgets.add(request.timeout());
              time.advance(SECOND);
              return new TransportResponse(200, budgets.size() == 1 ? "PENDING" : "PAID");
            })) {
      assertEquals(
          OrderStatus.PAID,
          OrderPoller.awaitPaid(
                  client, REFERENCE, Duration.ofSeconds(5), SECOND, 2, time, time::sleep)
              .data()
              .status());
      assertBudgetsWithin(budgets, Duration.ofSeconds(5), Duration.ofSeconds(3));
    }
  }

  @Test
  void oversleepDoesNotStartAnotherQuery() {
    ManualTime time = new ManualTime();
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        client(
            request -> {
              sent.incrementAndGet();
              return new TransportResponse(200, "PENDING");
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () ->
                  OrderPoller.awaitPaid(
                      client,
                      REFERENCE,
                      Duration.ofSeconds(5),
                      SECOND,
                      3,
                      time,
                      delay -> time.advance(Duration.ofSeconds(6)))));
      assertEquals(1, sent.get());
    }
  }

  @Test
  void lastAttemptCanReturnPaidWithLessThanOneMillisecondRemaining() {
    ManualTime time = new ManualTime();
    try (EPayClient client =
        client(
            request -> {
              time.advance(Duration.ofNanos(999_500_000));
              return new TransportResponse(200, "PAID");
            })) {
      assertEquals(
          OrderStatus.PAID,
          OrderPoller.awaitPaid(client, REFERENCE, SECOND, SECOND, 1, time, time::sleep)
              .data()
              .status());
    }
  }

  @Test
  void publicEntryRejectsRealLateSuccessWithoutTinyTimingThresholds() {
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        client(
            request -> {
              sent.incrementAndGet();
              try {
                // 故意超过实际收到的预算；不对机器调度速度设苛刻的上界断言。
                TimeUnit.NANOSECONDS.sleep(request.timeout().plus(SECOND).toNanos());
              } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw EPayException.transport(false);
              }
              return new TransportResponse(200, "PAID");
            })) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () -> OrderPoller.awaitPaid(client, REFERENCE, Duration.ofSeconds(2), SECOND, 3)));
      assertEquals(1, sent.get());
    }
  }

  @Test
  void publicEntrySleepsOnCallerThreadAndLeavesBorrowedClientAndTransportOpen() {
    AtomicInteger sent = new AtomicInteger();
    AtomicInteger closed = new AtomicInteger();
    Thread caller = Thread.currentThread();
    HttpTransport transport =
        new HttpTransport() {
          @Override
          public TransportResponse execute(TransportRequest request) {
            assertSame(caller, Thread.currentThread());
            return new TransportResponse(200, sent.incrementAndGet() == 1 ? "PENDING" : "PAID");
          }

          @Override
          public void close() {
            closed.incrementAndGet();
          }
        };
    try (EPayClient client = client(transport)) {
      assertEquals(
          OrderStatus.PAID,
          OrderPoller.awaitPaid(client, REFERENCE, Duration.ofMinutes(1), Duration.ofMillis(20), 2)
              .data()
              .status());
      assertEquals(2, sent.get());
      assertEquals(0, closed.get());
      assertEquals(OrderStatus.PAID, client.queryOrder(REFERENCE).data().status());
    }
    assertEquals(0, closed.get());
  }

  @Test
  void pollingAccountsForQueryPreprocessingBeforeAnyTransmission() {
    AtomicInteger sent = new AtomicInteger();
    QueryAdapter adapter =
        new QueryAdapter() {
          @Override
          public GatewayResult<OrderResult> queryOrder(
              ProtocolContext context, OrderReference request) {
            pause(context.requestTimeout().plusMillis(50));
            return super.queryOrder(context, request);
          }
        };
    try (EPayClient client =
        client(
            request -> {
              sent.incrementAndGet();
              return new TransportResponse(200, "PAID");
            },
            adapter,
            Duration.ofSeconds(5))) {
      assertTimeoutFailure(
          assertThrows(
              EPayException.class,
              () -> OrderPoller.awaitPaid(client, REFERENCE, Duration.ofMillis(100), SECOND, 3)));
      assertEquals(0, sent.get());
    }
  }

  @Test
  void pollingRetriesAnExpiredReadInsteadOfAcceptingItsLatePaidResult() {
    ManualTime time = new ManualTime();
    AtomicInteger sent = new AtomicInteger();
    try (EPayClient client =
        client(
            request -> {
              if (sent.incrementAndGet() == 1) pause(request.timeout().plusMillis(50));
              return new TransportResponse(200, "PAID");
            },
            new QueryAdapter(),
            Duration.ofMillis(100))) {
      assertEquals(
          OrderStatus.PAID,
          OrderPoller.awaitPaid(
                  client, REFERENCE, Duration.ofSeconds(5), SECOND, 3, time, time::sleep)
              .data()
              .status());
      assertEquals(2, sent.get());
      assertEquals(List.of(SECOND), time.sleeps);
    }
  }

  private static void pause(Duration duration) {
    try {
      TimeUnit.NANOSECONDS.sleep(duration.toNanos());
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw new AssertionError(error);
    }
  }

  private static void assertBudgetsWithin(List<Duration> actual, Duration... limits) {
    assertEquals(limits.length, actual.size());
    for (int i = 0; i < limits.length; i++) {
      assertTrue(actual.get(i).compareTo(Duration.ofMillis(1)) >= 0);
      assertTrue(actual.get(i).compareTo(limits[i]) <= 0);
    }
  }

  private static void assertInterruptedFailure(EPayException error) {
    assertTrue(Thread.currentThread().isInterrupted());
    assertEquals(EPayException.Kind.TRANSPORT, error.kind());
    assertFalse(error.retryable());
    assertFalse(error.executionUncertain());
    assertNull(error.getCause());
    assertEquals(0, error.getSuppressed().length);
    assertEquals("网关传输失败", error.getMessage());
  }

  private static void assertTimeoutFailure(EPayException error) {
    assertEquals(EPayException.Kind.TIMEOUT, error.kind());
    assertEquals("订单轮询等待超时", error.getMessage());
    assertFalse(error.retryable());
    assertFalse(error.executionUncertain());
    assertNull(error.getCause());
    assertEquals(0, error.getSuppressed().length);
  }

  private static final class ManualTime implements LongSupplier {
    private long nanos;
    private final List<Duration> sleeps = new ArrayList<>();

    @Override
    public long getAsLong() {
      return nanos;
    }

    void advance(Duration duration) {
      nanos += duration.toNanos();
    }

    void sleep(long delay) {
      sleeps.add(Duration.ofNanos(delay));
      nanos += delay;
    }
  }

  private static EPayClient client(HttpTransport transport) {
    return client(transport, new QueryAdapter(), Duration.ofSeconds(5));
  }

  private static EPayClient client(
      HttpTransport transport, ProtocolAdapter adapter, Duration timeout) {
    return EPayClient.builder()
        .config(
            MerchantConfig.builder()
                .baseUrl("https://pay.example/prefix")
                .merchantId("1001")
                .credentials(new Md5Credentials("private-secret"))
                .build())
        .httpOptions(new HttpOptions(SECOND, SECOND, SECOND, timeout))
        .adapter(adapter)
        .transport(transport)
        .build();
  }

  private static class QueryAdapter implements ProtocolAdapter {
    @Override
    public String id() {
      return "poll-test";
    }

    @Override
    public Set<Capability> capabilities() {
      return Set.of(Capability.QUERY_ORDER);
    }

    @Override
    public void validateCredentials(Credentials credentials) {
      if (!(credentials instanceof Md5Credentials)) throw EPayException.configuration();
    }

    @Override
    public GatewayResult<PaymentResult> createPayment(
        ProtocolContext context, PaymentRequest request) {
      throw EPayException.unsupported();
    }

    @Override
    public GatewayResult<VerifiedNotification> verifyNotification(
        ProtocolContext context, NotificationRequest request) {
      throw EPayException.unsupported();
    }

    @Override
    public GatewayResult<OrderResult> queryOrder(ProtocolContext context, OrderReference request) {
      String status = context.send("GET", "order", Map.of("id", request.value())).body();
      if (status.equals("DENIED")) return GatewayResult.rejected("DENIED", "private-message");
      return GatewayResult.success(
          "OK",
          "private-message",
          new OrderResult(
              "private-trade",
              request.value(),
              "1001",
              BigDecimal.ONE,
              OrderStatus.valueOf(status),
              status,
              "custom"));
    }
  }
}
