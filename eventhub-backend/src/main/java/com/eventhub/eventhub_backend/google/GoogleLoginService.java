package com.eventhub.eventhub_backend.google;

import com.eventhub.eventhub_backend.dto.AuthResponse;
import com.eventhub.eventhub_backend.dto.UserResponse;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GoogleLoginService {

    private static final int MAX_ATTEMPTS = 3;

    private final GoogleTokenVerifier verifier;
    private final GoogleAccountService accountService;
    private final JwtService jwtService;

    public AuthResponse login(String idToken) {
        GoogleIdentity identity = verifier.verify(idToken);

        if (!identity.emailVerified()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Your Google email address is not verified");
        }

        User user = null;
        for (int attempt = 1; user == null; attempt++) {
            try {
                user = accountService.findOrCreate(identity);
            } catch (DataIntegrityViolationException | ConcurrencyFailureException e) {
                // Two first-time sign-ins at the same moment: the other one created the user. Look again.
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }

        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                jwtService.getExpirationSeconds(), UserResponse.from(user));
    }
}