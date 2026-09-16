package com.nbsb.epaysdk.core.form;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubmitFormBuilderTest {

    @Test
    void buildsAutoPostFormAndEscapesValues() {
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put("name", "a&b\"c");
        fields.put("key", "secret-must-not-appear");
        fields.put("url", "https://hidden");
        fields.put("pid", "1001");
        String html = SubmitFormBuilder.build("https://pay.example.com/submit.php", fields);
        assertTrue(html.contains("action=\"https://pay.example.com/submit.php\""));
        assertTrue(html.contains("name=\"name\" value=\"a&amp;b&quot;c\""));
        assertTrue(html.contains("name=\"pid\""));
        assertTrue(html.contains("epay-submit"));
        assertTrue(html.contains("submit()"));
        assertFalse(html.contains("secret-must-not-appear"));
        assertFalse(html.contains("https://hidden"));
    }

    @Test
    void extractsRelativePayUrl() {
        String html = "<html><script>window.location.href='./pay/qr?id=9';</script></html>";
        assertEquals("/pay/qr?id=9", SubmitHtmlParser.extractRelativeUrl(html));
    }
}
