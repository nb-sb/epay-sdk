package com.nbsb.epaysdk.core.http;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nbsb.epaysdk.core.config.MerchantConfig;
import com.nbsb.epaysdk.core.exception.EPayHttpException;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EPayHttpClientTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api.php", exchange -> write(exchange, 200, "{\"code\":1}"));
        server.createContext("/down", exchange -> write(exchange, 500, "down"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void redactsMerchantKeyInQueryString() {
        assertNull(EPayHttpClient.redactSensitive(null));
        assertEquals("", EPayHttpClient.redactSensitive(""));
        assertEquals("/api.php?act=query&pid=1001&key=***",
                EPayHttpClient.redactSensitive("/api.php?act=query&pid=1001&key=secret"));
        assertEquals("key=***&act=order", EPayHttpClient.redactSensitive("key=secret&act=order"));
        assertEquals("/api.php?KEY=***", EPayHttpClient.redactSensitive("/api.php?KEY=AbC"));
        assertEquals("/mapi.php", EPayHttpClient.redactSensitive("/mapi.php"));
    }

    @Test
    void debugLogHidesMerchantKey() {
        Logger logger = (Logger) LoggerFactory.getLogger(EPayHttpClient.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            Map<String, String> params = new LinkedHashMap<String, String>();
            params.put("act", "query");
            params.put("key", "secret-value");
            String body = new EPayHttpClient(config()).get(baseUrl + "/api.php", params);
            assertEquals("{\"code\":1}", body);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
        }

        assertFalse(appender.list.isEmpty());
        for (ILoggingEvent event : appender.list) {
            String message = event.getFormattedMessage();
            assertFalse(message.contains("secret-value"));
            assertTrue(message.contains("key=***"));
        }
    }

    @Test
    void errorResponsesReleaseTheConnection() {
        EPayHttpClient client = new EPayHttpClient(config());
        for (int i = 0; i < 6; i++) {
            EPayHttpException error = assertThrows(EPayHttpException.class,
                    () -> client.get(baseUrl + "/down", Collections.<String, String>emptyMap()));
            assertTrue(error.getMessage().contains("HTTP 500"));
        }
        assertEquals("{\"code\":1}", client.get(baseUrl + "/api.php", Collections.<String, String>emptyMap()));
    }

    @Test
    void wrapsTransportFailure() throws Exception {
        CloseableHttpClient http = mock(CloseableHttpClient.class);
        when(http.execute(any(HttpUriRequestBase.class))).thenThrow(new IOException("connection reset"));

        EPayHttpClient client = new EPayHttpClient(http);
        EPayHttpException error = assertThrows(EPayHttpException.class,
                () -> client.get("https://pay.example/api.php", Collections.<String, String>emptyMap()));
        assertTrue(error.getMessage().contains("connection reset"));
    }

    private MerchantConfig config() {
        return MerchantConfig.builder()
                .url(baseUrl + "/")
                .appId("1001")
                .appKey("secret-value")
                .connectTimeoutMs(500)
                .responseTimeoutMs(1000)
                .build();
    }

    private static void write(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
