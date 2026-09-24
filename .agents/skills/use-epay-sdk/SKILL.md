---
name: use-epay-sdk
description: >
  Write a complete 彩虹易支付 / 易支付 integration with io.github.nb-sb epay-sdk 1.0.0-SNAPSHOT
  (Java 17, package com.nbsb.epaysdk): V1 MD5, V2 RSA, dialects, Spring Boot, pay, query, notify,
  refund, and polling. Use when the user mentions epay-sdk, EPayClient, 彩虹易支付, 易支付对接,
  mapi.php, or /use-epay-sdk. Do not use the published 0.0.1 API.
---

# 用 epay-sdk 写出全部用法

不了解本仓库时，只按本文件写代码。公开包是 `com.nbsb.epaysdk` 的 `api`、`model`、`spi`、`protocol.*`。不要引用 `internal`。不要依赖 Central 的 `0.0.1`，也不要写 `EPayFactory`、`GetQRCmd`、`submit`、`mapi`、`nbsb.pay`。

依赖一律写 `io.github.nb-sb` 的 `1.0.0-SNAPSHOT`。先在 SDK 仓库执行 `mvn -B install`。纯 Java 依赖 `epay-sdk`，Spring Boot 只依赖 `epay-sdk-spring-boot-starter`。Gradle 要声明 `mavenLocal()`。不要写成尚未发布的 `1.0.0`。

每个商户一个长期客户端。`baseUrl` 保留部署前缀，生产用 HTTPS，不含用户信息、query、fragment。密钥从调用方配置注入。不要写默认密钥，不要写 `127.0.0.1`。金额用大于 0、最多两位小数的 `BigDecimal`。

不传 Adapter 时，MD5 选择 `epay-v1`（退款成功码 0），RSA 选择 `epay-v2`。不探测网络。成功码为 1 的 V1 站必须显式用 MPAY。调用前看 `capabilities()`，不支持就不要发请求。

`success()` 只表示这次调用被接受。本地表单和已验证通知的 `code()` 为 null。`return_url` 不能发货。写操作 `executionUncertain()` 为 true 时按原单号查询，不要换单号重试。不要记录密钥、PEM、签名、付款码或完整支付链接。

## 客户端

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.model.RsaCredentials;
import com.nbsb.epaysdk.protocol.epayv1.EpayV1Adapter;
import com.nbsb.epaysdk.protocol.epayv2.EpayV2Adapter;
import com.nbsb.epaysdk.protocol.mzf.MzfLegacyAdapter;

public final class PaymentClients {
    public static EPayClient md5() {
        return EPayClient.builder()
                .config(MerchantConfig.builder()
                        .baseUrl(System.getenv("EPAY_BASE_URL"))
                        .merchantId(System.getenv("EPAY_MERCHANT_ID"))
                        .credentials(new Md5Credentials(System.getenv("EPAY_MD5_KEY")))
                        .build())
                .build();
    }

    public static EPayClient rsa() {
        return EPayClient.builder()
                .config(MerchantConfig.builder()
                        .baseUrl(System.getenv("EPAY_BASE_URL"))
                        .merchantId(System.getenv("EPAY_MERCHANT_ID"))
                        .credentials(RsaCredentials.fromPem(
                                System.getenv("EPAY_MERCHANT_PRIVATE_KEY"),
                                System.getenv("EPAY_PLATFORM_PUBLIC_KEY")))
                        .build())
                .build();
    }

    public static EPayClient mpay() {
        return EPayClient.builder()
                .config(MerchantConfig.builder()
                        .baseUrl(System.getenv("EPAY_BASE_URL"))
                        .merchantId(System.getenv("EPAY_MERCHANT_ID"))
                        .credentials(new Md5Credentials(System.getenv("EPAY_MD5_KEY")))
                        .build())
                .adapter(new EpayV1Adapter(EpayV1Adapter.Dialect.MPAY))
                .build();
    }

    public static EPayClient xarr() {
        return EPayClient.builder()
                .config(MerchantConfig.builder()
                        .baseUrl(System.getenv("EPAY_BASE_URL"))
                        .merchantId(System.getenv("EPAY_MERCHANT_ID"))
                        .credentials(RsaCredentials.fromPem(
                                System.getenv("EPAY_MERCHANT_PRIVATE_KEY"),
                                System.getenv("EPAY_PLATFORM_PUBLIC_KEY")))
                        .build())
                .adapter(new EpayV2Adapter(EpayV2Adapter.Dialect.XARR))
                .build();
    }

    public static EPayClient mzf() {
        return EPayClient.builder()
                .config(MerchantConfig.builder()
                        .baseUrl(System.getenv("EPAY_BASE_URL"))
                        .merchantId(System.getenv("EPAY_MERCHANT_ID"))
                        .credentials(new Md5Credentials(System.getenv("EPAY_MD5_KEY")))
                        .build())
                .adapter(new MzfLegacyAdapter())
                .build();
    }
}
```

`adapterFor` 接受 `auto`、`epay-v1`、`epay-v1-mpay`、`epay-v2`、`epay-v2-xarr`、`mzf-legacy201`。`auto` 返回 null，不要再调用 `adapter`。`epay-v2` 和 `epay-v2-xarr` 只能配 RSA，其余协议名只能配 MD5。RSA 只接受 `BEGIN PRIVATE KEY` 和 `BEGIN PUBLIC KEY`。

## 下单

页面支付不抓 HTML。接口支付必须传真实 `clientIp`。MZF201 只支持 API，并且不能带 `clientIp`、`device`、`param` 和非默认支付选项。

```java
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.PaymentRequest;
import com.nbsb.epaysdk.model.PaymentResult;
import com.nbsb.epaysdk.model.PaymentScene;
import java.math.BigDecimal;

public final class Payments {
    public static GatewayResult<PaymentResult> create(
            EPay client, String orderNo, String clientIp, PaymentScene scene) {
        return client.createPayment(PaymentRequest.builder()
                .outTradeNo(orderNo)
                .name("演示商品")
                .amount(new BigDecimal("1.00"))
                .paymentMethod("alipay")
                .notifyUrl("https://shop.example/pay/notify")
                .returnUrl("https://shop.example/pay/return")
                .clientIp(clientIp)
                .scene(scene)
                .build());
    }
}
```

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.model.PaymentAction;
import com.nbsb.epaysdk.model.PaymentScene;

public final class FormExample {
    public static String form(EPayClient client, String orderNo) {
        var result = Payments.create(client, orderNo, null, PaymentScene.BROWSER_FORM);
        if (!result.success()) {
            throw new IllegalStateException("下单被网关拒绝");
        }
        return result.data().actions().stream()
                .filter(PaymentAction.Form.class::isInstance)
                .map(PaymentAction.Form.class::cast)
                .map(PaymentAction.Form::html)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("网关未提供表单动作"));
    }
}
```

按具体动作类型读取，不要执行 HTML、脚本或插件参数。`QrCode.content()` 不是图片地址。`QrImage` 才是图片 URL。

```java
import com.nbsb.epaysdk.model.PaymentAction;
import com.nbsb.epaysdk.model.PaymentResult;

public final class ActionDispatch {
    public static String locate(PaymentResult result) {
        PaymentAction action = result.actions().get(0);
        if (action instanceof PaymentAction.Redirect redirect) {
            return redirect.url().toString();
        }
        if (action instanceof PaymentAction.QrCode qr) {
            return qr.content();
        }
        if (action instanceof PaymentAction.QrImage image) {
            return image.url().toString();
        }
        if (action instanceof PaymentAction.UrlScheme scheme) {
            return scheme.url();
        }
        if (action instanceof PaymentAction.JsApi jsapi) {
            return jsapi.payload();
        }
        if (action instanceof PaymentAction.App app) {
            return app.payload();
        }
        if (action instanceof PaymentAction.WechatPlugin plugin) {
            return plugin.payload();
        }
        if (action instanceof PaymentAction.MiniProgram mini) {
            return mini.payload();
        }
        if (action instanceof PaymentAction.ScanResult scan) {
            return scan.payload();
        }
        if (action instanceof PaymentAction.Form form) {
            return form.html();
        }
        return ((PaymentAction.Html) action).html();
    }
}
```

## V2 的 JSAPI 与付款码

`paymentMethod` 是渠道，`PaymentOptions.method` 是 `web`、`jump`、`jsapi`、`app`、`scan`、`applet`。用 `subOpenId`，不要传 `openId`。`scan` 的返回不是已支付。

```java
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.PaymentOptions;
import com.nbsb.epaysdk.model.PaymentRequest;
import com.nbsb.epaysdk.model.PaymentResult;
import com.nbsb.epaysdk.model.PaymentScene;
import java.math.BigDecimal;

public final class WechatPayments {
    public static GatewayResult<PaymentResult> jsapi(
            EPay client, String orderNo, String clientIp,
            String subAppId, String subOpenId, Boolean isApplet) {
        return client.createPayment(PaymentRequest.builder()
                .outTradeNo(orderNo)
                .name("演示商品")
                .amount(new BigDecimal("1.00"))
                .paymentMethod("wxpay")
                .notifyUrl("https://shop.example/pay/notify")
                .returnUrl("https://shop.example/pay/return")
                .clientIp(clientIp)
                .scene(PaymentScene.API)
                .options(PaymentOptions.builder()
                        .method("jsapi")
                        .subAppId(subAppId)
                        .subOpenId(subOpenId)
                        .isApplet(isApplet)
                        .build())
                .build());
    }

    public static GatewayResult<PaymentResult> scan(
            EPay client, String orderNo, String clientIp, String authCode) {
        return client.createPayment(PaymentRequest.builder()
                .outTradeNo(orderNo)
                .name("演示商品")
                .amount(new BigDecimal("1.00"))
                .paymentMethod("wxpay")
                .notifyUrl("https://shop.example/pay/notify")
                .returnUrl("https://shop.example/pay/return")
                .clientIp(clientIp)
                .scene(PaymentScene.API)
                .options(PaymentOptions.builder().method("scan").authCode(authCode).build())
                .build());
    }
}
```

## 查询、退款、商户、订单列表

V1 退款的 `outRefundNo` 传 null。V2 退款要传稳定的商户退款号。XArr 和 MZF 没有退款。V1 没有退款查询。V1 订单列表 `limit` 最大 50，`offset` 必须是 `limit` 的整数倍。`UNKNOWN` 不是待支付，退款 `ACCEPTED` 不是已退完。

```java
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.Capability;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.MerchantResult;
import com.nbsb.epaysdk.model.OrderListRequest;
import com.nbsb.epaysdk.model.OrderListResult;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.OrderResult;
import com.nbsb.epaysdk.model.RefundReference;
import com.nbsb.epaysdk.model.RefundRequest;
import com.nbsb.epaysdk.model.RefundResult;
import java.math.BigDecimal;
import java.time.Duration;

public final class MerchantOps {
    public static GatewayResult<OrderResult> query(EPay client, String orderNo) {
        return client.queryOrder(OrderReference.byOutTradeNo(orderNo), Duration.ofSeconds(2));
    }

    public static GatewayResult<RefundResult> refundV1(EPay client, String orderNo) {
        if (!client.capabilities().contains(Capability.REFUND)) {
            throw EPayException.unsupported();
        }
        return client.refund(new RefundRequest(
                OrderReference.byOutTradeNo(orderNo), new BigDecimal("1.00"), null));
    }

    public static GatewayResult<RefundResult> refundV2(EPay client, String orderNo, String refundNo) {
        return client.refund(new RefundRequest(
                OrderReference.byOutTradeNo(orderNo), new BigDecimal("1.00"), refundNo));
    }

    public static GatewayResult<RefundResult> refundQuery(EPay client, String refundNo) {
        return client.queryRefund(RefundReference.byOutRefundNo(refundNo));
    }

    public static GatewayResult<MerchantResult> merchant(EPay client) {
        return client.queryMerchant();
    }

    public static GatewayResult<OrderListResult> firstPage(EPay client) {
        return client.listOrders(new OrderListRequest(0, 20));
    }
}
```

## 通知

用原始多值参数验签。事务提交成功后才返回 `successAck()`。同一商户订单号必须幂等。

```java
import com.nbsb.epaysdk.api.EPay;
import com.nbsb.epaysdk.model.NotificationRequest;
import com.nbsb.epaysdk.model.OrderStatus;
import com.nbsb.epaysdk.model.VerifiedNotification;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public final class NotifyHandler {
    public interface Orders {
        void reconcileAndCommit(VerifiedNotification notification);
    }

    public static String handle(EPay client, Map<String, String[]> raw, Orders orders) {
        var multiValue = raw.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey, entry -> Arrays.asList(entry.getValue())));
        var result = client.verifyNotification(NotificationRequest.fromMultiValue(multiValue));
        if (!result.success() || result.data().status() != OrderStatus.PAID) {
            throw new IllegalStateException("通知未确认支付成功");
        }
        orders.reconcileAndCommit(result.data());
        return result.data().successAck();
    }
}
```

## 轮询

只把 `EPayClient` 传给 `OrderPoller`。返回后仍要确认 `PAID`。预算耗尽抛 `TIMEOUT`，不要据此重新下单。

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.OrderPoller;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.OrderResult;
import com.nbsb.epaysdk.model.OrderStatus;
import java.time.Duration;

public final class PollingExample {
    public static OrderResult awaitPaid(EPayClient client, String orderNo) {
        GatewayResult<OrderResult> result = OrderPoller.awaitPaid(
                client, OrderReference.byOutTradeNo(orderNo),
                Duration.ofSeconds(30), Duration.ofSeconds(1), 20);
        if (!result.success() || result.data().status() != OrderStatus.PAID) {
            throw new IllegalStateException("订单尚未支付");
        }
        return result.data();
    }
}
```

## Spring Boot

只加 starter。注入 `EPayClient`。已有 `EPay` Bean 时自动装配退让。不要写 `classpath:` 密钥路径。

```yaml
epay:
  base-url: ${EPAY_BASE_URL}
  merchant-id: ${EPAY_MERCHANT_ID}
  protocol: auto
  credentials:
    md5-key: ${EPAY_MD5_KEY}
```

RSA 删掉 `md5-key`，改成 `merchant-private-key` 与 `platform-public-key`。XArr 写 `protocol: epay-v2-xarr`，MPAY 写 `epay-v1-mpay`，MZF 写 `mzf-legacy201`。

```java
import com.nbsb.epaysdk.api.EPayClient;
import org.springframework.stereotype.Service;

@Service
public class PayService {
    private final EPayClient client;

    public PayService(EPayClient client) {
        this.client = client;
    }

    public EPayClient client() {
        return client;
    }
}
```

应用退出时关闭自己创建的客户端。外部 `HttpTransport` 默认由调用方关闭；只有 `.transport(transport, true)` 才交给客户端关。Spring 里的传输 Bean 不要移交。
