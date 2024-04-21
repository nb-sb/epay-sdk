package com.nbsb.epaysdk;

import com.alibaba.fastjson.JSON;
import com.nbsb.epaysdk.api.EPayFactory;
import com.nbsb.epaysdk.api.Impl.EPayMZF;
import com.nbsb.epaysdk.api.Impl.EPayYZF;
import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;
import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EpaySdkApplicationTests {

    @Test
    public void testYZF() {
        GetQRCmd getQRCmd = new GetQRCmd("测试商品名称","20240421173712331","0.1",
                PaymentMethod.ALIPAY,
                "https://baidu1.com/","https://baidu1.com/");
        EPayYZF ePayYZF = new EPayYZF();
        MapiResponse mapi = ePayYZF.mapi(getQRCmd);
        System.out.println(JSON.toJSONString(mapi));
    }

    @Test
    public void testYZFquery() {
        Query query = new Query(2,"20240421173712331");
        EPayYZF ePayYZF = new EPayYZF();
        Object mapi = ePayYZF.queryOrder(query);
        System.out.println(JSON.toJSONString(mapi));
    }
    @Test
    public void testMZF() {
        GetQRCmd cmd = new GetQRCmd("测试商品名称","202401411121173712331","0.1",
                PaymentMethod.ALIPAY,
                "https://baidu1.com/","https://baidu1.com/");
        EPayMZF ePayMZF = new EPayMZF();
        MapiResponse mapi = ePayMZF.mapi(cmd);
        System.out.println(JSON.toJSONString(mapi));
    }
    @Test
    public void testMZFquery() {
        Query query = new Query(2,"202401411211173712331");
        EPayMZF ePayMZF = new EPayMZF();
        Object mapi = ePayMZF.queryOrder(query);
        System.out.println(JSON.toJSONString(mapi));
    }


    @Test
    public void testFactory() {
        EPayFactory ePayFactory = new EPayFactory();
        //传入 码支付类型 PayType.MZF 或者 易支付类型 PayType.YZF
        EPayMZF ePay = (EPayMZF) ePayFactory.create(PayType.MZF);

        GetQRCmd cmd = new GetQRCmd("测试商品名称","2024014211173712331","0.1",
                PaymentMethod.ALIPAY,
                "https://baidu1.com/","https://baidu1.com/");
        MapiResponse mapi = ePay.mapi(cmd);
        System.out.println(JSON.toJSONString(mapi));
    }
}
