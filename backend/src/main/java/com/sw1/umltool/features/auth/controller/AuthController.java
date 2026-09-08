package com.sw1.umltool.features.auth.controller;

import com.sw1.umltool.features.auth.dto.*;
import com.sw1.umltool.features.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;
    public AuthController(AuthService service) { this.service = service; }
    @PostMapping("/register") public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) { return ResponseEntity.status(HttpStatus.CREATED).body(service.register(request)); }
    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
    @GetMapping("/me") public UserResponse me(Authentication authentication) { return service.me(authentication.getName()); }
    @PutMapping("/me") public UserResponse updateProfile(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) { return service.updateProfile(authentication.getName(), request); }
    @PutMapping("/me/password") public ResponseEntity<Void> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest request) { service.changePassword(authentication.getName(), request); return ResponseEntity.noContent().build(); }
    @PutMapping("/me/preferences") public UserResponse updateTheme(Authentication authentication, @Valid @RequestBody UpdateThemeRequest request) { return service.updateTheme(authentication.getName(), request); }
}
