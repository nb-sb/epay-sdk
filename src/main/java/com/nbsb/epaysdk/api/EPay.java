package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.entity.reponse.MapiResponse;
import com.nbsb.epaysdk.api.entity.reponse.MerchantInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderInfoResponse;
import com.nbsb.epaysdk.api.entity.reponse.OrderListResponse;
import com.nbsb.epaysdk.api.entity.reponse.RefundResponse;
import com.nbsb.epaysdk.api.entity.reponse.SubmitResponse;
import com.nbsb.epaysdk.api.entity.request.GetQRCmd;
import com.nbsb.epaysdk.api.entity.request.OrderListQuery;
import com.nbsb.epaysdk.api.entity.request.Query;
import com.nbsb.epaysdk.api.entity.request.RefundCmd;
import com.nbsb.epaysdk.core.notify.NotifyPayload;

import java.util.Map;

/**
 * Payment API a channel must implement.
 */
public interface EPay {

    MapiResponse mapi(GetQRCmd cmd);

    SubmitResponse submit(GetQRCmd cmd);

    OrderInfoResponse queryOrder(Query query);

    RefundResponse refund(RefundCmd cmd);

    MerchantInfoResponse queryMerchant();

    OrderListResponse queryOrders(OrderListQuery query);

    boolean verifyNotify(Map<String, String> params);

    NotifyPayload parseNotify(Map<String, String> params);

    String buildSubmitForm(GetQRCmd cmd);

    OrderInfoResponse waitUntilPaid(Query query, long timeoutMs, long intervalMs);
}
