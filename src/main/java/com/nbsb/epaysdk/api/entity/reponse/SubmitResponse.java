package com.nbsb.epaysdk.api.entity.reponse;

/**
 * Page-jump payment result: either a pay URL extracted from HTML, or the raw/form HTML.
 */
public class SubmitResponse extends CommonResponse {

    private String payUrl;
    private String rawHtml;
    private String formHtml;

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public String getRawHtml() {
        return rawHtml;
    }

    public void setRawHtml(String rawHtml) {
        this.rawHtml = rawHtml;
    }

    public String getFormHtml() {
        return formHtml;
    }

    public void setFormHtml(String formHtml) {
        this.formHtml = formHtml;
    }
}
