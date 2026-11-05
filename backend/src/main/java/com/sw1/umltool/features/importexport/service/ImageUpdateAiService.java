package com.sw1.umltool.features.importexport.service;

import com.sw1.umltool.features.importexport.dto.ImageUpdateAiRequest;
import com.sw1.umltool.features.importexport.dto.ImageUpdateAiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;

@Service
public class ImageUpdateAiService {
    private final RestClient client;
    private final String webhookUrl;
    private final String token;

    public ImageUpdateAiService(
            @Value("${ai.n8n.uml-image-update-webhook-url:}") String webhookUrl,
            @Value("${ai.n8n.uml-image-webhook-token:}") String token,
            @Value("${ai.n8n.connect-timeout-seconds:10}") int connectTimeout,
            @Value("${ai.n8n.image-read-timeout-seconds:${ai.n8n.read-timeout-seconds:30}}") int readTimeout) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(connectTimeout)).build());
        factory.setReadTimeout(Duration.ofSeconds(readTimeout));
        this.client = RestClient.builder().requestFactory(factory).build();
        this.webhookUrl = webhookUrl;
        this.token = token;
    }

    public ImageUpdateAiResponse interpret(byte[] image, String fileName, String contentType,
                                           String instruction, boolean allowDestructiveChanges,
                                           com.sw1.umltool.features.diagram.model.canonical.UmlDiagram context) {
        if (webhookUrl.isBlank()) {
            throw new IllegalStateException("El servicio de actualización visual aún no está configurado.");
        }
        var request = new ImageUpdateAiRequest(fileName, contentType,
                Base64.getEncoder().encodeToString(image), instruction == null ? "" : instruction,
                allowDestructiveChanges, context);
        try {
            ImageUpdateAiResponse response = client.post().uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> { if (!token.isBlank()) headers.setBearerAuth(token); })
                    .body(request).retrieve().body(ImageUpdateAiResponse.class);
            if (response == null) throw new IllegalArgumentException("Respuesta vacía del servicio visual.");
            return response;
        } catch (org.springframework.web.client.RestClientException exception) {
            throw new IllegalStateException("El servicio de actualización visual no está disponible.", exception);
        }
    }
}
