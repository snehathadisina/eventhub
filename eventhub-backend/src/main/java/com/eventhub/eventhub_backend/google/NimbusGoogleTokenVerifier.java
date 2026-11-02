package com.eventhub.eventhub_backend.google;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Component
public class NimbusGoogleTokenVerifier implements GoogleTokenVerifier {

    private static final String GOOGLE_KEYS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final String clientId;
    // Deliberately a field and not a Spring bean: our own login tokens already have a JwtDecoder bean
    private final NimbusJwtDecoder decoder;

    public NimbusGoogleTokenVerifier(@Value("${app.google.client-id:}") String configuredClientId) {
        this.clientId = configuredClientId == null ? "" : configuredClientId.trim();

        // Downloads and caches Google's public keys, and follows their rotation
        NimbusJwtDecoder nimbus = NimbusJwtDecoder.withJwkSetUri(GOOGLE_KEYS).build();

        String expectedAudience = this.clientId;
        OAuth2TokenValidator<Jwt> issuerAndExpiry = JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER);
        // The token must have been issued for OUR app, not for some other site's Google login
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(expectedAudience));

        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndExpiry, audience));
        this.decoder = nimbus;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Google sign-in is not configured on this server");
        }

        try {
            Jwt jwt = decoder.decode(idToken);

            String email = jwt.getClaimAsString("email");
            if (email == null || email.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Google did not provide an email address");
            }
            boolean verified = Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"));

            return new GoogleIdentity(jwt.getSubject(), email, verified, jwt.getClaimAsString("name"));
        } catch (BadJwtException e) {
            // Wrong signature, expired, wrong issuer, wrong audience, or not a token at all
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google sign-in token");
        } catch (JwtException e) {
            log.warn("Could not verify a Google token: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not reach Google to verify the sign-in. Please try again");
        }
    }
}