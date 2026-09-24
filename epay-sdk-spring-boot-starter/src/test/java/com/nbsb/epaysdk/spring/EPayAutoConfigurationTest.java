package com.nbsb.epaysdk.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.Capability;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.HttpOptions;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.PaymentAction;
import com.nbsb.epaysdk.model.PaymentRequest;
import com.nbsb.epaysdk.model.PaymentScene;
import com.nbsb.epaysdk.model.RefundRequest;
import com.nbsb.epaysdk.spi.HttpTransport;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import com.nbsb.epaysdk.spi.TransportRequest;
import com.nbsb.epaysdk.spi.TransportResponse;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class EPayAutoConfigurationTest {
  private static final String TEST_MD5 = "test-only-md5-secret-not-a-merchant-key";
  private static String merchantPrivatePem;
  private static String platformPublicPem;

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(EPayAutoConfiguration.class));

  @BeforeAll
  static void generateTestKeys() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair merchant = generator.generateKeyPair();
    KeyPair platform = generator.generateKeyPair();
    merchantPrivatePem = pem("PRIVATE KEY", merchant.getPrivate().getEncoded());
    platformPublicPem = pem("PUBLIC KEY", platform.getPublic().getEncoded());
  }

  @Test
  void registersUsingBootImports() {
    assertThat(ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader()))
        .contains(EPayAutoConfiguration.class.getName());
  }

  @Test
  void emptyConfigurationDoesNotCreateOrBindAnything() {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(EPay.class);
          assertThat(context).doesNotHaveBean(MerchantConfig.class);
          assertThat(context).doesNotHaveBean(EPayProperties.class);
        });
  }

  @Test
  void credentialsAloneDoNotActivateConfiguration() {
    runner
        .withPropertyValues("epay.credentials.merchant-private-key=incomplete-test-key")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(EPay.class);
              assertThat(context).doesNotHaveBean(EPayProperties.class);
            });
  }

  @Test
  void md5AutomaticallySelectsV1AndBuildsBrowserFormWithoutNetwork() {
    CountingTransport transport = new CountingTransport();
    md5Runner()
        .withBean(HttpTransport.class, () -> transport)
        .run(
            context -> {
              assertThat(context).hasSingleBean(EPayClient.class);
              EPay client = context.getBean(EPay.class);
              assertThat(client.protocolId()).isEqualTo("epay-v1");
              var result = client.createPayment(browserPayment());
              assertThat(result.success()).isTrue();
              assertThat(result.data().actions()).hasSize(1);
              assertThat(result.data().actions().get(0)).isInstanceOf(PaymentAction.Form.class);
              assertThat(((PaymentAction.Form) result.data().actions().get(0)).html())
                  .contains("/gateway/submit.php", "order-spring-test")
                  .doesNotContain(TEST_MD5);
              assertThat(transport.calls.get()).isZero();
              assertThat(context.getBean(EPayProperties.class).toString()).doesNotContain(TEST_MD5);
              assertThat(context.getBean(EPayProperties.class).getCredentials().toString())
                  .doesNotContain(TEST_MD5);
            });
  }

  @Test
  void rsaAutomaticallySelectsV2AndInvokesSignedQuery() {
    CountingTransport transport = new CountingTransport();
    rsaRunner()
        .withBean(HttpTransport.class, () -> transport)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EPay client = context.getBean(EPay.class);
              assertThat(client.protocolId()).isEqualTo("epay-v2");
              assertTransportFailure(client);
              assertThat(transport.calls.get()).isEqualTo(1);
              assertThat(transport.lastRequest.uri().getPath()).startsWith("/gateway/");
              assertThat(transport.lastRequest.parameters()).containsKey("sign");
              assertThat(transport.lastRequest.parameters().values())
                  .doesNotContain(merchantPrivatePem, platformPublicPem);
            });
  }

  @Test
  void explicitXarrDisablesRefundBeforeTransport() {
    CountingTransport transport = new CountingTransport();
    rsaRunner()
        .withPropertyValues("epay.protocol=epay-v2-xarr")
        .withBean(HttpTransport.class, () -> transport)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EPay client = context.getBean(EPay.class);
              assertThat(client.protocolId()).isEqualTo("epay-v2-xarr");
              assertUnsupportedRefund(client);
              assertThat(transport.calls.get()).isZero();
              assertTransportFailure(client);
              assertThat(transport.calls.get()).isEqualTo(1);
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"epay-v1", "epay-v1-mpay", "mzf-legacy201"})
  void explicitMd5DialectsCanQuery(String protocol) {
    CountingTransport transport = new CountingTransport();
    md5Runner()
        .withPropertyValues("epay.protocol=" + protocol)
        .withBean(HttpTransport.class, () -> transport)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EPay client = context.getBean(EPay.class);
              assertThat(client.protocolId()).isEqualTo(protocol);
              assertTransportFailure(client);
              assertThat(transport.calls.get()).isEqualTo(1);
            });
  }

  @Test
  void mixedCredentialsFailWithoutExposingValues() {
    assertConfigurationFailure(
        md5Runner().withPropertyValues("epay.credentials.merchant-private-key=private-test-marker"),
        "不得混用",
        TEST_MD5,
        "private-test-marker");
  }

  @ParameterizedTest
  @ValueSource(strings = {"merchant-private-key", "platform-public-key"})
  void partialRsaFailsWithoutExposingValues(String key) {
    assertConfigurationFailure(
        configuredRunner().withPropertyValues("epay.credentials." + key + "=partial-test-marker"),
        "必须同时提供",
        "partial-test-marker");
  }

  @Test
  void malformedRsaFailsWithoutPreservingSensitiveCause() {
    assertConfigurationFailure(
        configuredRunner()
            .withPropertyValues(
                "epay.credentials.merchant-private-key=bad-private-test-marker",
                "epay.credentials.platform-public-key=bad-public-test-marker"),
        "PKCS8",
        "bad-private-test-marker",
        "bad-public-test-marker");
  }

  @Test
  void unknownProtocolFailsWithSafeListOfChoices() {
    assertConfigurationFailure(
        md5Runner().withPropertyValues("epay.protocol=unknown-test-marker"),
        "epay.protocol 无效",
        TEST_MD5,
        "unknown-test-marker");
  }

  @Test
  void explicitProtocolMustMatchCredentialsInBothDirections() {
    assertConfigurationFailure(
        md5Runner().withPropertyValues("epay.protocol=epay-v2"), "与商户凭据不匹配", TEST_MD5);
    assertConfigurationFailure(
        rsaRunner().withPropertyValues("epay.protocol=epay-v1"),
        "与商户凭据不匹配",
        merchantPrivatePem,
        platformPublicPem);
  }

  @Test
  void customMerchantConfigActivatesWithoutAnyEpayProperties() {
    MerchantConfig merchant = merchant();
    runner
        .withBean(MerchantConfig.class, () -> merchant)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(MerchantConfig.class);
              assertThat(context.getBean(MerchantConfig.class)).isSameAs(merchant);
              assertThat(context.getBean(EPay.class).createPayment(browserPayment()).success())
                  .isTrue();
            });
  }

  @Test
  void customMerchantConfigDoesNotParseUnusedPartialCredentials() {
    runner
        .withBean(MerchantConfig.class, EPayAutoConfigurationTest::merchant)
        .withPropertyValues("epay.credentials.merchant-private-key=unused-partial-test-key")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context.getBean(EPay.class).createPayment(browserPayment()).success())
                  .isTrue();
            });
  }

  @Test
  void customEpayBacksOffIncludingPropertyBindingDespiteMalformedConfiguration() {
    EPay custom = mock(EPay.class);
    when(custom.protocolId()).thenReturn("user-owned");
    runner
        .withBean(EPay.class, () -> custom)
        .withPropertyValues(
            "epay.base-url=https://gateway.example/",
            "epay.credentials.merchant-private-key=incomplete-test-key",
            "epay.http.request-timeout=not-a-duration",
            "epay.protocol=not-a-protocol")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).hasSingleBean(EPay.class);
              assertThat(context.getBean(EPay.class)).isSameAs(custom);
              assertThat(context.getBean(EPay.class).protocolId()).isEqualTo("user-owned");
              assertThat(context).doesNotHaveBean(EPayClient.class);
              assertThat(context).doesNotHaveBean(MerchantConfig.class);
              assertThat(context).doesNotHaveBean(EPayProperties.class);
            });
  }

  @Test
  void customClientAlsoBacksOffWithIncompleteCredentials() {
    try (EPayClient custom = EPayClient.builder().config(merchant()).build()) {
      configuredRunner()
          .withPropertyValues("epay.credentials.platform-public-key=incomplete-test-key")
          .withBean(EPayClient.class, () -> custom)
          .run(
              context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(EPay.class)).isSameAs(custom);
                assertThat(custom.createPayment(browserPayment()).success()).isTrue();
                assertThat(context).doesNotHaveBean(MerchantConfig.class);
                assertThat(context).doesNotHaveBean(EPayProperties.class);
              });
    }
  }

  @Test
  void explicitAdapterBeanTakesPrecedenceOverProtocolProperty() {
    ProtocolAdapter adapter = mock(ProtocolAdapter.class);
    OrderReference reference = OrderReference.byOutTradeNo("order-spring-test");
    when(adapter.id()).thenReturn("user-adapter");
    when(adapter.capabilities()).thenReturn(Set.of(Capability.QUERY_ORDER));
    when(adapter.queryOrder(any(), eq(reference)))
        .thenReturn(GatewayResult.rejected("not-found", "本地测试拒绝"));
    md5Runner()
        .withPropertyValues("epay.protocol=epay-v2")
        .withBean(ProtocolAdapter.class, () -> adapter)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EPay client = context.getBean(EPay.class);
              assertThat(client.protocolId()).isEqualTo("user-adapter");
              assertThat(client.queryOrder(reference).code()).isEqualTo("not-found");
            });
  }

  @Test
  void springClosesExternalTransportExactlyOnceAndClientDoesNotOwnIt() {
    CountingTransport transport = new CountingTransport();
    md5Runner()
        .withBean(HttpTransport.class, () -> transport)
        .run(
            context -> {
              EPay client = context.getBean(EPay.class);
              assertTransportFailure(client);
              client.close();
              client.close();
              assertThat(transport.closes.get()).isZero();
            });
    assertThat(transport.closes.get()).isEqualTo(1);
  }

  @Test
  void durationsAndDisabledCapabilitiesAffectClientOperations() {
    CountingTransport transport = new CountingTransport();
    md5Runner()
        .withBean(HttpTransport.class, () -> transport)
        .withPropertyValues(
            "epay.http.connect-timeout=1s",
            "epay.http.connection-request-timeout=2s",
            "epay.http.response-timeout=3s",
            "epay.http.request-timeout=4s",
            "epay.disabled-capabilities=REFUND,LIST_ORDERS")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              EPay client = context.getBean(EPay.class);
              assertUnsupportedRefund(client);
              assertThat(transport.calls.get()).isZero();
              assertTransportFailure(client);
              assertThat(transport.lastRequest.timeout())
                  .isPositive()
                  .isLessThanOrEqualTo(Duration.ofSeconds(4));
              assertThat(context.getBean(EPayProperties.class).httpOptions())
                  .isEqualTo(
                      new HttpOptions(
                          Duration.ofSeconds(1), Duration.ofSeconds(2),
                          Duration.ofSeconds(3), Duration.ofSeconds(4)));
            });
  }

  @Test
  void invalidTimeoutFailsBeforeAnyTransportRequest() {
    assertConfigurationFailure(
        md5Runner().withPropertyValues("epay.http.request-timeout=0s"), "各项超时必须为正", TEST_MD5);
  }

  @Test
  void explicitlyDisabledDoesNotParseBrokenPropertiesOrCreateClient() {
    configuredRunner()
        .withBean(MerchantConfig.class, EPayAutoConfigurationTest::merchant)
        .withPropertyValues(
            "epay.enabled=false",
            "epay.credentials.merchant-private-key=incomplete-test-key",
            "epay.http.request-timeout=not-a-duration")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(EPay.class);
              assertThat(context).doesNotHaveBean(EPayProperties.class);
              assertThat(context).hasSingleBean(MerchantConfig.class);
            });
  }

  private ApplicationContextRunner configuredRunner() {
    return runner.withPropertyValues(
        "epay.base-url=https://gateway.example/gateway/", "epay.merchant-id=merchant-test");
  }

  private ApplicationContextRunner md5Runner() {
    return configuredRunner().withPropertyValues("epay.credentials.md5-key=" + TEST_MD5);
  }

  private ApplicationContextRunner rsaRunner() {
    // MapPropertySource 保留 PEM 换行，不依赖 Properties 字符串解析规则。
    return configuredRunner()
        .withInitializer(
            context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new MapPropertySource(
                            "test-rsa",
                            Map.of(
                                "epay.credentials.merchant-private-key", merchantPrivatePem,
                                "epay.credentials.platform-public-key", platformPublicPem))));
  }

  private static MerchantConfig merchant() {
    return MerchantConfig.builder()
        .baseUrl("https://gateway.example/gateway/")
        .merchantId("merchant-test")
        .credentials(new Md5Credentials(TEST_MD5))
        .build();
  }

  private static PaymentRequest browserPayment() {
    return PaymentRequest.builder()
        .outTradeNo("order-spring-test")
        .name("测试商品")
        .amount(new BigDecimal("1.00"))
        .paymentMethod("alipay")
        .notifyUrl("https://shop.example/pay/notify")
        .returnUrl("https://shop.example/pay/return")
        .scene(PaymentScene.BROWSER_FORM)
        .build();
  }

  private static void assertTransportFailure(EPay client) {
    assertThatThrownBy(() -> client.queryOrder(OrderReference.byOutTradeNo("order-spring-test")))
        .isInstanceOfSatisfying(
            EPayException.class,
            exception -> assertThat(exception.kind()).isEqualTo(EPayException.Kind.TRANSPORT));
  }

  private static void assertUnsupportedRefund(EPay client) {
    assertThatThrownBy(
            () ->
                client.refund(
                    new RefundRequest(
                        OrderReference.byOutTradeNo("order-spring-test"),
                        new BigDecimal("1.00"),
                        null)))
        .isInstanceOfSatisfying(
            EPayException.class,
            exception -> assertThat(exception.kind()).isEqualTo(EPayException.Kind.UNSUPPORTED));
  }

  private static void assertConfigurationFailure(
      ApplicationContextRunner configured, String expectedMessage, String... secrets) {
    configured.run(
        context -> {
          assertThat(context).hasFailed();
          Throwable failure = context.getStartupFailure();
          Throwable root = failure;
          while (root.getCause() != null) {
            root = root.getCause();
          }
          assertThat(root)
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining(expectedMessage);
          while (failure != null) {
            assertThat(failure.getMessage()).doesNotContain(secrets);
            failure = failure.getCause();
          }
        });
  }

  private static String pem(String label, byte[] encoded) {
    return "-----BEGIN "
        + label
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
        + "\n-----END "
        + label
        + "-----";
  }

  private static final class CountingTransport implements HttpTransport {
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger closes = new AtomicInteger();
    private TransportRequest lastRequest;

    @Override
    public TransportResponse execute(TransportRequest request) {
      calls.incrementAndGet();
      lastRequest = request;
      throw EPayException.transport(false);
    }

    @Override
    public void close() {
      closes.incrementAndGet();
    }
  }
}
