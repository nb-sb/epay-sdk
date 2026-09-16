package com.nbsb.epaysdk.core.form;

/**
 * Extracts the relative pay URL from YZF submit.php HTML.
 */
public final class SubmitHtmlParser {

    private SubmitHtmlParser() {
    }

    public static String extractRelativeUrl(String htmlContent) {
        if (htmlContent == null) {
            return null;
        }
        String startToken = "<script>window.location.href='.";
        String endToken = "';</script>";
        int startIndex = htmlContent.indexOf(startToken);
        int endIndex = htmlContent.indexOf(endToken);
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return htmlContent.substring(startIndex + startToken.length(), endIndex).trim();
        }
        return null;
    }
}
