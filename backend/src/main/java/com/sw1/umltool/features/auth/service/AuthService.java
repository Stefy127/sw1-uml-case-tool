package com.sw1.umltool.features.auth.service;

import com.sw1.umltool.features.auth.dto.*;
import com.sw1.umltool.features.auth.model.UserEntity;
import com.sw1.umltool.features.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;

@Service
public class AuthService {
    private static final Set<String> THEMES = Set.of("LAVENDER", "ROSE", "SKY", "MINT", "PEACH", "LILAC", "BUTTER", "SAGE", "NEUTRAL");
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) { this.users = users; this.encoder = encoder; this.jwt = jwt; }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalize(request.getEmail());
        if (users.existsByEmailIgnoreCase(email)) throw new IllegalArgumentException("El correo ya está registrado.");
        LocalDateTime now = LocalDateTime.now();
        UserEntity user = users.save(UserEntity.builder().id(UUID.randomUUID().toString()).firstName(request.getFirstName().trim()).lastName(request.getLastName().trim()).email(email).passwordHash(encoder.encode(request.getPassword())).createdAt(now).updatedAt(now).enabled(true).theme("LAVENDER").build());
        return AuthResponse.builder().token(jwt.issue(user)).user(UserResponse.from(user)).build();
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = users.findByEmailIgnoreCase(normalize(request.getEmail())).filter(UserEntity::isEnabled).filter(u -> encoder.matches(request.getPassword(), u.getPasswordHash())).orElseThrow(() -> new IllegalArgumentException("Correo o contraseña incorrectos."));
        return AuthResponse.builder().token(jwt.issue(user)).user(UserResponse.from(user)).build();
    }

    public UserResponse me(String userId) { return users.findById(userId).filter(UserEntity::isEnabled).map(UserResponse::from).orElseThrow(() -> new IllegalArgumentException("La sesión no es válida.")); }
    @Transactional
    public UserResponse updateProfile(String userId, UpdateProfileRequest request) {
        UserEntity user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("La sesión no es válida."));
        String email = normalize(request.getEmail());
        users.findByEmailIgnoreCase(email).filter(other -> !other.getId().equals(userId)).ifPresent(other -> { throw new IllegalArgumentException("El correo ya está registrado."); });
        user.setFirstName(request.getFirstName().trim()); user.setLastName(request.getLastName().trim()); user.setEmail(email); user.setUpdatedAt(LocalDateTime.now());
        return UserResponse.from(users.save(user));
    }
    @Transactional
    public void changePassword(String userId, ChangePasswordRequest request) {
        UserEntity user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("La sesión no es válida."));
        if (!encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) throw new IllegalArgumentException("La contraseña actual no es correcta.");
        if (encoder.matches(request.getNewPassword(), user.getPasswordHash())) throw new IllegalArgumentException("La nueva contraseña debe ser diferente.");
        user.setPasswordHash(encoder.encode(request.getNewPassword())); user.setUpdatedAt(LocalDateTime.now()); users.save(user);
    }
    @Transactional
    public UserResponse updateTheme(String userId, UpdateThemeRequest request) {
        UserEntity user = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("La sesión no es válida."));
        String theme = request.getTheme() == null ? "" : request.getTheme().trim().toUpperCase(Locale.ROOT);
        if (!THEMES.contains(theme)) throw new IllegalArgumentException("El tema seleccionado no es válido.");
        user.setTheme(theme); user.setUpdatedAt(LocalDateTime.now()); return UserResponse.from(users.save(user));
    }
    private String normalize(String email) { return email == null ? "" : email.trim().toLowerCase(Locale.ROOT); }
}
