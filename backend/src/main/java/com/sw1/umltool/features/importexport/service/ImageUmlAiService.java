package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.importexport.dto.AiUmlDetectionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;

@Service
public class ImageUmlAiService {
    private static final Logger log = LoggerFactory.getLogger(ImageUmlAiService.class);
    private final RestClient client;
    private final String webhookUrl;
    private final String webhookToken;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    @Autowired
    public ImageUmlAiService(@Value("${ai.n8n.uml-image-webhook-url:}") String webhookUrl,
                             @Value("${ai.n8n.uml-image-webhook-token:}") String webhookToken,
                             @Value("${ai.n8n.connect-timeout-seconds:10}") int connectTimeoutSeconds,
                             @Value("${ai.n8n.read-timeout-seconds:30}") int readTimeoutSeconds,
                             @Value("${ai.n8n.image-read-timeout-seconds:${ai.n8n.read-timeout-seconds:30}}") int imageReadTimeoutSeconds) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeoutSeconds)).build());
        factory.setReadTimeout(Duration.ofSeconds(imageReadTimeoutSeconds));
        client = RestClient.builder().requestFactory(factory).build();
        this.webhookUrl = webhookUrl;
        this.webhookToken = webhookToken;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = imageReadTimeoutSeconds;
    }

    ImageUmlAiService(String webhookUrl, String webhookToken, int connectTimeoutSeconds, int readTimeoutSeconds) {
        this(webhookUrl, webhookToken, connectTimeoutSeconds, readTimeoutSeconds, readTimeoutSeconds);
    }

    public AiUmlDetectionResponse interpret(byte[] bytes, String fileName, String contentType) {
        if (webhookUrl.isBlank()) throw new IllegalStateException("El servicio de importación de imágenes aún no está configurado.");
        String encodedImage = Base64.getEncoder().encodeToString(bytes);
        long startedAt = System.nanoTime();
        log.info("N8N image request started url={} connectTimeout={}s readTimeout={}s fileName={} contentType={} imageBase64Chars={}",
                sanitizedUrl(), connectTimeoutSeconds, readTimeoutSeconds, safeFileName(fileName), contentType, encodedImage.length());
        try {
            AiUmlDetectionResponse response = client.post().uri(webhookUrl).contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> { if (!webhookToken.isBlank()) headers.setBearerAuth(webhookToken); })
                    .body(new ImageRequest(safeFileName(fileName), contentType, encodedImage))
                    .retrieve().body(AiUmlDetectionResponse.class);
            if (response == null) throw new IllegalArgumentException("El servicio visual devolvió una respuesta vacía.");
            log.info("N8N image request completed in {} ms status={} success={}", elapsedMs(startedAt), 200, response.success());
            return response;
        } catch (RestClientResponseException exception) {
            log.error("N8N image request failed after {} ms status={} body={} exception={} message={} rootCause={} rootMessage={}",
                    elapsedMs(startedAt), exception.getStatusCode().value(), summarize(exception.getResponseBodyAsString()),
                    exception.getClass().getName(), exception.getMessage(), rootCauseClass(exception), rootCauseMessage(exception));
            throw new IllegalStateException("El servicio de interpretación visual no está disponible.", exception);
        } catch (RestClientException exception) {
            log.error("N8N image request failed after {} ms status=unknown exception={} message={} rootCause={} rootMessage={}",
                    elapsedMs(startedAt), exception.getClass().getName(), exception.getMessage(), rootCauseClass(exception), rootCauseMessage(exception));
            if (hasCause(exception, HttpMessageConversionException.class)) {
                throw new IllegalArgumentException("El servicio visual devolvió una respuesta no válida.", exception);
            }
            throw new IllegalStateException("El servicio de interpretación visual no está disponible.", exception);
        }
    }

    private long elapsedMs(long startedAt) { return Duration.ofNanos(System.nanoTime() - startedAt).toMillis(); }
    private String sanitizedUrl() { return webhookUrl.replaceAll("([?&](?:token|key|secret)=)[^&]*", "$1***"); }
    private String safeFileName(String value) {
        if (value == null) return "diagram";
        String normalized = value.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }
    private String summarize(String body) {
        if (body == null) return "<empty>";
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > 300 ? compact.substring(0, 300) + "..." : compact;
    }
    private Throwable rootCause(Throwable exception) { Throwable root = exception; while (root.getCause() != null) root = root.getCause(); return root; }
    private String rootCauseClass(Throwable exception) { return rootCause(exception).getClass().getName(); }
    private String rootCauseMessage(Throwable exception) { return rootCause(exception).getMessage(); }
    private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) if (type.isInstance(current)) return true;
        return false;
    }
    private record ImageRequest(String fileName, String contentType, String imageBase64) {}
}
