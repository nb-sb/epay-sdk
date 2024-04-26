package com.nbsb.epaysdk.epaybase.Impl;

import com.alibaba.fastjson.JSON;
import com.nbsb.epaysdk.epaybase.EPayExecute;
import com.nbsb.epaysdk.epaybase.enumeration.DeviceType;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class YZFExecute extends EPayExecute {
    Logger logger = org.slf4j.LoggerFactory.getLogger(YZFExecute.class);
    @Override
    public  String executePayment(Map<String, String> map) {
        //使用 httpclient5 http请求，并且里面的from data值是map
        try(CloseableHttpClient httpClient = HttpClients.createDefault())  {
            // Build URL with parameters
            HttpPost httpPost = new HttpPost(map.get("url"));
            map.remove("url");
            map.remove("epayBodyType");
            map.remove("key");
            //if is yzf
            map.put("device", DeviceType.JUMP.getDeviceTypeName());
            map.put("param", "");
            map.put("clientip", "192.168.1.100");
            // Add form parameters
            HttpEntity entity = buildFormData(map);
            httpPost.setEntity(entity);
            ClassicHttpResponse response = httpClient.execute(httpPost);
            String responseBody = EntityUtils.toString(response.getEntity());
            logger.info("Response Body: {}",responseBody);
            return responseBody;
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public String queryOrderInfo(Map<String,String> map)  {
        // 创建一个HttpClient对象
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 使用URIBuilder构建URL，并添加参数
            URIBuilder uriBuilder = new URIBuilder(map.get("url"));
            uriBuilder.addParameter("act", map.get("act"));
            uriBuilder.addParameter("pid", map.get("pid"));
            uriBuilder.addParameter("key", map.get("key"));

            String queryType = map.get("query_type");
            String orderNo = map.get("order_no");

            if ("1".equals(queryType)) {
                uriBuilder.addParameter("trade_no", orderNo);
            } else if ("2".equals(queryType)) {
                uriBuilder.addParameter("out_trade_no", orderNo);
            } else {
                throw new RuntimeException("订单类型错误");
            }

            // 创建一个HttpGet对象，并设置URI
            HttpGet httpGet = new HttpGet(uriBuilder.build());

            // 发送HTTP GET请求，并获取响应
            ClassicHttpResponse response = httpClient.execute(httpGet);
            // 从响应中读取响应体内容
            String responseBody = EntityUtils.toString(response.getEntity());
            return responseBody;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
