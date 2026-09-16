package com.nbsb.epaysdk.core.config;

import com.nbsb.epaysdk.core.exception.EPayConfigException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MerchantConfigTest {

    @Test
    void normalizesUrlAndDefaults() {
        MerchantConfig config = MerchantConfig.builder()
                .url("https://pay.example.com")
                .appId("1001")
                .appKey("secret")
                .build();
        assertEquals("https://pay.example.com/", config.getUrl());
        assertEquals("https://pay.example.com/mapi.php", config.join("mapi.php"));
        assertEquals("https://pay.example.com/pay/apisubmit", config.join("/pay/apisubmit"));
        assertEquals("127.0.0.1", config.getClientIp());
        assertEquals(5000, config.getConnectTimeoutMs());
    }

    @Test
    void requiresCredentials() {
        assertThrows(EPayConfigException.class, () -> MerchantConfig.builder().appId("1").appKey("k").build());
        assertThrows(EPayConfigException.class, () -> MerchantConfig.builder().url("https://x").appKey("k").build());
        assertThrows(EPayConfigException.class, () -> MerchantConfig.builder().url("https://x").appId("1").build());
    }
}
