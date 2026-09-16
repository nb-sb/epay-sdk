package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.notify.NotifyPayload;
import com.nbsb.epaysdk.core.notify.NotifyVerifier;

import java.util.Map;

/**
 * Callback hook for notify_url. Return {@link NotifyPayload#SUCCESS_ACK} after persisting the order.
 */
public interface EPayInterface {

    /**
     * If the body is not {@code success}, the gateway retries.
     */
    String onPayResult(Map<String, String> params);

    default boolean verifyNotify(Map<String, String> params, String appKey) {
        return NotifyVerifier.verify(params, appKey);
    }

    default NotifyPayload parseNotify(Map<String, String> params, MerchantConfig config) {
        return NotifyVerifier.parseAndVerify(params, config);
    }
}
