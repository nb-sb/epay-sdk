package com.nbsb.epaysdk.api;

import com.nbsb.epaysdk.api.Impl.EPayMZF;
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
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayUnsupportedException;
import com.nbsb.epaysdk.epaybase.enumeration.PayType;
import com.nbsb.epaysdk.epaybase.enumeration.PaymentMethod;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EPayClientHttpTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicInteger orderQueries = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mapi.php", exchange -> write(exchange, "{\"code\":1,\"msg\":\"ok\",\"qrcode\":\"https://qr\",\"out_trade_no\":\"ORD-1\"}"));
        server.createContext("/submit.php", exchange -> write(exchange, "<script>window.location.href='./pay/go?x=1';</script>"));
        server.createContext("/api.php", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("act=orders")) {
                write(exchange, "{\"code\":1,\"msg\":\"ok\",\"count\":1,\"data\":[{\"out_trade_no\":\"ORD-1\",\"status\":1}]}");
            } else if (query != null && query.contains("act=order")) {
                int n = orderQueries.incrementAndGet();
                int status = n >= 2 ? 1 : 0;
                write(exchange, "{\"code\":1,\"msg\":\"ok\",\"out_trade_no\":\"ORD-1\",\"status\":" + status + "}");
            } else if (query != null && query.contains("act=refund")) {
                write(exchange, "{\"code\":1,\"msg\":\"ok\",\"money\":\"1.00\"}");
            } else if (query != null && query.contains("act=query")) {
                write(exchange, "{\"code\":1,\"msg\":\"ok\",\"pid\":1001,\"money\":\"88.50\",\"username\":\"shop\"}");
            } else {
                write(exchange, "{\"code\":0,\"msg\":\"unknown\"}");
            }
        });
        server.createContext("/pay/apisubmit", exchange -> write(exchange, "{\"code\":201,\"msg\":\"ok\",\"qrcode\":\"https://mzf\"}"));
        server.createContext("/pay/chaorder", exchange -> write(exchange,
                "{\"code\":201,\"msg\":\"ok\",\"data\":{\"out_trade_no\":\"ORD-1\",\"status\":1}}"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void yzfPayQueryRefundMerchantOrdersAndPoll() {
        EPayClient client = client(PayType.YZF);
        GetQRCmd cmd = new GetQRCmd("demo", "ORD-1", "1.00", PaymentMethod.ALIPAY,
                "https://shop.example/notify", "https://shop.example/return");

        MapiResponse mapi = client.mapi(cmd);
        assertTrue(mapi.isSuccess());
        assertEquals("https://qr", mapi.getQrcode());

        SubmitResponse submit = client.submit(cmd);
        assertTrue(submit.isSuccess());
        assertTrue(submit.getPayUrl().endsWith("pay/go?x=1"));

        String form = client.buildSubmitForm(cmd);
        assertTrue(form.contains("mapi.php") || form.contains("submit.php"));

        OrderInfoResponse unpaidThenPaid = client.waitUntilPaid(Query.byOutTradeNo("ORD-1"), 2000, 20);
        assertTrue(unpaidThenPaid.isPaid());

        RefundResponse refund = client.refund(RefundCmd.byOutTradeNo("ORD-1", "1.00"));
        assertTrue(refund.isSuccess());

        MerchantInfoResponse merchant = client.queryMerchant();
        assertEquals("88.50", merchant.getMoney());

        OrderListResponse list = client.queryOrders(new OrderListQuery(10));
        assertEquals(1, list.getOrders().size());
        assertTrue(list.getOrders().get(0).isPaid());
    }

    @Test
    void mzfMapiAndQueryNormalizeCodes() {
        EPay mzf = new EPayMZF(config());
        GetQRCmd cmd = new GetQRCmd("demo", "ORD-1", "1.00", PaymentMethod.WXPAY,
                "https://shop.example/notify", "https://shop.example/return");
        MapiResponse mapi = mzf.mapi(cmd);
        assertEquals(1, mapi.getCode());
        assertEquals("https://mzf", mapi.getQrcode());

        OrderInfoResponse order = mzf.queryOrder(Query.byOutTradeNo("ORD-1"));
        assertTrue(order.isPaid());
        assertEquals(1, order.getCode());
        assertThrows(EPayUnsupportedException.class, () -> mzf.refund(RefundCmd.byTradeNo("T1", "1.00")));
        assertThrows(EPayUnsupportedException.class, mzf::queryMerchant);
    }

    @Test
    void factoryCreatesChannel() {
        assertTrue(EPayFactory.create(PayType.YZF, config()) instanceof com.nbsb.epaysdk.api.Impl.EPayYZF);
        assertTrue(EPayFactory.create(PayType.MZF, config()) instanceof EPayMZF);
    }

    private EPayClient client(PayType type) {
        return EPayClient.builder().payType(type).config(config()).build();
    }

    private MerchantConfig config() {
        return MerchantConfig.builder()
                .url(baseUrl)
                .appId("1001")
                .appKey("secret")
                .clientIp("203.0.113.10")
                .build();
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
