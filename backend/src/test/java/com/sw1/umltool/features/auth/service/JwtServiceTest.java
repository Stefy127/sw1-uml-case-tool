package com.sw1.umltool.features.auth.service;

import com.sw1.umltool.features.auth.model.UserEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {
    @Test
    void rejectsMissingOrKnownSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtService("", 3600));
        assertThrows(IllegalStateException.class, () -> new JwtService("sw1-development-secret-change-me-32chars", 3600));
    }

    @Test
    void issuesAndValidatesTokenWithExplicitSecret() {
        JwtService service = new JwtService("a-secure-test-secret-with-at-least-32-chars", 3600);
        UserEntity user = UserEntity.builder().id("user-1").build();
        assertEquals("user-1", service.userId(service.issue(user)));
    }
}
