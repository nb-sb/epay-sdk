package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.Impl.EPayMZF;
import com.nbsb.epaysdk.api.Impl.EPayYZF;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayException;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;

/**
 * Creates a 易支付 or 码支付 client.
 */
public class EPayFactory {

    public static EPay create(PayType payType) {
        return of(payType, MerchantConfig.fromAccountConfig());
    }

    public static EPay create(PayType payType, MerchantConfig config) {
        return of(payType, config);
    }

    public static EPay of(PayType payType) {
        return of(payType, MerchantConfig.fromAccountConfig());
    }

    public static EPay of(PayType payType, MerchantConfig config) {
        if (payType == PayType.MZF) {
            return new EPayMZF(config);
        }
        if (payType == PayType.YZF) {
            return new EPayYZF(config);
        }
        throw new EPayException("未找到的支付方式（支持码支付/易支付）");
    }
}
