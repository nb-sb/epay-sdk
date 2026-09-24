<h1 align="center">epay-sdk</h1>
<h4 align="center">彩虹易支付 Java SDK</h4>

一个客户端对接易支付 V1（MD5）和 V2（RSA），并带 Spring Boot 自动配置。按下面的 `1.0.0-SNAPSHOT` 使用。Central 上的 `0.0.1` 是旧坐标，不要依赖它。

## 安装

Java 17+。在本仓库安装到本地仓库：

```bash
mvn -B install
```

纯 Java 项目：

```xml
<dependency>
  <groupId>io.github.nb-sb</groupId>
  <artifactId>epay-sdk</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Spring Boot 只加 starter，不要再声明核心：

```xml
<dependency>
  <groupId>io.github.nb-sb</groupId>
  <artifactId>epay-sdk-spring-boot-starter</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Gradle 同样使用 `1.0.0-SNAPSHOT`，并加上 `mavenLocal()`：

```groovy
repositories {
    mavenLocal()
}
dependencies {
    implementation 'io.github.nb-sb:epay-sdk:1.0.0-SNAPSHOT'
}
```

## 快速开始

密钥从应用自己的配置注入。MD5 自动走 V1，RSA 自动走 V2。

```java
import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.model.RsaCredentials;

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
}
```

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

页面支付用 `PaymentScene.BROWSER_FORM`。接口支付用 `PaymentScene.API`，并传入真实客户端 IP。`success()` 只表示这次调用被接受，不表示已经付款。

方言、Spring、通知、退款和轮询见 [使用说明](docs/usage.md)。

## 给Agent

```text
Clone https://github.com/nb-sb/epay-sdk and follow the instructions in .agents/skills/use-epay-sdk/SKILL.md to install the use-epay-sdk skill and integrate epay-sdk into this project.
```

## 构建

```bash
mvn -B verify
```

## 许可

[Apache License 2.0](LICENSE)

## ⚡ 反馈与交流

有问题可以联系作者，有其他的想法或者有问题都可以联系作者或提 Issue。你也可以在 Issue 查看别人提的问题和给出解决方案。

作者 qq：3500079813

作者微信：扫码加好友拉你进交流群

<img src="./doc/3b3149687e2827ffe4f8d509ca5e50bf_720-3700853.png" style="width:200px;margin: 10px;">
