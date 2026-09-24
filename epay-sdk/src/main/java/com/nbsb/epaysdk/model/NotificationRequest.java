package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.internal.checks.Checks;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 一次快照后验签和映射共用原始参数；框架层应优先传入多值参数。 */
public final class NotificationRequest {
  private final Map<String, String> parameters;

  private NotificationRequest(Map<String, String> parameters) {
    this.parameters = Checks.parameters(parameters);
  }

  /** 仅适用于上游已保证没有重复键的输入。 */
  public static NotificationRequest fromParameters(Map<String, String> parameters) {
    return new NotificationRequest(parameters);
  }

  public static NotificationRequest fromMultiValue(
      Map<String, ? extends Collection<String>> parameters) {
    Checks.required(parameters);
    Map<String, String> snapshot = new LinkedHashMap<>();
    parameters.forEach(
        (key, values) -> {
          if (values == null || values.size() != 1) throw EPayException.validation();
          String value = values.iterator().next();
          if (snapshot.putIfAbsent(Checks.text(key), Checks.required(value)) != null) {
            throw EPayException.validation();
          }
        });
    return new NotificationRequest(snapshot);
  }

  public Map<String, String> parameters() {
    return parameters;
  }

  @Override
  public String toString() {
    return "NotificationRequest[已遮蔽]";
  }
}
