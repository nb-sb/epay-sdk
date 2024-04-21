package com.nbsb.epaysdk.api.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.epaybase.Impl.YZFExecute;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.properties.AccountConfig;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;

import java.util.Map;

import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.*;

/**
 * @author whn
 */
public class EPayYZF implements EPay {


    @Override
    public MapiResponse mapi(GetQRCmd cmd) {
        EPayBody ePayBody = cmd2EPayBody(cmd);
        ePayBody.setUrl(AccountConfig.getUrl() + "mapi.php");
        ePayBody.setIs_mzf("false");
        //转为md5
        ePayBody.setSign(SignUtil.Body2Md5(ePayBody));
        Map<String, String> bodyMap = EpayBody2Map.beanToMap(ePayBody);
        YZFExecute yzfExecute = new YZFExecute();
        Object res = yzfExecute.executePayment(bodyMap);
        // 使用 FastJSON 解析 JSON 字符串为一个 Map 对象
        Map<String, Object> resultMap = JSON.parseObject(res.toString(), new TypeReference<Map<String, Object>>() {});
        MapiResponse mapiResponse = Map2MapiResponse(resultMap);
        return mapiResponse;
    }

    @Override
    public Object submit(GetQRCmd cmd) {
        EPayBody ePayBody = cmd2EPayBody(cmd);
        ePayBody.setUrl(AccountConfig.getUrl() + "submit.php");
        ePayBody.setIs_mzf("true");
        //转为md5
        ePayBody.setSign(SignUtil.Body2Md5(ePayBody));
        Map<String, String> bodyMap = EpayBody2Map.beanToMap(ePayBody);
        YZFExecute yzfExecute = new YZFExecute();
        Object res = yzfExecute.executePayment(bodyMap);
        return res;
    }

    @Override
    public OrderInfoResponse queryOrder(Query query) {
        query.setKey(AccountConfig.getAppKey());
        query.setPid(Integer.valueOf(AccountConfig.getAppId()));
        Map<String, String> map = EpayBody2Map.beanToMap(query);
        map.put("url", AccountConfig.getUrl() + "api.php");
        YZFExecute yzfExecute = new YZFExecute();
        String res = yzfExecute.queryOrderInfo(map);
        Map<String, Object> resultMap = JSON.parseObject(res, new TypeReference<Map<String, Object>>() {});
        OrderInfoResponse orderInfoResponse = Map2Bean(resultMap,OrderInfoResponse.class);
        return orderInfoResponse;
    }


}
