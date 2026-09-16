package com.nbsb.epaysdk.api.entity;

import com.nbsb.epaysdk.api.entity.reponse.CommonResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.enumeration.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonResponseTest {

    @Test
    void successMeansCodeOne() {
        CommonResponse ok = new CommonResponse();
        ok.setCode(1);
        assertTrue(ok.isSuccess());
        assertTrue(CommonResponse.verifyResponse(ok));

        CommonResponse fail = new CommonResponse();
        fail.setCode(0);
        assertFalse(fail.isSuccess());
    }

    @Test
    void map2BeanAndPaidHelpers() {
        Map<String, Object> raw = new HashMap<String, Object>();
        raw.put("code", 1);
        raw.put("out_trade_no", "ORD-1");
        raw.put("status", 1);
        raw.put("money", "1.00");
        OrderInfoResponse order = EpayBody2Map.Map2Bean(raw, OrderInfoResponse.class);
        assertTrue(order.isPaid());
        assertEquals(OrderStatus.PAID, order.orderStatus());
        assertEquals("ORD-1", order.getOut_trade_no());
    }
}
