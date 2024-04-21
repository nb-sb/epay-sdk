package com.nbsb.epaysdk.epaybase.Impl;

import com.nbsb.epaysdk.epaybase.EPayExecute;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;

import java.io.IOException;
import java.util.Map;

public class MZFExecute extends EPayExecute {
    @Override
    public  String executePayment(Map<String, String> map) {
        //使用 httpclient5 http请求，并且里面的from data值是map
        try(CloseableHttpClient httpClient = HttpClients.createDefault())  {
            // Build URL with parameters
            HttpPost httpPost = new HttpPost(map.get("url"));
            map.remove("url");
            map.remove("epayBodyType");
            map.remove("key");
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
    public String queryOrderInfo(Map<String,String> map) {
        // 创建一个HttpClient对象
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            // 使用URIBuilder构建URL，并添加参数
            URIBuilder uriBuilder = new URIBuilder(map.get("url"));
            uriBuilder.addParameter("order_no", map.get("order_no"));
            uriBuilder.addParameter("type", map.get("query_type"));

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
