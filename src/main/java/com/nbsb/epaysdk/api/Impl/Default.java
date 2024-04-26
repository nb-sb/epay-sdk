package com.nbsb.epaysdk.api.Impl;

import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.Query;


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
