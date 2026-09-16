package com.nbsb.epaysdk.core.notify;

import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPaySignException;
import com.nbsb.epaysdk.epaybase.enumeration.TradeStatus;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotifyVerifierTest {

    private static MerchantConfig config() {
        return MerchantConfig.builder()
                .url("https://pay.example.com/")
                .appId("1001")
                .appKey("secret")
                .build();
    }

    private static Map<String, String> paidNotify(String key) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("pid", "1001");
        params.put("trade_no", "T1");
        params.put("out_trade_no", "O1");
        params.put("type", "alipay");
        params.put("name", "demo");
        params.put("money", "1.00");
        params.put("trade_status", "TRADE_SUCCESS");
        params.put("sign_type", "MD5");
        params.put("sign", SignUtil.map2Md5(new HashMap<String, String>(params), key));
        return params;
    }

    @Test
    void acceptsValidSignatureAndPid() {
        Map<String, String> params = paidNotify("secret");
        assertTrue(NotifyVerifier.verify(params, config()));
        NotifyPayload payload = NotifyVerifier.parseAndVerify(params, config());
        assertTrue(payload.isPaid());
        assertEquals(TradeStatus.TRADE_SUCCESS, payload.getTradeStatus());
        assertEquals("O1", payload.getOutTradeNo());
        assertEquals("success", NotifyPayload.SUCCESS_ACK);
    }

    @Test
    void rejectsWrongKeyAndWrongPid() {
        Map<String, String> params = paidNotify("other");
        assertFalse(NotifyVerifier.verify(params, config()));

        Map<String, String> wrongPid = paidNotify("secret");
        wrongPid.put("pid", "9999");
        wrongPid.put("sign", SignUtil.map2Md5(new HashMap<String, String>(wrongPid), "secret"));
        assertFalse(NotifyVerifier.verify(wrongPid, config()));
        assertThrows(EPaySignException.class, () -> NotifyVerifier.parseAndVerify(wrongPid, config()));
    }
}
