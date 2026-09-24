package com.nbsb.epaysdk.internal;

import com.nbsb.epaysdk.internal.checks.Checks;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

/** 签名完成后才调用编码；不读取远端页面，也不插入自动执行的脚本。 */
public final class FormCodec {
  private FormCodec() {}

  public static String encode(Map<String, String> parameters) {
    StringJoiner result = new StringJoiner("&");
    Checks.parameters(parameters)
        .forEach(
            (key, value) ->
                result.add(
                    URLEncoder.encode(key, StandardCharsets.UTF_8)
                        + "="
                        + URLEncoder.encode(value, StandardCharsets.UTF_8)));
    return result.toString();
  }

  public static String buildHtmlForm(URI action, Map<String, String> parameters) {
    Checks.httpUri(action);
    StringBuilder html =
        new StringBuilder("<form method=\"post\" accept-charset=\"UTF-8\" action=\"");
    html.append(escapeHtml(action.toASCIIString())).append("\">");
    Checks.parameters(parameters)
        .forEach(
            (key, value) ->
                html.append("<input type=\"hidden\" name=\"")
                    .append(escapeHtml(key))
                    .append("\" value=\"")
                    .append(escapeHtml(value))
                    .append("\">"));
    return html.append("<button type=\"submit\">继续支付</button></form>").toString();
  }

  public static String escapeHtml(String value) {
    return Checks.required(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
}
