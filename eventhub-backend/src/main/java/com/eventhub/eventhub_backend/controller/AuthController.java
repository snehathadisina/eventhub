package com.eventhub.eventhub_backend.controller;

import com.eventhub.eventhub_backend.dto.AuthResponse;
import com.eventhub.eventhub_backend.dto.LoginRequest;
import com.eventhub.eventhub_backend.dto.RegisterRequest;
import com.eventhub.eventhub_backend.dto.UserResponse;
import com.eventhub.eventhub_backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}