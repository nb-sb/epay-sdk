package com.nbsb.epaysdk.internal;

import com.nbsb.epaysdk.api.EPayException;
import com.nbsb.epaysdk.model.Md5Credentials;
import com.nbsb.epaysdk.model.RsaCredentials;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

/** JCA 签名工具；UTF-8 原值签名，编码只能在签名之后进行。 */
public final class Signatures {
  private Signatures() {}

  public static String canonical(Map<String, String> parameters) {
    if (parameters == null) throw EPayException.signature();
    TreeMap<String, String> sorted = new TreeMap<>();
    parameters.forEach(
        (key, value) -> {
          if (key == null) throw EPayException.signature();
          if (!key.equals("sign")
              && !key.equals("sign_type")
              && value != null
              && !value.isEmpty()) {
            sorted.put(key, value);
          }
        });
    StringJoiner text = new StringJoiner("&");
    sorted.forEach((key, value) -> text.add(key + "=" + value));
    return text.toString();
  }

  public static String md5(Map<String, String> parameters, Md5Credentials credentials) {
    try {
      byte[] bytes =
          (canonical(parameters) + credentials.secret()).getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(bytes));
    } catch (Exception e) {
      throw EPayException.signature();
    }
  }

  public static boolean verifyMd5(
      Map<String, String> parameters, Md5Credentials credentials, String signature) {
    if (signature == null || !signature.matches("[0-9a-fA-F]{32}")) return false;
    byte[] expected = HexFormat.of().parseHex(md5(parameters, credentials));
    byte[] actual = HexFormat.of().parseHex(signature);
    return MessageDigest.isEqual(expected, actual);
  }

  public static String rsa(Map<String, String> parameters, RsaCredentials credentials) {
    if (credentials == null) throw EPayException.signature();
    return rsa(parameters, credentials.merchantPrivateKey());
  }

  public static String rsa(Map<String, String> parameters, PrivateKey privateKey) {
    try {
      checkRsa(privateKey);
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(canonical(parameters).getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (Exception e) {
      throw EPayException.signature();
    }
  }

  public static boolean verifyRsa(
      Map<String, String> parameters, RsaCredentials credentials, String signature) {
    if (credentials == null) throw EPayException.signature();
    return verifyRsa(parameters, credentials.platformPublicKey(), signature);
  }

  public static boolean verifyRsa(
      Map<String, String> parameters, PublicKey publicKey, String signed) {
    if (signed == null || signed.isEmpty() || signed.length() > 16384) return false;
    try {
      checkRsa(publicKey);
      Signature verifier = Signature.getInstance("SHA256withRSA");
      verifier.initVerify(publicKey);
      verifier.update(canonical(parameters).getBytes(StandardCharsets.UTF_8));
      return verifier.verify(Base64.getDecoder().decode(signed));
    } catch (java.security.SignatureException | IllegalArgumentException e) {
      return false;
    } catch (Exception e) {
      throw EPayException.signature();
    }
  }

  private static void checkRsa(Object key) {
    if (!(key instanceof RSAKey rsa) || rsa.getModulus().bitLength() < 2048) {
      throw EPayException.signature();
    }
  }
}
