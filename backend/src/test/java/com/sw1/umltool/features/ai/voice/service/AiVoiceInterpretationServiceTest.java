package com.sw1.umltool.features.ai.voice.service;

import com.sun.net.httpserver.HttpServer;
import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretRequest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiVoiceInterpretationServiceTest {
    private static final AiVoiceInterpretRequest REQUEST = new AiVoiceInterpretRequest("crear clase Cliente", "es-BO", null);

    @Test
    void acceptsValidResponseWithinConfiguredTimeout() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, "{\"success\":true,\"command\":{\"type\":\"CREATE_CLASS\",\"className\":\"Cliente\"},\"confidence\":0.95,\"summary\":\"Crear clase Cliente\",\"errors\":[]}"));
        try {
            var service = serviceFor(server, 10, 30);
            assertEquals("Cliente", service.interpret(REQUEST).command().className());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsReadTimeoutToTimeoutException() throws Exception {
        HttpServer server = server(exchange -> {
            try {
                Thread.sleep(1_500);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "{\"success\":false,\"errors\":[]}");
        });
        try {
            var service = serviceFor(server, 10, 1);
            assertThrows(AiVoiceTimeoutException.class, () -> service.interpret(REQUEST));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInvalidResponseWithoutMappingItToTimeout() throws Exception {
        HttpServer server = server(exchange -> respond(exchange, 200, "{\"success\":true,\"command\":{\"type\":\"UNKNOWN\"}}"));
        try {
            var service = serviceFor(server, 10, 30);
            assertThrows(IllegalArgumentException.class, () -> service.interpret(REQUEST));
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(HttpHandler handler) throws IOException {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/interpret", exchange -> handler.handle(exchange));
        server.start();
        return server;
    }

    private AiVoiceInterpretationService serviceFor(HttpServer server, int connectTimeout, int readTimeout) {
        return new AiVoiceInterpretationService("http://localhost:" + server.getAddress().getPort() + "/interpret", "", connectTimeout, readTimeout);
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException;
    }
}
