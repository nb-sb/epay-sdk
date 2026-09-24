package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;

/** V1 商户密钥；只在实际认证时读取，禁止写入日志。 */
public record Md5Credentials(String secret) implements Credentials {
  public Md5Credentials {
    if (secret == null || secret.isBlank()) throw EPayException.configuration();
  }

  @Override
  public String toString() {
    return "Md5Credentials[已遮蔽]";
  }
}
