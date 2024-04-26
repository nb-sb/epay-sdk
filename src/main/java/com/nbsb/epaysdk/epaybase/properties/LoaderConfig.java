package com.nbsb.epaysdk.epaybase.properties;

import com.nbsb.epaysdk.epaybase.common.Constant;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * author: Wanghaonan @戏人看戏
 * description: 加载配置文件信息
 * create: 2024/4/21 15:34
 */
@Component
public class LoaderConfig implements InitializingBean {
    @Autowired
    private Environment environment;
    @Override
    public void afterPropertiesSet() {
        if (environment == null) {
            throw new RuntimeException("环境未配置，请检查");
        }
        String accountPrefix = Constant.propertiesPrefix + ".account.";
        String appId = environment.getProperty(accountPrefix + "appId");
        if (Objects.nonNull(appId)) {
            AccountConfig.setAppId(appId);
        }
        String appKey = environment.getProperty(accountPrefix + "appKey");
        if (Objects.nonNull(appKey)) {
            AccountConfig.setAppKey(appKey);
        }
        String url = environment.getProperty(accountPrefix + "url");
        if (Objects.nonNull(url)) {
            AccountConfig.setUrl(url);
        }
    }
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }
}
