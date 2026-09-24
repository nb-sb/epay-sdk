package com.nbsb.epaysdk.spring;

import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.spi.HttpTransport;
import com.nbsb.epaysdk.spi.ProtocolAdapter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;

/** 不依赖 Web 或组件扫描；已有业务客户端时连属性绑定和凭据解析一起退让。 */
@AutoConfiguration
@ConditionalOnClass(EPayClient.class)
@ConditionalOnMissingBean(EPay.class)
@ConditionalOnProperty(
    prefix = "epay",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Conditional(EPayAutoConfiguration.ConfiguredCondition.class)
@EnableConfigurationProperties(EPayProperties.class)
public class EPayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(MerchantConfig.class)
  MerchantConfig epayMerchantConfig(EPayProperties properties) {
    return properties.merchantConfig();
  }

  @Bean(destroyMethod = "close")
  EPayClient epayClient(
      MerchantConfig config,
      EPayProperties properties,
      ObjectProvider<ProtocolAdapter> adapters,
      ObjectProvider<HttpTransport> transports) {
    // 显式 Bean 优先，不根据站点 URL 或网络探测猜测协议。
    ProtocolAdapter adapter = adapters.getIfAvailable();
    if (adapter == null) {
      adapter = configuredAdapter(properties.getProtocol());
    }
    if (adapter != null) {
      try {
        adapter.validateCredentials(config.credentials());
      } catch (RuntimeException ignored) {
        throw new IllegalArgumentException("epay.protocol 或 ProtocolAdapter 与商户凭据不匹配");
      }
    }
    EPayClient.Builder builder =
        EPayClient.builder()
            .config(config)
            .httpOptions(properties.httpOptions())
            .disabledCapabilities(properties.getDisabledCapabilities());
    if (adapter != null) {
      builder.adapter(adapter);
    }
    // Spring 销毁外部传输 Bean；客户端只关闭自己创建的默认传输。
    HttpTransport transport = transports.getIfAvailable();
    if (transport != null) {
      builder.transport(transport);
    }
    return builder.build();
  }

  private static ProtocolAdapter configuredAdapter(String protocol) {
    try {
      return EPayClient.adapterFor(protocol);
    } catch (EPayException exception) {
      throw new IllegalArgumentException("epay.protocol 无效；可选 " + EPayClient.protocolChoices());
    }
  }

  static final class ConfiguredCondition extends AnyNestedCondition {
    ConfiguredCondition() {
      super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnBean(MerchantConfig.class)
    static class MerchantBean {}

    @ConditionalOnProperty(prefix = "epay", name = "base-url")
    static class BaseUrlProperty {}
  }
}
