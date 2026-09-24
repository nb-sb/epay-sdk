# 使用说明

快速开始见 [README](../README.md)。这里只写接入时会踩坑的部分。

## 选择协议

不传 Adapter 时，`Md5Credentials` 选择 `epay-v1`，`RsaCredentials` 选择 `epay-v2`。不探测网址，RSA 失败也不回退 MD5。

| 协议 | 构造 | 凭据 |
|---|---|---|
| V1 参考 | `new EpayV1Adapter()` | MD5，退款成功码 0 |
| MPAY V1 | `new EpayV1Adapter(EpayV1Adapter.Dialect.MPAY)` | MD5，退款成功码 1 |
| V2 参考 | `new EpayV2Adapter()` | 商户 PKCS8 私钥 + 平台 X509 公钥，均至少 2048 位 |
| XArr V2 | `new EpayV2Adapter(EpayV2Adapter.Dialect.XARR)` | RSA，没有退款 |
| 实验 MZF201 | `new MzfLegacyAdapter()` | MD5，只有 API 下单和查单 |

字符串协议名只认 `EPayClient.adapterFor(protocolId)`。`auto` 返回 null，仍按凭据选择。`baseUrl` 保留部署前缀，生产使用 HTTPS。平台公钥来自网关，不能用商户公钥代替。

先看 `capabilities()`。不支持的操作抛 `UNSUPPORTED`，不要先发请求。

## 下单结果

本地表单和已验证通知没有网关业务码，`code()` 为 null。网关真正返回的码原样保留，不要一律当成 `1`，也不要把码支付的 `201` 减 `200`。

`return_url` 只用于展示，不能据此发货。写操作若 `executionUncertain()` 为 true，先按原单号查询，不要换单号重试。

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.model.PaymentAction;
import com.nbsb.epaysdk.model.PaymentScene;

public final class FormExample {
    public static String form(String orderNo) {
        try (EPayClient client = PaymentClients.md5()) {
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
}
```

应用里复用长期客户端，退出时关闭。上面的 try-with-resources 只说明纯 Java 怎么关。

V2 的 `paymentMethod` 是渠道，`PaymentOptions.method` 是调用方式，默认 `web`。JSAPI 用 `subOpenId`，不要用 `openId` 顶替。`scan` 必须带付款码，返回的 `ScanResult` 不是已支付。

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
}
```

## Spring Boot

注入 `EPay` 或 `EPayClient`。已有 `EPay` Bean 时自动装配退让。Starter 不读取密钥文件。

```yaml
epay:
  base-url: ${EPAY_BASE_URL}
  merchant-id: ${EPAY_MERCHANT_ID}
  protocol: auto
  credentials:
    md5-key: ${EPAY_MD5_KEY}
```

RSA 换成 `merchant-private-key` 和 `platform-public-key`，不要和 `md5-key` 混在一起。XArr 把 `protocol` 写成 `epay-v2-xarr`。

## 通知

先验原始参数，业务事务提交成功后再返回 `success`。重复键要拒绝，不要先收成单值 Map。

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
        VerifiedNotification verified = result.data();
        orders.reconcileAndCommit(verified);
        return verified.successAck();
    }
}
```

## 查询、退款、轮询

| 操作 | 请求 |
|---|---|
| `queryOrder` | `OrderReference.byTradeNo` 或 `byOutTradeNo` |
| `refund` | `RefundRequest`。V1 的 `outRefundNo` 必须为 null |
| `queryRefund` | `RefundReference`。V1 不支持 |
| `queryMerchant` | 无参数 |
| `listOrders` | `OrderListRequest`。V1 每页最多 50 条，offset 是 limit 的整数倍 |

`OrderStatus.UNKNOWN` 不是待支付。退款 `ACCEPTED` 只是受理，完成看 `RefundStatus.SUCCEEDED`。

只在 `PENDING` 时轮询，并且调用方必须是 `EPayClient`：

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.OrderPoller;
import com.nbsb.epaysdk.model.GatewayResult;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.OrderResult;
import java.time.Duration;

public final class PollingExample {
    public static GatewayResult<OrderResult> await(EPayClient client, String orderNo) {
        return OrderPoller.awaitPaid(client, OrderReference.byOutTradeNo(orderNo),
                Duration.ofSeconds(30), Duration.ofSeconds(1), 20);
    }
}
```

返回后仍要检查 `success()` 和 `OrderStatus.PAID`。非法的单次超时、轮询间隔和次数是 `VALIDATION`。预算耗尽是 `TIMEOUT`，不是支付失败。

真实商户联调还没做。默认 V1 退款成功码是 0；成功码为 1 的站要显式用 MPAY。
