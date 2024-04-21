package com.nbsb.epaysdk;

import com.alibaba.fastjson.JSON;
import com.nbsb.epaysdk.api.Impl.EPayMZF;
import com.nbsb.epaysdk.api.Impl.EPayYZF;
import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.epaybase.Impl.DefaultEPayExecuteImpl;
import com.nbsb.epaysdk.epaybase.Impl.MZFExecute;
import com.nbsb.epaysdk.epaybase.Impl.YZFExecute;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest
class EpaySdkApplicationTests {

    @Test
    void contextLoads() {
    }
    @Test
    public void test2222() {
        String url = "https://www.qjpay.icu/pay/apisubmit";//支付地址
        String pid = "2142";//商户id
        String type = "alipay";//支付类型
        String outTradeNo = "120340806151343349";//商户单号
        String notifyUrl = "https://baidu.com/";//异步通知
        String returnUrl = "https://baidu.com/";//跳转地址
        String name = "name999";//商品名
        String money = "1";//价格
        String signType = "MD5";//签名类型
        String key = "XHbVwThjtfSPui2xuwwg8QHI7lRbpP3f";//商户密钥
        //参数存入 map
        Map<String, String> sign = new HashMap<>();
        sign.put("pid", pid);
        sign.put("type", type);
        sign.put("out_trade_no", outTradeNo);
        sign.put("notify_url", notifyUrl);
        sign.put("return_url", returnUrl);
        sign.put("name", name);
        sign.put("money", money);
        //根据key升序排序
        sign = sortByKey(sign);
        String signStr = "";
        //遍历map 转成字符串
        for (Map.Entry<String, String> m : sign.entrySet()) {
            signStr += m.getKey() + "=" + m.getValue() + "&";
        }
        //去掉最后一个 &
        signStr = signStr.substring(0, signStr.length() - 1);
        //最后拼接上KEY
        signStr += key;
        //转为MD5
        signStr = DigestUtils.md5DigestAsHex(signStr.getBytes());
        sign.put("sign_type", signType);
        sign.put("sign", signStr);
        System.out.println("<form id='paying' action='" + url + "/submit.php' method='post'>");
        for(Map.Entry<String,String> m :sign.entrySet()){
            System.out.println("<input type='hidden' name='"+m.getKey()+"' value='"+m.getValue()+"'/>");
        }
        System.out.println("<input type='submit' value='正在跳转'>");
        System.out.println("</form>");
        System.out.println("<script>document.forms['paying'].submit();</script>");

    }

    public static <K extends Comparable<? super K>, V > Map<K, V> sortByKey(Map<K, V> map) {
        Map<K, V> result = new LinkedHashMap<>();

        map.entrySet().stream()
                .sorted(Map.Entry.<K, V>comparingByKey()).forEachOrdered(e -> result.put(e.getKey(), e.getValue()));
        return result;
    }

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
        GetQRCmd cmd = new GetQRCmd("测试商品名称","202401421173712331","0.1",
                PaymentMethod.ALIPAY,
                "https://baidu1.com/","https://baidu1.com/");
        EPayMZF ePayMZF = new EPayMZF();
        MapiResponse mapi = ePayMZF.mapi(cmd);
        System.out.println(JSON.toJSONString(mapi));
    }
    @Test
    public void testMZFquery() {
        Query query = new Query(2,"202401421173712331");
        EPayMZF ePayMZF = new EPayMZF();
        Object mapi = ePayMZF.queryOrder(query);
        System.out.println(JSON.toJSONString(mapi));
    }
    @Test
    public void testdefalut() {
        DefaultEPayExecuteImpl.testniu();
    }
}
