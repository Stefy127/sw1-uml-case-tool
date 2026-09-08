package com.sw1.umltool.features.auth.service;

import com.sw1.umltool.features.auth.model.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Service
public class JwtService {
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(@Value("${auth.jwt.secret}") String secret,
                      @Value("${auth.jwt.expiration-seconds:86400}") long expirationSeconds) {
        if (secret == null || secret.length() < 32) throw new IllegalStateException("JWT secret must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public String issue(UserEntity user) {
        String header = encoded("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        long expiry = Instant.now().getEpochSecond() + expirationSeconds;
        String payload = encoded("{\"sub\":\"" + user.getId() + "\",\"exp\":" + expiry + "}");
        String content = header + "." + payload;
        return content + "." + sign(content);
    }

    public String userId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3 || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) return null;
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            long exp = Long.parseLong(json.replaceAll(".*\\\"exp\\\":([0-9]+).*", "$1"));
            if (exp <= Instant.now().getEpochSecond()) return null;
            return json.replaceAll(".*\\\"sub\\\":\\\"([^\"]+)\\\".*", "$1");
        } catch (RuntimeException exception) { return null; }
    }

    private String encoded(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private String sign(String content) {
        try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret, "HmacSHA256")); return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("JWT signing failed", exception); }
    }
}
