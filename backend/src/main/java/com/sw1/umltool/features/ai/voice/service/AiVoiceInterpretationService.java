package com.sw1.umltool.features.ai.voice.service;

import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretRequest;
import com.sw1.umltool.features.ai.voice.dto.AiVoiceInterpretResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
public class AiVoiceInterpretationService {
    private static final Logger log = LoggerFactory.getLogger(AiVoiceInterpretationService.class);
    private static final Set<String> COMMANDS = Set.of("CREATE_CLASS", "DELETE_CLASS", "RENAME_CLASS", "ADD_ATTRIBUTE", "REMOVE_ATTRIBUTE", "ADD_METHOD", "CREATE_RELATION");
    private static final Set<String> RELATION_TYPES = Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION", "INHERITANCE", "DEPENDENCY");
    private final RestClient client;
    private final String webhookUrl;
    private final String webhookToken;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    public AiVoiceInterpretationService(@Value("${ai.n8n.voice-webhook-url:}") String webhookUrl,
                                        @Value("${ai.n8n.voice-webhook-token:}") String webhookToken,
                                        @Value("${ai.n8n.connect-timeout-seconds:10}") int connectTimeoutSeconds,
                                        @Value("${ai.n8n.read-timeout-seconds:30}") int readTimeoutSeconds) {
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.webhookUrl = webhookUrl;
        this.webhookToken = webhookToken;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public AiVoiceInterpretResponse interpret(AiVoiceInterpretRequest request) {
        if (webhookUrl.isBlank()) throw new IllegalStateException("El servicio de interpretacion por IA no esta configurado.");
        AiVoiceInterpretRequest safeRequest = new AiVoiceInterpretRequest(request.text(), request.language() == null ? "es-BO" : request.language(), request.diagramContext());
        long startedAt = System.nanoTime();
        log.info("N8N voice request started url={} connectTimeout={}s readTimeout={}s",
                sanitizedUrl(), connectTimeoutSeconds, readTimeoutSeconds);
        try {
            var response = client.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> { if (!webhookToken.isBlank()) headers.setBearerAuth(webhookToken); })
                    .body(new N8nRequest(safeRequest.text(), safeRequest.language(), safeRequest.diagramContext(), List.copyOf(COMMANDS), List.copyOf(RELATION_TYPES)))
                    .retrieve().body(AiVoiceInterpretResponse.class);
            validate(response);
            AiVoiceInterpretResponse normalizedResponse;
            if (response != null && response.success() && response.command() != null
                    && response.command().secondaryClassName() == null
                    && response.command().targetClassName() != null) {
                var c = response.command();
                normalizedResponse = new AiVoiceInterpretResponse(true, new AiVoiceInterpretResponse.AiCommand(c.type(), c.className() == null ? c.sourceClassName() : c.className(), c.targetClassName(), c.sourceClassName(), c.targetClassName(), c.newClassName(), c.attributeName(), c.attributeType(), c.methodName(), c.relationType()), response.confidence(), response.summary(), response.errors());
            } else {
                normalizedResponse = response;
            }
            log.info("N8N voice request completed in {} ms", elapsedMs(startedAt));
            return normalizedResponse;
        } catch (RestClientException exception) {
            log.error("N8N voice request failed after {} ms: {} - {} cause={} - {}",
                    elapsedMs(startedAt), exception.getClass().getName(), exception.getMessage(),
                    causeClass(exception), causeMessage(exception));
            if (hasCause(exception, HttpTimeoutException.class)) {
                throw new AiVoiceTimeoutException("El servicio de interpretaci\u00f3n por IA tard\u00f3 demasiado en responder.", exception);
            }
            if (hasCause(exception, HttpMessageConversionException.class)) {
                throw new IllegalArgumentException("El servicio de IA devolvi\u00f3 una respuesta no v\u00e1lida.", exception);
            }
            throw new IllegalStateException("El servicio de interpretaci\u00f3n por IA no est\u00e1 disponible.", exception);
        }
    }

    private long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String sanitizedUrl() {
        return webhookUrl.replaceAll("([?&](?:token|key|secret)=)[^&]*", "$1***");
    }

    private String causeClass(Throwable exception) {
        return exception.getCause() == null ? "none" : exception.getCause().getClass().getName();
    }

    private String causeMessage(Throwable exception) {
        return exception.getCause() == null ? "none" : exception.getCause().getMessage();
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) return true;
        }
        return false;
    }

    private void validate(AiVoiceInterpretResponse response) {
        if (response == null) throw invalidResponse();
        if (!response.success()) return;
        var command = response.command();
        if (command == null || !COMMANDS.contains(command.type())) throw invalidResponse();
        if (Set.of("CREATE_CLASS", "DELETE_CLASS", "RENAME_CLASS", "ADD_ATTRIBUTE", "REMOVE_ATTRIBUTE", "ADD_METHOD").contains(command.type()) && blank(command.className())) throw invalidResponse();
        if (command.type().equals("ADD_ATTRIBUTE") && (blank(command.attributeName()) || blank(command.attributeType()))) throw invalidResponse();
        if (command.type().equals("ADD_METHOD") && blank(command.methodName())) throw invalidResponse();
        if (command.type().equals("CREATE_RELATION") && !RELATION_TYPES.contains(command.relationType())) throw invalidResponse();
        if (command.type().equals("CREATE_RELATION") && (blank(command.className()) && blank(command.sourceClassName()) || blank(command.secondaryClassName()) && blank(command.targetClassName()))) throw invalidResponse();
    }

    private IllegalArgumentException invalidResponse() {
        return new IllegalArgumentException("El servicio de IA devolvi\u00f3 una respuesta no v\u00e1lida.");
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private record N8nRequest(String text, String language, AiVoiceInterpretRequest.DiagramContext diagram,
                              List<String> allowedCommands, List<String> allowedRelationTypes) {}
}
