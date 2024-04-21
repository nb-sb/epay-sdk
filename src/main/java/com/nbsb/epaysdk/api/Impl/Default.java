package com.nbsb.epaysdk.api.Impl;

import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.epaybase.bean.EPayBody;
import com.nbsb.epaysdk.epaybase.common.util.EpayBody2Map;
import com.nbsb.epaysdk.epaybase.sign.SignUtil;
import com.sun.org.apache.regexp.internal.REUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


public class Default implements EPay {


    @Override
    public Object mapi(GetQRCmd ePayBody) {
        throw new RuntimeException("不应该使用这个默认的方法！");
    }

    @Override
    public Object submit(GetQRCmd ePayBody) {
        throw new RuntimeException("不应该使用这个默认的方法！");
    }

    @Override
    public Object queryOrder(Query query) {
        throw new RuntimeException("不应该使用这个默认的方法！");
    }
}
