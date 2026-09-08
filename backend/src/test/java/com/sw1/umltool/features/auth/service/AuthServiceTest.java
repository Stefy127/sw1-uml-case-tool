package com.sw1.umltool.features.auth.service;

import com.sw1.umltool.features.auth.dto.LoginRequest;
import com.sw1.umltool.features.auth.dto.RegisterRequest;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock JwtService jwt;
    @InjectMocks AuthService service;

    @Test
    void registersWithHashedPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest(); request.setFirstName("María"); request.setLastName("Gómez"); request.setEmail(" MARIA@TEST.COM "); request.setPassword("password123");
        when(users.existsByEmailIgnoreCase("maria@test.com")).thenReturn(false);
        when(encoder.encode("password123")).thenReturn("$2a$hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.issue(any())).thenReturn("token");
        var response = service.register(request);
        assertEquals("token", response.getToken()); assertEquals("maria@test.com", response.getUser().getEmail());
        verify(encoder).encode("password123");
    }

    @Test
    void rejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(); request.setFirstName("A"); request.setLastName("B"); request.setEmail("a@test.com"); request.setPassword("password123");
        when(users.existsByEmailIgnoreCase("a@test.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.register(request));
        verify(users, never()).save(any());
    }

    @Test
    void logsInOnlyWithMatchingPassword() {
        LoginRequest request = new LoginRequest(); request.setEmail("user@test.com"); request.setPassword("password123");
        UserEntity user = UserEntity.builder().id("u1").firstName("A").lastName("B").email("user@test.com").passwordHash("hash").enabled(true).build();
        when(users.findByEmailIgnoreCase("user@test.com")).thenReturn(Optional.of(user)); when(encoder.matches("password123", "hash")).thenReturn(true); when(jwt.issue(user)).thenReturn("token");
        assertEquals("u1", service.login(request).getUser().getId());
    }
}
