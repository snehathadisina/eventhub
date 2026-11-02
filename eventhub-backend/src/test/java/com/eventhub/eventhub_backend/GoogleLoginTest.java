package com.eventhub.eventhub_backend;

import com.eventhub.eventhub_backend.dto.AuthResponse;
import com.eventhub.eventhub_backend.dto.LoginRequest;
import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.AuthProvider;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.google.GoogleIdentity;
import com.eventhub.eventhub_backend.google.GoogleLoginService;
import com.eventhub.eventhub_backend.google.GoogleTokenVerifier;
import com.eventhub.eventhub_backend.repository.UserRepository;
import com.eventhub.eventhub_backend.service.AuthService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GoogleLoginTest {

    @MockitoBean private GoogleTokenVerifier verifier;

    @Autowired private GoogleLoginService googleLoginService;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @BeforeEach
    void setUp() {
        assertThat(datasourceUrl)
                .as("Refusing to run: this test must only use the eventhub_test database")
                .contains("eventhub_test");
    }

    @Test
    void aNewGoogleUserIsCreatedAsAnAttendee() {
        String email = uniqueEmail();
        GoogleIdentity identity = identity("g-" + UUID.randomUUID(), email, true, "Asha Rao");
        when(verifier.verify("token")).thenReturn(identity);

        AuthResponse response = googleLoginService.login("token");

        assertThat(response.token()).isNotBlank();
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().name()).isEqualTo("Asha Rao");
        assertThat(response.user().role()).isEqualTo(Role.ATTENDEE);

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getGoogleId()).isEqualTo(identity.subject());
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void signingInAgain_returnsTheSameUser() {
        GoogleIdentity identity = identity("g-" + UUID.randomUUID(), uniqueEmail(), true, "Asha Rao");
        when(verifier.verify("token")).thenReturn(identity);

        Long first = googleLoginService.login("token").user().id();
        Long second = googleLoginService.login("token").user().id();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void anExistingPasswordAccount_isLinked_andItsPasswordIsCleared() {
        String email = uniqueEmail();
        User existing = passwordUser(email, "password123");
        authService.login(new LoginRequest(email, "password123")); // works before linking

        when(verifier.verify("token")).thenReturn(identity("g-" + UUID.randomUUID(), email, true, "Asha"));
        AuthResponse response = googleLoginService.login("token");

        assertThat(response.user().id()).isEqualTo(existing.getId()); // same account, bookings kept
        User linked = userRepository.findById(existing.getId()).orElseThrow();
        assertThat(linked.getGoogleId()).isNotNull();
        assertThat(linked.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(linked.getPasswordHash()).isNull();
        assertRejected(HttpStatus.UNAUTHORIZED, () -> authService.login(new LoginRequest(email, "password123")));
    }

    @Test
    void emailMatchingIgnoresCapitalLetters() {
        String lower = uniqueEmail();
        User existing = passwordUser(lower.toUpperCase(), "password123");

        when(verifier.verify("token")).thenReturn(identity("g-" + UUID.randomUUID(), lower, true, "Asha"));

        assertThat(googleLoginService.login("token").user().id()).isEqualTo(existing.getId());
    }

    @Test
    void anUnverifiedGoogleEmail_isRejected_andNoUserIsCreated() {
        String email = uniqueEmail();
        when(verifier.verify("token")).thenReturn(identity("g-" + UUID.randomUUID(), email, false, "Asha"));

        assertRejected(HttpStatus.UNAUTHORIZED, () -> googleLoginService.login("token"));

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void anInvalidToken_isRejected() {
        when(verifier.verify("bad")).thenThrow(
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Google sign-in token"));

        assertRejected(HttpStatus.UNAUTHORIZED, () -> googleLoginService.login("bad"));
    }

    @Test
    void anEmailLinkedToAnotherGoogleAccount_isRejected() {
        String email = uniqueEmail();
        when(verifier.verify("first")).thenReturn(identity("g-" + UUID.randomUUID(), email, true, "Asha"));
        googleLoginService.login("first");

        when(verifier.verify("second")).thenReturn(identity("g-" + UUID.randomUUID(), email, true, "Asha"));

        assertRejected(HttpStatus.CONFLICT, () -> googleLoginService.login("second"));
    }

    @Test
    void aMissingName_fallsBackToTheEmailPrefix() {
        String prefix = "person" + UUID.randomUUID().toString().substring(0, 8);
        when(verifier.verify("token")).thenReturn(
                identity("g-" + UUID.randomUUID(), prefix + "@test.local", true, null));

        assertThat(googleLoginService.login("token").user().name()).isEqualTo(prefix);
    }

    @Test
    void tenFirstTimeSignInsAtOnce_createExactlyOneUser() throws Exception {
        String email = uniqueEmail();
        GoogleIdentity identity = identity("g-" + UUID.randomUUID(), email, true, "Asha");
        when(verifier.verify("token")).thenReturn(identity);

        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        Set<Long> userIds = ConcurrentHashMap.newKeySet();
        Queue<String> errors = new ConcurrentLinkedQueue<>();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    userIds.add(googleLoginService.login("token").user().id());
                } catch (RuntimeException e) {
                    errors.add(e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(errors).as("unexpected errors").isEmpty();
        assertThat(userIds).as("distinct users").hasSize(1);
        assertThat(userRepository.findByEmail(email)).isPresent();
    }

    private String uniqueEmail() {
        return "google-" + UUID.randomUUID() + "@test.local";
    }

    private GoogleIdentity identity(String subject, String email, boolean verified, String name) {
        return new GoogleIdentity(subject, email, verified, name);
    }

    private User passwordUser(String email, String password) {
        User user = new User();
        user.setName("Existing User");
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(Role.ATTENDEE);
        user.setAuthProvider(AuthProvider.LOCAL);
        return userRepository.save(user);
    }

    private void assertRejected(HttpStatus status, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode().value()).isEqualTo(status.value()));
    }
}