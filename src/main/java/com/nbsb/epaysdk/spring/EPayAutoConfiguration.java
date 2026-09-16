package com.nbsb.epaysdk.spring;

import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EPayProperties.class)
@ConditionalOnProperty(prefix = "nbsb.pay.account", name = "appId")
public class EPayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MerchantConfig.class)
    public MerchantConfig merchantConfig(EPayProperties properties) {
        return properties.toMerchantConfig();
    }

    @Bean
    @ConditionalOnMissingBean(EPay.class)
    public EPayClient ePayClient(EPayProperties properties, MerchantConfig merchantConfig) {
        return EPayClient.builder()
                .payType(properties.payType())
                .config(merchantConfig)
                .build();
    }
}
