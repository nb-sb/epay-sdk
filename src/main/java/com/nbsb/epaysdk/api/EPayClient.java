package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.reponse.MerchantInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderListResponse;
import com.nbsb.epaysdk.api.entity.reponse.RefundResponse;
import com.nbsb.epaysdk.api.entity.reponse.SubmitResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.OrderListQuery;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.api.entity.request.RefundCmd;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayConfigException;
import com.nbsb.epaysdk.core.notify.NotifyPayload;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;

import java.util.Map;

/**
 * Recommended entry point: one config instance, one channel, typed operations.
 */
public final class EPayClient implements EPay {

    private final EPay delegate;
    private final MerchantConfig config;
    private final PayType payType;

    private EPayClient(PayType payType, MerchantConfig config) {
        if (payType == null) {
            throw new EPayConfigException("PayType 不能为空");
        }
        if (config == null) {
            throw new EPayConfigException("MerchantConfig 不能为空");
        }
        this.payType = payType;
        this.config = config;
        this.delegate = EPayFactory.of(payType, config);
    }

    public static Builder builder() {
        return new Builder();
    }

    public MerchantConfig getConfig() {
        return config;
    }

    public PayType getPayType() {
        return payType;
    }

    @Override
    public MapiResponse mapi(GetQRCmd cmd) {
        return delegate.mapi(cmd);
    }

    @Override
    public SubmitResponse submit(GetQRCmd cmd) {
        return delegate.submit(cmd);
    }

    @Override
    public OrderInfoResponse queryOrder(Query query) {
        return delegate.queryOrder(query);
    }

    @Override
    public RefundResponse refund(RefundCmd cmd) {
        return delegate.refund(cmd);
    }

    @Override
    public MerchantInfoResponse queryMerchant() {
        return delegate.queryMerchant();
    }

    @Override
    public OrderListResponse queryOrders(OrderListQuery query) {
        return delegate.queryOrders(query);
    }

    @Override
    public boolean verifyNotify(Map<String, String> params) {
        return delegate.verifyNotify(params);
    }

    @Override
    public NotifyPayload parseNotify(Map<String, String> params) {
        return delegate.parseNotify(params);
    }

    @Override
    public String buildSubmitForm(GetQRCmd cmd) {
        return delegate.buildSubmitForm(cmd);
    }

    @Override
    public OrderInfoResponse waitUntilPaid(Query query, long timeoutMs, long intervalMs) {
        return delegate.waitUntilPaid(query, timeoutMs, intervalMs);
    }

    public static final class Builder {
        private PayType payType = PayType.YZF;
        private MerchantConfig config;

        public Builder payType(PayType payType) {
            this.payType = payType;
            return this;
        }

        public Builder config(MerchantConfig config) {
            this.config = config;
            return this;
        }

        public EPayClient build() {
            return new EPayClient(payType, config);
        }
    }
}
