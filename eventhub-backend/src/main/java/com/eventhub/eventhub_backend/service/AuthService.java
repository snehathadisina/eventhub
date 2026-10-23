package com.eventhub.eventhub_backend.service;

import com.eventhub.eventhub_backend.dto.AuthResponse;
import com.eventhub.eventhub_backend.dto.LoginRequest;
import com.eventhub.eventhub_backend.dto.RegisterRequest;
import com.eventhub.eventhub_backend.dto.UserResponse;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.AuthProvider;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        }

        Role role = request.role() == null ? Role.ATTENDEE : request.role();
        if (role == Role.STAFF) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This role cannot be chosen at registration");
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(role);
        user.setAuthProvider(AuthProvider.LOCAL);

        return UserResponse.from(userRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();

        User user = userRepository.findByEmail(email)
                .filter(u -> u.getPasswordHash() != null
                        && passwordEncoder.matches(request.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        String token = jwtService.generateToken(user);
        return new AuthResponse(token, "Bearer", jwtService.getExpirationSeconds(),
                UserResponse.from(user));
    }
}