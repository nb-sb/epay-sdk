package com.nbsb.epaysdk.core.form;

import com.nbsb.epaysdk.core.exception.EPayValidationException;

import java.util.Map;

/**
 * Builds an auto-submitting HTML form for page-jump payment (submit.php).
 */
public final class SubmitFormBuilder {

    private SubmitFormBuilder() {
    }

    public static String build(String actionUrl, Map<String, String> fields) {
        if (actionUrl == null || actionUrl.trim().isEmpty()) {
            throw new EPayValidationException("submit 表单 action 不能为空");
        }
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>正在跳转支付</title></head><body>");
        html.append("<form id=\"epay-submit\" method=\"POST\" action=\"").append(escape(actionUrl)).append("\">");
        if (fields != null) {
            for (Map.Entry<String, String> entry : fields.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                if ("url".equals(entry.getKey()) || "key".equals(entry.getKey()) || "epayBodyType".equals(entry.getKey())) {
                    continue;
                }
                html.append("<input type=\"hidden\" name=\"")
                        .append(escape(entry.getKey()))
                        .append("\" value=\"")
                        .append(escape(entry.getValue()))
                        .append("\"/>");
            }
        }
        html.append("</form><script>document.getElementById('epay-submit').submit();</script>");
        html.append("</body></html>");
        return html.toString();
    }

    static String escape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\'':
                    out.append("&#39;");
                    break;
                default:
                    out.append(c);
            }
        }
        return out.toString();
    }
}
