package com.sw1.umltool.features.importexport.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class ImageUmlAiServiceTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Test
    void productionConstructorIsExplicitlySelectedForSpringInjection() {
        var constructors = ImageUmlAiService.class.getConstructors();
        assertEquals(1, constructors.length);
        assertTrue(constructors[0].isAnnotationPresent(Autowired.class));
    }

    @Test
    void acceptsTheDocumentedStructuredResponse() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200,
                "{\"success\":true,\"diagram\":{\"classes\":[],\"relations\":[],\"associationClasses\":[]},\"warnings\":[],\"confidence\":0.98,\"summary\":\"UML detectado\"}"));
        try {
            var response = service(server, 10, 30).interpret(PNG, "modelo.png", "image/png");
            assertTrue(response.success());
            assertEquals("UML detectado", response.summary());
        } finally { server.stop(0); }
    }

    @Test
    void acceptsResponsesThatOmitOptionalBooleanFields() throws Exception {
        String responseBody = "{\"success\":true,\"diagram\":{"
                + "\"classes\":[{\"name\":\"Cliente\",\"attributes\":[{\"name\":\"id\",\"type\":\"Long\"}],\"methods\":[]}],"
                + "\"relations\":[{\"type\":\"ASSOCIATION\",\"sourceClassName\":\"Cliente\",\"targetClassName\":\"Cliente\"}],"
                + "\"associationClasses\":[]},\"warnings\":[],\"confidence\":1}";
        HttpServer server = server(exchange -> respond(exchange, 200, responseBody));
        try {
            var response = service(server, 10, 30).interpret(PNG, "modelo.png", "image/png");
            assertNull(response.diagram().classes().getFirst().attributes().getFirst().staticAttribute());
            assertNull(response.diagram().relations().getFirst().sourceNavigable());
        } finally { server.stop(0); }
    }

    @Test
    void logsAndMapsUpstreamHttpErrorsAsUnavailable() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 422, "{\"error\":\"invalid image\"}"));
        try { assertThrows(IllegalStateException.class, () -> service(server, 10, 30).interpret(PNG, "modelo.png", "image/png")); }
        finally { server.stop(0); }
    }

    @Test
    void mapsInvalidJsonAsBadResponseInsteadOfTimeout() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, "not-json"));
        try { assertThrows(IllegalArgumentException.class, () -> service(server, 10, 30).interpret(PNG, "modelo.png", "image/png")); }
        finally { server.stop(0); }
    }

    @Test
    void mapsReadTimeoutAsUnavailable() throws Exception {
        HttpServer server = server(exchange -> { try { Thread.sleep(1_500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); } });
        try { assertThrows(IllegalStateException.class, () -> service(server, 10, 1).interpret(PNG, "modelo.png", "image/png")); }
        finally { server.stop(0); }
    }

    @Test
    void usesImageSpecificReadTimeoutWhenConfigured() throws Exception {
        HttpServer server = server(exchange -> {
            try { Thread.sleep(1_500); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        try { assertThrows(IllegalStateException.class, () -> service(server, 10, 30, 1).interpret(PNG, "modelo.png", "image/png")); }
        finally { server.stop(0); }
    }

    @Test
    void rejectsUnconfiguredWebhookBeforeMakingHttpCall() {
        var service = new ImageUmlAiService("", "", 10, 30);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.interpret(PNG, "modelo.png", "image/png"));
        assertTrue(exception.getMessage().contains("no está configurado"));
    }

    private ImageUmlAiService service(HttpServer server, int connect, int read) {
        return new ImageUmlAiService("http://localhost:" + server.getAddress().getPort() + "/image", "", connect, read);
    }
    private ImageUmlAiService service(HttpServer server, int connect, int read, int imageRead) {
        return new ImageUmlAiService("http://localhost:" + server.getAddress().getPort() + "/image", "", connect, read, imageRead);
    }
    private HttpServer server(Handler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/image", handler::handle);
        server.start();
        return server;
    }
    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) { output.write(bytes); }
    }
    @FunctionalInterface private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
