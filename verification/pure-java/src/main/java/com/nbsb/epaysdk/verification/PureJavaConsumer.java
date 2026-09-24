package com.nbsb.epaysdk.verification;

import com.nbsb.epaysdk.api.EPayClient;
import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.api.OrderPoller;
import com.nbsb.epaysdk.model.NotificationRequest;
import com.nbsb.epaysdk.spi.HttpTransport;
import com.nbsb.epaysdk.spi.TransportRequest;
import com.nbsb.epaysdk.spi.TransportResponse;
import com.nbsb.epaysdk.model.HttpOptions;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.MerchantConfig;
import com.nbsb.epaysdk.model.OrderReference;
import com.nbsb.epaysdk.model.OrderStatus;
import com.nbsb.epaysdk.model.PaymentAction;
import com.nbsb.epaysdk.model.PaymentRequest;
import com.nbsb.epaysdk.model.PaymentScene;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

public final class PureJavaConsumer {
  // 固定的虚构数据，仅用于本进程的环回服务器，不读取环境变量或真实商户配置。
  private static final String MERCHANT_ID = "10001";
  private static final String TEST_KEY = "pure-java-fixture-not-a-real-secret";
  private static final String ORDER_NO = "pure-java-order-001";
  private static final String TRADE_NO = "local-trade-001";
  private static final BigDecimal AMOUNT = new BigDecimal("1.00");

  private PureJavaConsumer() {}

  public static void main(String[] args) throws Exception {
    require(args.length == 4, "需要 SDK jar、消费者 jar、隔离仓库和 README 四个路径参数");
    verifyClasspath(Path.of(args[0]), Path.of(args[1]), Path.of(args[2]));
    verifyLocalPaymentAndQuery();
    verifyBorrowedTransportNotificationAndPolling();
    compileReadmeExamples(Path.of(args[3]), Path.of(args[1]).toRealPath().getParent());
    System.out.println("独立纯 Java 消费者验收通过：无 Spring/测试库，默认 HTTP、本地表单、自定义传输查单、通知、轮询、资源所有权及文档中的 Java 示例编译。");
  }

  private static void verifyClasspath(Path sdkJar, Path consumerJar, Path repository)
      throws Exception {
    sdkJar = sdkJar.toRealPath();
    consumerJar = consumerJar.toRealPath();
    repository = repository.toRealPath();
    require(codeSource(EPayClient.class).equals(sdkJar), "SDK 必须从本次安装的二进制 jar 加载");
    require(codeSource(PureJavaConsumer.class).equals(consumerJar), "消费者必须从构建的 jar 加载");

    List<String> forbiddenPackages =
        List.of(
            "org/springframework/",
            "com/nbsb/epaysdk/spring/",
            "org/junit/",
            "junit/",
            "org/opentest4j/",
            "org/apiguardian/",
            "org/hamcrest/",
            "org/mockito/",
            "org/testng/",
            "com/alibaba/fastjson2/",
            "lombok/");
    var jars = new HashSet<Path>();
    String[] entries = System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator), -1);
    for (String entry : entries) {
      require(!entry.isBlank(), "classpath 不得含空路径或当前目录");
      Path jarPath = Path.of(entry).toRealPath();
      require(Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar"),
          "classpath 只能包含 jar，不能包含源码、classes 或 test-classes 目录");
      require(jarPath.equals(consumerJar) || jarPath.startsWith(repository),
          "依赖必须来自工作区隔离仓库");
      require(jars.add(jarPath), "classpath 不得重复包含同一个 jar");
      try (var jar = new JarFile(jarPath.toFile())) {
        var manifest = jar.getManifest();
        require(manifest == null || manifest.getMainAttributes().getValue("Class-Path") == null,
            "不得通过 jar manifest 扩展验收 classpath");
        for (var item : jar.stream().toList()) {
          String name = item.getName().replaceFirst("^META-INF/versions/[0-9]+/", "");
          if (name.endsWith(".class")) {
            require(forbiddenPackages.stream().noneMatch(name::startsWith),
                "运行时 jar 混入 Spring、Starter、测试库、fastjson2 或 Lombok");
          }
        }
      }
      System.out.println("运行时 jar：" + jarPath);
    }
    require(jars.contains(sdkJar) && jars.contains(consumerJar), "缺少 SDK 或消费者 jar");

    // 只接受 ClassNotFoundException，不能把依赖损坏引起的 LinkageError 当作类缺失。
    for (String name : List.of(
        "org.springframework.core.SpringVersion",
        "org.springframework.context.ApplicationContext",
        "org.springframework.beans.factory.BeanFactory",
        "org.springframework.boot.SpringApplication",
        "org.springframework.web.servlet.DispatcherServlet",
        "com.nbsb.epaysdk.spring.EPayAutoConfiguration",
        "org.junit.jupiter.api.Test",
        "org.junit.platform.launcher.Launcher",
        "org.junit.Test",
        "org.mockito.Mockito",
        "org.testng.TestNG",
        "com.alibaba.fastjson2.JSON",
        "lombok.Data")) {
      try {
        Class.forName(name, false, ClassLoader.getSystemClassLoader());
        throw new AssertionError("不应能加载隔离范围外的类：" + name);
      } catch (ClassNotFoundException expected) {
        // 主动确认缺失，而不只是相信 POM 或依赖树。
      }
    }
    System.out.println("运行环境：Java " + Runtime.version() + "；classpath 隔离检查通过。");
  }

  private static Path codeSource(Class<?> type) throws Exception {
    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
  }

  private static void verifyLocalPaymentAndQuery() throws Exception {
    var requests = new AtomicInteger();
    var serverFailure = new AtomicReference<Throwable>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try {
      server.createContext("/", exchange -> handleQuery(exchange, requests, serverFailure));
      server.start();
      String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/gateway/";
      var config = MerchantConfig.builder()
          .baseUrl(baseUrl)
          .merchantId(MERCHANT_ID)
          .credentials(new Md5Credentials(TEST_KEY))
          .build();
      Duration budget = Duration.ofSeconds(5);
      // 不注入自定义传输，实际构造并使用 SDK 默认的 Apache HttpClient 传输。
      EPayClient client = EPayClient.builder()
          .config(config)
          .httpOptions(new HttpOptions(budget, budget, budget, budget))
          .build();
      try (client) {
        require("epay-v1".equals(client.protocolId()), "MD5 凭据应自动选择 V1");
        require(requests.get() == 0, "客户端初始化不得探测网络");
        verifyForm(client, baseUrl);
        require(requests.get() == 0, "README 的 BROWSER_FORM 必须在本地生成，不能请求网关");

        var result = client.queryOrder(OrderReference.byOutTradeNo(ORDER_NO));
        verifyServerFailure(serverFailure);
        require(requests.get() == 1, "实际 HTTP 查单必须恰好一次");
        require(result.success() && "1".equals(result.code()), "本地查单操作应成功并保留原始码");
        var order = result.data();
        require(order != null, "成功查单必须返回订单数据");
        require(ORDER_NO.equals(order.outTradeNo()) && TRADE_NO.equals(order.tradeNo()), "订单号映射错误");
        require(MERCHANT_ID.equals(order.merchantId()), "商户号映射错误");
        require(AMOUNT.compareTo(order.amount()) == 0, "订单金额映射错误");
        require(order.status() == OrderStatus.PAID && "1".equals(order.rawStatus()), "支付状态映射错误");
        require("alipay".equals(order.paymentMethod()), "支付渠道映射错误");
      }
      // try-with-resources 已关闭默认传输；再次关闭应无异常，后续调用不得发 HTTP。
      client.close();
      try {
        client.queryOrder(OrderReference.byOutTradeNo(ORDER_NO));
        throw new AssertionError("关闭后的客户端仍接受查询");
      } catch (EPayException expected) {
        require(expected.kind() == EPayException.Kind.CLOSED, "关闭后必须报告 CLOSED");
      }
      verifyServerFailure(serverFailure);
      require(requests.get() == 1, "关闭及关闭后的调用不得额外发送 HTTP 请求");
    } finally {
      // 所有成功和失败路径都停止服务器，不用 System.exit 掩盖未关闭的非守护线程。
      server.stop(0);
    }
  }

  private static void verifyForm(EPayClient client, String baseUrl) {
    var result = client.createPayment(PaymentRequest.builder()
        .outTradeNo(ORDER_NO)
        .name("演示商品 & \"仅本地\"")
        .amount(AMOUNT)
        .paymentMethod("alipay")
        .notifyUrl(baseUrl + "notify")
        .returnUrl(baseUrl + "return")
        .clientIp(null)
        .scene(PaymentScene.BROWSER_FORM)
        .build());
    require(result.success() && result.data() != null, "本地表单生成失败");
    require(ORDER_NO.equals(result.data().outTradeNo()), "表单结果丢失商户订单号");
    String html = result.data().actions().stream()
        .filter(PaymentAction.Form.class::isInstance)
        .map(PaymentAction.Form.class::cast)
        .map(PaymentAction.Form::html)
        .findFirst()
        .orElseThrow(() -> new AssertionError("未返回 README 示例所需的 Form 动作"));
    require(html.contains("method=\"post\""), "表单必须使用 POST");
    require(html.contains("action=\"" + baseUrl + "submit.php\""), "表单必须保留部署路径前缀");
    require(html.contains("name=\"pid\" value=\"" + MERCHANT_ID + "\""), "表单缺少商户号");
    require(html.contains("name=\"out_trade_no\" value=\"" + ORDER_NO + "\""), "表单缺少订单号");
    require(html.contains("name=\"money\" value=\"1.00\""), "表单金额错误");
    require(html.contains("演示商品 &amp; &quot;仅本地&quot;"), "表单必须转义业务文本");
    require(html.contains("name=\"sign_type\" value=\"MD5\""), "表单缺少签名类型");
    require(Pattern.compile("name=\"sign\" value=\"[0-9a-f]{32}\"").matcher(html).find(), "表单缺少 MD5 签名");
    require(!html.contains(TEST_KEY), "表单不得泄露原始密钥");
    // 只检查字符串，不提交表单、不打开浏览器、不输出表单或签名。
  }

  private static void handleQuery(HttpExchange exchange, AtomicInteger requests,
      AtomicReference<Throwable> serverFailure) throws IOException {
    try {
      require(requests.incrementAndGet() == 1, "本地服务器收到多余请求");
      require("GET".equals(exchange.getRequestMethod()), "查单必须使用 GET");
      require("/gateway/api.php".equals(exchange.getRequestURI().getPath()), "查单端点或部署前缀错误");
      require(exchange.getRemoteAddress().getAddress().isLoopbackAddress(), "只允许环回请求");
      require(queryParameters(exchange.getRequestURI().getRawQuery()).equals(Map.of(
          "act", "order", "pid", MERCHANT_ID, "key", TEST_KEY, "out_trade_no", ORDER_NO)),
          "查单参数不符合 V1 契约");
      require(exchange.getRequestBody().read() == -1, "GET 查单不应携带请求体");
      String body = """
          {"code":1,"msg":"ok","pid":"%s","trade_no":"%s",\
          "out_trade_no":"%s","money":"1.00","status":1,"type":"alipay"}
          """.formatted(MERCHANT_ID, TRADE_NO, ORDER_NO);
      byte[] response = body.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
    } catch (AssertionError | RuntimeException | IOException failure) {
      serverFailure.compareAndSet(null, failure);
      // 不回显可能包含认证参数的请求；失败通过主线程的断言传播。
      exchange.sendResponseHeaders(500, -1);
    } finally {
      exchange.close();
    }
  }

  private static Map<String, String> queryParameters(String query) {
    require(query != null && !query.isBlank(), "查单缺少查询串");
    var values = new HashMap<String, String>();
    for (String pair : query.split("&", -1)) {
      String[] parts = pair.split("=", 2);
      require(parts.length == 2, "查询参数格式错误");
      String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
      String value = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
      require(values.putIfAbsent(key, value) == null, "查单不得发送重复参数");
    }
    return values;
  }

  private static void verifyBorrowedTransportNotificationAndPolling() {
    var calls = new AtomicInteger();
    var closes = new AtomicInteger();
    List<String> statuses = List.of("1", "0", "1", "0", "1");
    HttpTransport transport = new HttpTransport() {
      @Override
      public TransportResponse execute(TransportRequest request) {
        require(closes.get() == 0, "借用传输不得提前关闭");
        require("GET".equals(request.method()), "自定义传输只允许查单");
        require("https://local.invalid/prefix/api.php".equals(request.uri().toString()), "部署前缀丢失");
        require(request.queryParameters().isEmpty(), "查询串不得重复承载参数");
        require(request.parameters().equals(Map.of("act", "order", "pid", MERCHANT_ID,
            "key", TEST_KEY, "out_trade_no", ORDER_NO)), "自定义传输收到错误的查单参数");
        require(!request.timeout().isNegative() && !request.timeout().isZero()
            && request.timeout().compareTo(Duration.ofSeconds(5)) <= 0, "查询预算未传递");
        int index = calls.getAndIncrement();
        require(index < statuses.size(), "不得多发查询或自动重试写操作");
        return new TransportResponse(200, """
            {"code":"1","pid":"%s","trade_no":"%s","out_trade_no":"%s",\
            "money":"1.00","status":"%s","type":"alipay"}
            """.formatted(MERCHANT_ID, TRADE_NO, ORDER_NO, statuses.get(index)));
      }

      @Override
      public void close() {
        closes.incrementAndGet();
      }
    };
    var config = MerchantConfig.builder().baseUrl("https://local.invalid/prefix")
        .merchantId(MERCHANT_ID).credentials(new Md5Credentials(TEST_KEY)).build();
    Duration budget = Duration.ofSeconds(5);
    EPayClient client = EPayClient.builder().config(config).transport(transport)
        .httpOptions(new HttpOptions(budget, budget, budget, budget)).build();
    var reference = OrderReference.byOutTradeNo(ORDER_NO);
    try {
      try (client) {
        var order = client.queryOrder(reference);
        require(order.success() && order.data().status() == OrderStatus.PAID, "自定义传输查单失败");
        verifyNotification(client);
        require(calls.get() == 1, "通知验证必须完全本地完成");
        var paid = OrderPoller.awaitPaid(client, reference, budget, Duration.ofMillis(1), 2);
        require(paid.success() && paid.data().status() == OrderStatus.PAID && calls.get() == 3,
            "轮询必须从待支付查询到已支付");
        expect(EPayException.Kind.TIMEOUT,
            () -> OrderPoller.awaitPaid(client, reference, budget, Duration.ofMillis(1), 1));
        require(calls.get() == 4 && closes.get() == 0, "轮询次数耗尽不得继续查询或关闭传输");
        require(client.queryOrder(reference).data().status() == OrderStatus.PAID,
            "轮询结束后客户端必须仍可使用");
      }
      client.close();
      expect(EPayException.Kind.CLOSED, () -> client.queryOrder(reference));
      require(calls.get() == 5 && closes.get() == 0, "客户端不得关闭外部借用传输或额外查询");
    } finally {
      client.close();
      transport.close();
    }
    require(closes.get() == 1, "外部传输必须由调用方关闭且仅关闭一次");
  }

  private static void verifyNotification(EPayClient client) {
    var raw = new LinkedHashMap<String, List<String>>();
    raw.put("pid", List.of(MERCHANT_ID));
    raw.put("trade_no", List.of(TRADE_NO));
    raw.put("out_trade_no", List.of(ORDER_NO));
    raw.put("money", new ArrayList<>(List.of("1.00")));
    raw.put("name", List.of("演示 &+="));
    raw.put("param", List.of(" 原样 &+= "));
    raw.put("trade_status", List.of("TRADE_SUCCESS"));
    raw.put("type", List.of("alipay"));
    raw.put("future_zero", List.of("0"));
    raw.put("future_empty", List.of(""));
    raw.put("sign_type", List.of("MD5"));
    // V1 通知契约的合成样本；独立 Python hashlib 对手写原文加测试密钥计算的固定值。
    raw.put("sign", List.of("fd965609a1fa23cec9e2b79df1e33a9e"));
    NotificationRequest snapshot = NotificationRequest.fromMultiValue(raw);
    raw.get("money").set(0, "9.00");
    var result = client.verifyNotification(snapshot);
    require(result.success() && result.data() != null, "通知验签失败");
    var notification = result.data();
    require(MERCHANT_ID.equals(notification.merchantId())
        && ORDER_NO.equals(notification.outTradeNo()) && TRADE_NO.equals(notification.tradeNo()),
        "通知身份字段映射错误");
    require(AMOUNT.equals(notification.amount()) && notification.status() == OrderStatus.PAID,
        "通知必须按原始快照验签并映射");
    require(" 原样 &+= ".equals(notification.param()) && "success".equals(notification.successAck()),
        "通知不得 trim 参数或改写 ACK");
    // 此处只验证 ACK 数据，不代替业务事务，更不向真实平台确认。
    expect(EPayException.Kind.SIGNATURE,
        () -> client.verifyNotification(NotificationRequest.fromMultiValue(raw)));
    var changed = new LinkedHashMap<>(snapshot.parameters());
    changed.put("future_zero", "1");
    expect(EPayException.Kind.SIGNATURE,
        () -> client.verifyNotification(NotificationRequest.fromParameters(changed)));
    raw.put("pid", List.of(MERCHANT_ID, MERCHANT_ID));
    expect(EPayException.Kind.VALIDATION, () -> NotificationRequest.fromMultiValue(raw));
  }

  private static void compileReadmeExamples(Path readme, Path consumerTarget) throws IOException {
    List<Path> documents = List.of(readme, readme.getParent().resolve("docs/usage.md"));
    var blocks = Pattern.compile("(?ms)^```java[ \\t]*\\R(.*?)^```[ \\t]*\\r?$");
    var openings = Pattern.compile("(?m)^```java[ \\t]*\\r?$");
    var declarations = Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+([A-Za-z_$][\\w$]*)\\b");
    List<JavaFileObject> sources = new ArrayList<>();
    var names = new HashSet<String>();
    long declaredBlocks = 0;
    for (Path document : documents) {
      require(Files.isRegularFile(document), "缺少文档：" + document.getFileName());
      String markdown = Files.readString(document, StandardCharsets.UTF_8);
      declaredBlocks += openings.matcher(markdown).results().count();
      var matcher = blocks.matcher(markdown);
      while (matcher.find()) {
        String source = matcher.group(1);
        var declaration = declarations.matcher(source);
        String name;
        if (declaration.find()) {
          name = declaration.group(1);
        } else {
          require(source.replaceAll("(?m)^import [\\w.*]+;[ \\t]*\\r?$", "").isBlank(),
              document.getFileName() + " 的 Java 代码块不是完整类或 imports，必须显式处理，不能跳过");
          name = "ReadmeImports" + sources.size();
          source += "\nfinal class " + name + " {}\n";
        }
        require(names.add(name), "文档存在重复顶层类名：" + name);
        String content = source;
        sources.add(new SimpleJavaFileObject(URI.create("string:///" + name + ".java"),
            JavaFileObject.Kind.SOURCE) {
          @Override
          public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return content;
          }
        });
      }
    }
    require(!sources.isEmpty() && sources.size() == declaredBlocks, "文档 Java 代码块缺失或未闭合");
    var compiler = ToolProvider.getSystemJavaCompiler();
    require(compiler != null, "示例编译需要完整 JDK，不可用 JRE 冒充验收");
    Path output = Files.createDirectories(consumerTarget.resolve("readme-classes"));
    var diagnostics = new StringWriter();
    try (var files = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
      // 只使用已经检查过的 runtime classpath；不从仓库源码补类、不加载注解处理器。
      boolean compiled = compiler.getTask(diagnostics, files, null,
          List.of("--release", "17", "-proc:none", "-Xlint:all", "-Werror", "-sourcepath", "",
              "-classpath", System.getProperty("java.class.path"), "-d", output.toString()),
          null, sources).call();
      require(compiled, "文档 Java 示例编译失败：\n" + diagnostics);
    }
    // 只编译，不执行读取环境配置的示例工厂，也不连接示例网关。
    System.out.println("文档全部 " + sources.size() + " 个 Java 代码块编译通过（release 17）。");
  }

  private static void expect(EPayException.Kind kind, Runnable action) {
    try {
      action.run();
      throw new AssertionError("预期 SDK 拒绝操作：" + kind);
    } catch (EPayException error) {
      require(error.kind() == kind, "错误分类与契约不符");
    }
  }

  private static void verifyServerFailure(AtomicReference<Throwable> failure) {
    if (failure.get() != null) {
      throw new AssertionError("本地 HTTP 服务器验收失败", failure.get());
    }
  }

  private static void require(boolean condition, String message) {
    // 不依赖 -ea；断言失败总是让独立 JVM 非零退出。
    if (!condition) throw new AssertionError(message);
  }
}
