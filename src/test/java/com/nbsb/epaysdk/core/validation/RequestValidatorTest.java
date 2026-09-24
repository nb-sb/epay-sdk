package com.nbsb.epaysdk.core.validation;

import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.api.entity.request.RefundCmd;
import com.nbsb.epaysdk.core.exception.EPayValidationException;
import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequestValidatorTest {

    private static GetQRCmd validPay() {
        return new GetQRCmd("demo", "ORD-1", "1.00", PaymentMethod.ALIPAY,
                "https://shop.example/notify", "https://shop.example/return");
    }

    @Test
    void acceptsValidPayQueryRefund() {
        assertDoesNotThrow(() -> RequestValidator.validatePay(validPay()));
        assertDoesNotThrow(() -> RequestValidator.validateQuery(Query.byOutTradeNo("ORD-1")));
        assertDoesNotThrow(() -> RequestValidator.validateRefund(RefundCmd.byOutTradeNo("ORD-1", "1.00")));
    }

    @Test
    void rejectsBadAmountAndMissingFields() {
        GetQRCmd badAmount = validPay();
        badAmount.setAmount("1.001");
        assertThrows(EPayValidationException.class, () -> RequestValidator.validatePay(badAmount));

        GetQRCmd zero = validPay();
        zero.setAmount("0.00");
        assertThrows(EPayValidationException.class, () -> RequestValidator.validatePay(zero));

        GetQRCmd badUrl = validPay();
        badUrl.setNotify_url("ftp://x");
        assertThrows(EPayValidationException.class, () -> RequestValidator.validatePay(badUrl));

        assertThrows(EPayValidationException.class, () -> RequestValidator.validateQuery(new Query(3, "x")));
        assertThrows(EPayValidationException.class, () -> RequestValidator.validateRefund(new RefundCmd()));
    }

    @Test
    void rejectsZeroRefundAmount() {
        assertThrows(EPayValidationException.class, () -> RequestValidator.validateRefund(RefundCmd.byOutTradeNo("ORD-1", "0")));
        assertThrows(EPayValidationException.class, () -> RequestValidator.validateRefund(RefundCmd.byOutTradeNo("ORD-1", "0.0")));
        assertThrows(EPayValidationException.class, () -> RequestValidator.validateRefund(RefundCmd.byTradeNo("T1", "0.00")));
        assertDoesNotThrow(() -> RequestValidator.validateRefund(RefundCmd.byOutTradeNo("ORD-1", "0.10")));
    }
}
