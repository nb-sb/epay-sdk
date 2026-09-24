package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.internal.ApacheHttpTransport;
import com.nbsb.epaysdk.internal.OperationBudget;
import com.nbsb.epaysdk.internal.checks.Checks;
import com.nbsb.epaysdk.model.*;
import com.nbsb.epaysdk.protocol.epayv1.EpayV1Adapter;
import com.nbsb.epaysdk.protocol.epayv2.EpayV2Adapter;
import com.nbsb.epaysdk.protocol.mzf.MzfLegacyAdapter;
import com.nbsb.epaysdk.spi.*;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/** 线程安全业务门面：一次选择协议，不网络探测，不自动重试或降级。 */
public final class EPayClient implements EPay {
  private static final String AUTOMATIC_PROTOCOL = "auto";

  private final MerchantConfig config;
  private final ProtocolAdapter adapter;
  private final HttpTransport transport;
  private final boolean ownsTransport;
  private final Clock clock;
  private final HttpOptions httpOptions;
  private final Set<Capability> capabilities;
  private final String protocolId;
  private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock(true);
  private boolean closed;

  private EPayClient(Builder builder) {
    try {
      config = Checks.required(builder.config);
      clock = Checks.required(builder.clock);
      httpOptions = Checks.required(builder.httpOptions);
      adapter = builder.adapter != null ? builder.adapter : selectAdapter(config.credentials());
      adapter.validateCredentials(config.credentials());
      protocolId = Checks.text(adapter.id());
      if (!protocolId.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}"))
        throw EPayException.configuration();
      Set<Capability> enabled = new HashSet<>(Set.copyOf(adapter.capabilities()));
      enabled.removeAll(Set.copyOf(builder.disabledCapabilities));
      capabilities = Set.copyOf(enabled);
    } catch (RuntimeException e) {
      throw EPayException.configuration();
    }
    // 校验全部完成后才创建有资源的默认传输，失败构建不会遗留客户端。
    transport =
        builder.transport == null ? new ApacheHttpTransport(httpOptions) : builder.transport;
    ownsTransport = builder.transport == null || builder.ownsTransport;
  }

  private static ProtocolAdapter selectAdapter(Credentials credentials) {
    if (credentials instanceof Md5Credentials) return new EpayV1Adapter();
    if (credentials instanceof RsaCredentials) return new EpayV2Adapter();
    throw EPayException.configuration();
  }

  /** 显式协议标识的唯一目录。{@code auto} 返回 null，交给凭据选择参考方言。未知标识直接失败，不探测。 */
  public static ProtocolAdapter adapterFor(String protocolId) {
    String id = protocolId == null ? "" : protocolId.strip().toLowerCase(Locale.ROOT);
    return switch (id) {
      case AUTOMATIC_PROTOCOL -> null;
      case EpayV1Adapter.REFERENCE_ID -> new EpayV1Adapter();
      case EpayV1Adapter.MPAY_ID -> new EpayV1Adapter(EpayV1Adapter.Dialect.MPAY);
      case EpayV2Adapter.REFERENCE_ID -> new EpayV2Adapter();
      case EpayV2Adapter.XARR_ID -> new EpayV2Adapter(EpayV2Adapter.Dialect.XARR);
      case MzfLegacyAdapter.ID -> new MzfLegacyAdapter();
      default -> throw EPayException.configuration();
    };
  }

  /** 配置错误中展示的可选标识，不回显调用方传入的原值。 */
  public static String protocolChoices() {
    return AUTOMATIC_PROTOCOL
        + "、"
        + EpayV1Adapter.REFERENCE_ID
        + "、"
        + EpayV1Adapter.MPAY_ID
        + "、"
        + EpayV2Adapter.REFERENCE_ID
        + "、"
        + EpayV2Adapter.XARR_ID
        + "、"
        + MzfLegacyAdapter.ID;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Set<Capability> capabilities() {
    return capabilities;
  }

  @Override
  public String protocolId() {
    return protocolId;
  }

  @Override
  public GatewayResult<PaymentResult> createPayment(PaymentRequest request) {
    return invoke(
        System.nanoTime(),
        Capability.CREATE_PAYMENT,
        true,
        c -> adapter.createPayment(c, Checks.required(request)));
  }

  @Override
  public GatewayResult<OrderResult> queryOrder(OrderReference request) {
    return invoke(
        System.nanoTime(),
        Capability.QUERY_ORDER,
        false,
        c -> adapter.queryOrder(c, Checks.required(request)));
  }

  @Override
  public GatewayResult<OrderResult> queryOrder(OrderReference request, Duration requestTimeout) {
    long started = System.nanoTime();
    Checks.required(request);
    Checks.requestDuration(requestTimeout);
    Duration budget =
        requestTimeout.compareTo(httpOptions.requestTimeout()) < 0
            ? requestTimeout
            : httpOptions.requestTimeout();
    return invoke(
        Capability.QUERY_ORDER, false, budget, started, c -> adapter.queryOrder(c, request));
  }

  @Override
  public GatewayResult<VerifiedNotification> verifyNotification(NotificationRequest request) {
    return invoke(
        System.nanoTime(),
        Capability.VERIFY_NOTIFICATION,
        false,
        c -> adapter.verifyNotification(c, Checks.required(request)));
  }

  @Override
  public GatewayResult<RefundResult> refund(RefundRequest request) {
    return invoke(
        System.nanoTime(),
        Capability.REFUND,
        true,
        c -> adapter.refund(c, Checks.required(request)));
  }

  @Override
  public GatewayResult<RefundResult> queryRefund(RefundReference request) {
    return invoke(
        System.nanoTime(),
        Capability.QUERY_REFUND,
        false,
        c -> adapter.queryRefund(c, Checks.required(request)));
  }

  @Override
  public GatewayResult<MerchantResult> queryMerchant() {
    return invoke(System.nanoTime(), Capability.QUERY_MERCHANT, false, adapter::queryMerchant);
  }

  @Override
  public GatewayResult<OrderListResult> listOrders(OrderListRequest request) {
    return invoke(
        System.nanoTime(),
        Capability.LIST_ORDERS,
        false,
        c -> adapter.listOrders(c, Checks.required(request)));
  }

  private <T> GatewayResult<T> invoke(
      long started,
      Capability capability,
      boolean write,
      Function<ProtocolContext, GatewayResult<T>> operation) {
    return invoke(capability, write, httpOptions.requestTimeout(), started, operation);
  }

  private <T> GatewayResult<T> invoke(
      Capability capability,
      boolean write,
      Duration requestTimeout,
      long started,
      Function<ProtocolContext, GatewayResult<T>> operation) {
    OperationBudget budget = new OperationBudget(requestTimeout, started);
    try {
      if (!lifecycle.readLock().tryLock(budget.remainingNanos(), TimeUnit.NANOSECONDS)) {
        throw EPayException.transport(true);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw EPayException.transport(false);
    }
    try {
      if (closed) throw EPayException.closed();
      if (!capabilities.contains(capability)) throw EPayException.unsupported();
      budget.check();
      ProtocolContext context =
          new ProtocolContext(config, transport, clock, requestTimeout, started);
      try {
        GatewayResult<T> result = operation.apply(context);
        if (result == null) throw EPayException.protocol();
        budget.check();
        return result;
      } catch (EPayException e) {
        throw e.withExecutionUncertain(write && context.requestAttempted());
      } catch (RuntimeException e) {
        throw EPayException.protocol().withExecutionUncertain(write && context.requestAttempted());
      }
    } finally {
      lifecycle.readLock().unlock();
    }
  }

  /** 等待在途操作完成；幂等关闭，外部传输默认不关闭。 */
  @Override
  public void close() {
    lifecycle.writeLock().lock();
    try {
      if (closed) return;
      closed = true;
      if (ownsTransport) {
        try {
          transport.close();
        } catch (RuntimeException e) {
          throw EPayException.transport(false);
        }
      }
    } finally {
      lifecycle.writeLock().unlock();
    }
  }

  @Override
  public String toString() {
    return "EPayClient[已遮蔽]";
  }

  public static final class Builder {
    private MerchantConfig config;
    private ProtocolAdapter adapter;
    private HttpTransport transport;
    private boolean ownsTransport;
    private Clock clock = Clock.systemUTC();
    private HttpOptions httpOptions = HttpOptions.defaults();
    private Set<Capability> disabledCapabilities = Set.of();

    private Builder() {}

    public Builder config(MerchantConfig value) {
      config = value;
      return this;
    }

    public Builder adapter(ProtocolAdapter value) {
      adapter = value;
      return this;
    }

    public Builder transport(HttpTransport value) {
      return transport(value, false);
    }

    public Builder transport(HttpTransport value, boolean takeOwnership) {
      transport = value;
      ownsTransport = takeOwnership;
      return this;
    }

    public Builder clock(Clock value) {
      clock = value;
      return this;
    }

    public Builder httpOptions(HttpOptions value) {
      httpOptions = value;
      return this;
    }

    public Builder disabledCapabilities(Set<Capability> value) {
      try {
        disabledCapabilities = Set.copyOf(value);
      } catch (RuntimeException e) {
        throw EPayException.configuration();
      }
      return this;
    }

    public EPayClient build() {
      return new EPayClient(this);
    }
  }
}
