
<h1 align="center" style="margin: 30px 0 30px; font-weight: bold;">epay-sdk</h1>
<h4 align="center">彩虹易支付 / 码支付 Java SDK，引入依赖即可调用支付</h4>

##  🐻‍❄️ 介绍

官方文档只有 PHP 示例。这个 SDK 把易支付（YZF）和码支付（MZF）封装成 Java API，和 Spring Boot 集成后配置商户信息即可发起支付、查单、验签。

已发布到 Maven Central：**`io.github.nb-sb:epay-sdk:0.0.1`**。

Java 包名仍是 `com.nbsb.epaysdk`。仓库里的 POM 开发版本仍是 `0.0.1-SNAPSHOT`，请用上面的 Central 坐标依赖已发布制品。

`0.0.1` 不包含此后在 `main` 上的修正：商户密钥日志脱敏、查单不回写调用方对象、退款拒绝 0 元、`spring-boot-starter-web` 改为可选、HTTP 响应用完即关闭。下面的用法按当前仓库源码描述。要让调用方拿到这些行为，需要重新发布。

示例仓库：https://github.com/nb-sb/epay-sdk-example.git

##  🕊️ 快速开始

### 1. 依赖

Maven：

```xml
<dependency>
    <groupId>io.github.nb-sb</groupId>
    <artifactId>epay-sdk</artifactId>
    <version>0.0.1</version>
</dependency>
```

Gradle：

```groovy
implementation 'io.github.nb-sb:epay-sdk:0.0.1'
```

制品页：https://central.sonatype.com/artifact/io.github.nb-sb/epay-sdk/0.0.1

本地调试仍可用 `jar/` 下的包。不要用旧的 `com.nbsb:epay-sdk` 坐标。`jar/` 里的历史包也没有上述修正。

`spring-boot-starter-web` 是可选依赖，不会传递进来。不用 Spring 时，只引本 SDK，用下面的 `EPayClient.builder()` 即可。Spring Boot Web 工程本身已经依赖 `spring-boot-starter-web`，配好 `nbsb.pay.account.appId` 就会自动装配。如果应用没有自己声明 Spring Web，又想用自动装配，需要自行加上 `spring-boot-starter-web`。

### 2. Spring Boot 配置

应用 classpath 上有 Spring Boot，且配置了 `nbsb.pay.account.appId` 后，会自动装配 `EPayClient`。也可继续用 `LoaderConfig` 写入静态 `AccountConfig`。

```yaml
nbsb.pay.type: yzf   # yzf | mzf
nbsb.pay.account.url: https://XXXX.com/
nbsb.pay.account.appId: 1001
nbsb.pay.account.appKey: xxxxxxxxxxxxxxxxx
nbsb.pay.account.clientIp: 203.0.113.10
nbsb.pay.http.connectTimeoutMs: 5000
nbsb.pay.http.responseTimeoutMs: 15000
```

然后直接注入 `EPayClient`（或 `EPay`）。

### 3. 推荐用法：EPayClient

每个商户一份 `MerchantConfig`，不要依赖进程级静态账号。

```java
EPayClient client = EPayClient.builder()
        .payType(PayType.YZF)
        .config(MerchantConfig.builder()
                .url("https://XXXX.com/")
                .appId("1001")
                .appKey("xxxxxxxx")
                .clientIp("203.0.113.10")
                .build())
        .build();

GetQRCmd cmd = new GetQRCmd("测试商品", "20214014211111173712331", "0.10",
        PaymentMethod.ALIPAY, "https://shop.example/notify", "https://shop.example/return");
// 可选：cmd.setDevice(DeviceType.PC); cmd.setClientIp("203.0.113.10"); cmd.setParam("user=9");

MapiResponse mapi = client.mapi(cmd);
OrderInfoResponse order = client.queryOrder(Query.byOutTradeNo(cmd.getOrderNo()));
```

`GetQRCmd` 必填：商品名、商户订单号、金额、支付方式、`notify_url`、`return_url`。金额最多两位小数，且必须大于 0，`0`、`0.0`、`0.00` 会抛 `EPayValidationException`。查询类型：`Query.byTradeNo`（易支付订单号）或 `Query.byOutTradeNo`（商户订单号）。

`queryOrder` 不会修改传入的 `Query`。商户号和密钥只放进发往网关的参数，不会写回这个对象，调用后 `getKey()` / `getPid()` 仍是原值。

### 4. 易支付（YZF）其它 API

```java
SubmitResponse jump = client.submit(cmd);          // 服务端 POST submit.php，解析跳转 URL
String formHtml = client.buildSubmitForm(cmd);     // 浏览器自动 POST 的 HTML 表单

RefundResponse refund = client.refund(RefundCmd.byOutTradeNo(cmd.getOrderNo(), "0.10"));
MerchantInfoResponse merchant = client.queryMerchant();
OrderListResponse recent = client.queryOrders(new OrderListQuery(20));

OrderInfoResponse paid = client.waitUntilPaid(Query.byOutTradeNo(cmd.getOrderNo()), 60_000, 2_000);
```

退款金额和支付金额用同一套规则：最多两位小数，且必须大于 0。`0`、`0.0`、`0.00` 在请求网关之前抛 `EPayValidationException`。

易支付的查单、退款、商户查询、订单列表按网关协议把商户密钥放在 query string 的 `key` 上。SDK 自己的 debug 日志会把 `key` 打成 `***`。生产环境不要把 `org.apache.hc.client5.http.wire` 开到 DEBUG，HttpClient 的报文日志仍会打印完整 URL。

码支付（MZF）只实现 `mapi` 和 `queryOrder`。`submit` / `refund` / `queryMerchant` / `queryOrders` 会抛 `EPayUnsupportedException`。

### 5. 兼容用法：工厂或直接 new

```java
EPay ePay = EPayFactory.create(PayType.MZF, merchantConfig);
MapiResponse mapi = ePay.mapi(cmd);

OrderInfoResponse info = new EPayYZF(merchantConfig).queryOrder(Query.byOutTradeNo("20240421173712331"));
```

无参 `new EPayYZF()` / `new EPayMZF()` 仍可读静态 `AccountConfig`。

### 6. 异步通知（必须验签）

网关 GET 回调 `notify_url`。先验签，再改本地订单，最后返回字面量 `success`。

```java
@RestController
public class BasicController implements EPayInterface {
    private final EPayClient client;

    @GetMapping("/pay/notify/")
    @Override
    public String onPayResult(@RequestParam Map<String, String> params) {
        NotifyPayload notify = client.parseNotify(params); // 签名或 pid 不对会抛 EPaySignException
        if (notify.isPaid()) {
            // persist: notify.getOutTradeNo()
        }
        return NotifyPayload.SUCCESS_ACK; // "success"
    }
}
```

只验签、不抛异常时用 `client.verifyNotify(params)`。`trade_status == TRADE_SUCCESS` 才算支付成功。

| 字段名       | 变量名       | 必填 | 类型   | 示例值                           | 描述                    |
| :----------- | :----------- | :--- | :----- | :------------------------------- | :---------------------- |
| 商户ID       | pid          | 是   | Int    | 1001                             |                         |
| 易支付订单号 | trade_no     | 是   | String | 20160806151343349021             | 易支付订单号            |
| 商户订单号   | out_trade_no | 是   | String | 20160806151343349                | 商户系统内部的订单号    |
| 支付方式     | type         | 是   | String | alipay                           |                         |
| 商品名称     | name         | 是   | String | 测试商品                         |                         |
| 商品金额     | money        | 是   | String | 1.00                             |                         |
| 支付状态     | trade_status | 是   | String | TRADE_SUCCESS                    | 只有TRADE_SUCCESS是成功 |
| 业务扩展参数 | param        | 否   | String |                                  |                         |
| 签名字符串   | sign         | 是   | String | 202cb962ac59075b964b07152d234b70 | 签名算法                |
| 签名类型     | sign_type    | 是   | String | MD5                              | 默认为MD5               |

通知类型：服务器异步通知（notify_url）、页面跳转通知（return_url）。请求方式：GET。

## ⚡ 反馈与交流

有问题可以联系作者，有其他的想法或者有问题都可以联系作者或提 Issue。你也可以在 Issue 查看别人提的问题和给出解决方案。

作者 qq：3500079813

作者微信：扫码加好友拉你进交流群

<img src="./doc/3b3149687e2827ffe4f8d509ca5e50bf_720-3700853.png" style="width:200px;margin: 10px;">
