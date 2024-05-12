package com.nbsb.epaysdk.api.Impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.entity.reponse.MZFOrderInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.epaybase.Impl.MZFExecute;
import com.nbsb.epaysdk.epaybase.Impl.YZFExecute;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.properties.AccountConfig;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;

import java.util.Map;

import static com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map.*;

public class EPayMZF implements EPay {
    /**
     * API接口支付
     * 此接口可用于服务器后端发起支付请求，会返回支付二维码链接或支付跳转url。
     * <p>
     * URL地址：https://XXX.com/mapi.php
     * <p>
     * 请求方式：POST
     */
    @Override
    public MapiResponse mapi(GetQRCmd cmd) {
        EPayBody ePayBody = cmd2EPayBody(cmd);
        ePayBody.setUrl(AccountConfig.getUrl() + "pay/apisubmit");
        //转为md5
        ePayBody.setSign(SignUtil.Body2Md5(ePayBody));
        Map<String, String> bodyMap = EpayBody2Map.beanToMap(ePayBody);
        YZFExecute yzfExecute = new YZFExecute();
        Object res = yzfExecute.executePayment(bodyMap);
        // 使用 FastJSON 解析 JSON 字符串为一个 Map 对象
        Map<String, Object> resultMap = JSON.parseObject(res.toString(), new TypeReference<Map<String, Object>>() {});
        MapiResponse mapiResponse = Map2Bean(resultMap, MapiResponse.class);
        mapiResponse.setCode(mapiResponse.getCode() - 200);
        mapiResponse.setMsg(mapiResponse.getMsg());
        return mapiResponse;
    }

    @Override
    public Object submit(GetQRCmd cmd) {
        throw new RuntimeException("方法未实现，或不推荐此使用方式");
    }

    @Override
    public OrderInfoResponse queryOrder(Query query) {
        query.setKey(AccountConfig.getAppKey());
        query.setPid(Integer.valueOf(AccountConfig.getAppId()));
        Map<String, String> map = EpayBody2Map.beanToMap(query);
        map.put("url", AccountConfig.getUrl() + "pay/chaorder");
        MZFExecute mzfExecute = new MZFExecute();
        String res = mzfExecute.queryOrderInfo(map);
        Map<String, Object> resultMap = JSON.parseObject(res, new TypeReference<Map<String, Object>>() {});
        MZFOrderInfoResponse mzfOrderInfoResponse = Map2Bean(resultMap, MZFOrderInfoResponse.class);
        Object data = mzfOrderInfoResponse.getData();
        //将data转为map
        Map<String, Object> resultMap1 = JSON.parseObject(data.toString(), new TypeReference<Map<String, Object>>() {});
        OrderInfoResponse mapiResponse = Map2Bean(resultMap1, OrderInfoResponse.class);
        mapiResponse.setCode((Integer) resultMap.get("code") - 200);
        mapiResponse.setMsg((String) resultMap.get("msg"));
        return mapiResponse;
    }


}
