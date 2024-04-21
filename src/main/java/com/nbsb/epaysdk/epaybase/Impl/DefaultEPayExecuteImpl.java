package com.nbsb.epaysdk.epaybase.Impl;

import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.EPayExecute;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * @author whn
 */
public class DefaultEPayExecuteImpl extends EPayExecute {
    @Override
    public  String executePayment(Map<String, String> map) {
        //使用 httpclient5 http请求，并且里面的from data值是map
        try(CloseableHttpClient httpClient = HttpClients.createDefault())  {
            // Build URL with parameters
            HttpPost httpPost = new HttpPost(map.get("url"));
            map.remove("url");
            map.remove("key");
            //if is yzf
            if (!"true".equals(map.get("is_mzf"))) {
                map.put("device", "pc");
                map.put("param", "");
                map.put("clientip", "192.168.1.100");
            }
            map.remove("is_mzf");
            // Add form parameters
            HttpEntity entity = buildFormData(map);
            httpPost.setEntity(entity);
            // Execute the request
            ClassicHttpResponse response = httpClient.execute(httpPost);
            // Process the response
            int statusCode = response.getCode();
            String responseBody = EntityUtils.toString(response.getEntity());
            // Output response
            System.out.println("Status Code: " + statusCode);
            System.out.println("Response Body: " + responseBody);
            return responseBody;
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String queryOrderInfo(Map<String, String> map) throws IOException {
        return null;
    }


    public static void testniu() {
        EPayBody ePayBody = new EPayBody();
        //从配置文件中获取
        ePayBody.setUrl("https://yi-pay.com/mapi.php");
        ePayBody.setPid("1002");
        ePayBody.setKey("0VXv96xz9QW4x1z8555v0dz4VNdosv85");

//        ePayBody.setUrl("https://qrcode.0er.cn/pay/apisubmit");
//        ePayBody.setPid("1409");
//        ePayBody.setKey("o2D3R1AQI9mgE0iqJtDdFfMlboE2wEuK");

        ePayBody.setMoney("0.1");
        ePayBody.setName("name999");
        ePayBody.setOut_trade_no("123312312");
        ePayBody.setNotify_url("https://baidu.com/");
        ePayBody.setReturn_url("https://baidu.com/");
        ePayBody.setType("alipay");
        ePayBody.setIs_mzf("false");
        String md5Body = SignUtil.Body2Md5(ePayBody);
        ePayBody.setSign(md5Body);
        Map<String, String> bodyMap = EpayBody2Map.beanToMap(ePayBody);

        DefaultEPayExecuteImpl defaultEPayExecute = new DefaultEPayExecuteImpl();
        defaultEPayExecute.executePayment(bodyMap);

//        System.out.println( JSON.toJSONString(bodyMap.toString()));
//        System.out.println(bodyMap);
    }

}
