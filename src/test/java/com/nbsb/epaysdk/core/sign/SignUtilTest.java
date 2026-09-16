package com.nbsb.epaysdk.core.sign;

import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SignUtilTest {

    @Test
    void map2Md5IsDeterministicAndExcludesEmptyAndSignFields() {
        Map<String, String> first = new HashMap<String, String>();
        first.put("pid", "1001");
        first.put("money", "1.00");
        first.put("name", "demo");
        first.put("sign", "should-ignore");
        first.put("sign_type", "MD5");
        first.put("param", "");

        Map<String, String> second = new HashMap<String, String>();
        second.put("name", "demo");
        second.put("money", "1.00");
        second.put("pid", "1001");

        assertEquals(SignUtil.map2Md5(first, "secret"), SignUtil.map2Md5(second, "secret"));
        assertEquals("7312b0857d241e5f9f3b5162d0a02b35", SignUtil.map2Md5(second, "secret"));
    }

    @Test
    void bodySignIncludesDeviceClientIpAndParamForYzf() {
        EPayBody body = new EPayBody();
        body.setPid("1001");
        body.setType("alipay");
        body.setOut_trade_no("ORD1");
        body.setNotify_url("https://shop.example/notify");
        body.setReturn_url("https://shop.example/return");
        body.setName("demo");
        body.setMoney("1.00");
        body.setKey("secret");
        body.setIs_mzf("false");
        body.setDeviceType(DeviceType.PC);
        body.setClientip("10.0.0.2");
        body.setParam("user=9");

        String signed = SignUtil.Body2Md5(body);
        Map<String, String> expected = new HashMap<String, String>();
        expected.put("pid", "1001");
        expected.put("type", "alipay");
        expected.put("out_trade_no", "ORD1");
        expected.put("notify_url", "https://shop.example/notify");
        expected.put("return_url", "https://shop.example/return");
        expected.put("name", "demo");
        expected.put("money", "1.00");
        expected.put("device", "pc");
        expected.put("param", "user=9");
        expected.put("clientip", "10.0.0.2");
        assertEquals(SignUtil.map2Md5(expected, "secret"), signed);
    }

    @Test
    void differentKeyProducesDifferentSign() {
        Map<String, String> map = new HashMap<String, String>();
        map.put("pid", "1001");
        map.put("money", "1.00");
        assertNotEquals(SignUtil.map2Md5(map, "a"), SignUtil.map2Md5(map, "b"));
    }
}
