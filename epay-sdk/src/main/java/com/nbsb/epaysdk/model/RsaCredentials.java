package com.nbsb.epaysdk.model;

import com.nbsb.epaysdk.api.EPayException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** V2 双角色凭证；只接受 PKCS8 商户私钥和 X509 平台公钥，至少 2048 位。 */
public final class RsaCredentials implements Credentials {
  private final PrivateKey merchantPrivateKey;
  private final PublicKey platformPublicKey;

  public RsaCredentials(String merchantPrivateKeyPem, String platformPublicKeyPem) {
    try {
      KeyFactory factory = KeyFactory.getInstance("RSA");
      merchantPrivateKey =
          factory.generatePrivate(
              new PKCS8EncodedKeySpec(decodePem(merchantPrivateKeyPem, "PRIVATE KEY")));
      platformPublicKey =
          factory.generatePublic(
              new X509EncodedKeySpec(decodePem(platformPublicKeyPem, "PUBLIC KEY")));
      checkSize(merchantPrivateKey);
      checkSize(platformPublicKey);
    } catch (Exception e) {
      throw EPayException.configuration();
    }
  }

  public static RsaCredentials fromPem(String merchantPrivateKeyPem, String platformPublicKeyPem) {
    return new RsaCredentials(merchantPrivateKeyPem, platformPublicKeyPem);
  }

  public PrivateKey merchantPrivateKey() {
    return merchantPrivateKey;
  }

  public PublicKey platformPublicKey() {
    return platformPublicKey;
  }

  private static byte[] decodePem(String pem, String label) {
    if (pem == null || pem.length() > 32768) throw EPayException.configuration();
    String trimmed = pem.strip();
    String begin = "-----BEGIN " + label + "-----";
    String end = "-----END " + label + "-----";
    if (!trimmed.startsWith(begin) || !trimmed.endsWith(end)) throw EPayException.configuration();
    String body =
        trimmed
            .substring(begin.length(), trimmed.length() - end.length())
            .replaceAll("[\\r\\n\\t ]", "");
    return Base64.getDecoder().decode(body);
  }

  private static void checkSize(Object key) {
    if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048) {
      throw EPayException.configuration();
    }
  }

  @Override
  public String toString() {
    return "RsaCredentials[已遮蔽]";
  }
}
