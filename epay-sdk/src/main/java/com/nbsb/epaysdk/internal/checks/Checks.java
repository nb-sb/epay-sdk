package com.nbsb.epaysdk.internal.checks;

import com.nbsb.epaysdk.api.EPayException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 通用不变量，不承载任何协议字段或成功码规则。本包不依赖 model，避免和内部实现互引。 */
public final class Checks {
  private Checks() {}

  public static <T> T required(T value) {
    if (value == null) throw EPayException.validation();
    return value;
  }

  public static String text(String value) {
    if (value == null || value.isBlank()) throw EPayException.validation();
    return value;
  }

  public static BigDecimal amount(BigDecimal value) {
    if (value == null
        || value.signum() <= 0
        || value.scale() > 2
        || value.precision() > 100
        || value.scale() < -100) {
      throw EPayException.validation();
    }
    return value;
  }

  /** 客户端配置中的超时。非法值是配置错误。 */
  public static Duration duration(Duration value) {
    return boundedDuration(value, EPayException.configuration());
  }

  /** 单次调用传入的超时。非法值是参数错误，不是商户配置错误。 */
  public static Duration requestDuration(Duration value) {
    return boundedDuration(value, EPayException.validation());
  }

  public static URI httpUri(String value) {
    try {
      return httpUri(URI.create(text(value)));
    } catch (RuntimeException e) {
      throw EPayException.validation();
    }
  }

  public static URI httpUri(URI value) {
    if (value == null
        || value.getHost() == null
        || value.getRawUserInfo() != null
        || value.getRawFragment() != null
        || !("https".equalsIgnoreCase(value.getScheme())
            || "http".equalsIgnoreCase(value.getScheme()))
        || value.getPort() > 65535) throw EPayException.validation();
    return value;
  }

  /** 允许空格和中文等合法编码前缀，但拒绝二次解码、越级及分隔符歧义。 */
  public static URI safePath(URI value) {
    String decoded = value.getPath();
    String raw = value.getRawPath();
    if (decoded == null
        || decoded.indexOf('\\') >= 0
        || decoded.indexOf('%') >= 0
        || raw.toLowerCase(java.util.Locale.ROOT).contains("%2f")
        || decoded.chars().anyMatch(c -> c < 0x20 || c == 0x7f)) {
      throw EPayException.configuration();
    }
    for (String segment : decoded.split("/")) {
      if (segment.equals(".") || segment.equals("..")) throw EPayException.configuration();
    }
    return value;
  }

  public static Map<String, String> parameters(Map<String, String> values) {
    if (values == null) throw EPayException.validation();
    Map<String, String> copy = new LinkedHashMap<>();
    values.forEach((key, value) -> copy.put(text(key), required(value)));
    return Collections.unmodifiableMap(copy);
  }

  private static Duration boundedDuration(Duration value, EPayException error) {
    if (value == null
        || value.compareTo(Duration.ofMillis(1)) < 0
        || value.compareTo(Duration.ofDays(1)) > 0) {
      throw error;
    }
    return value;
  }
}
