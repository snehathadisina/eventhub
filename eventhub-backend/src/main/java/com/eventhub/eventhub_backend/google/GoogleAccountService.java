package com.eventhub.eventhub_backend.google;

import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.AuthProvider;
import com.eventhub.eventhub_backend.enums.Role;
import com.eventhub.eventhub_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class GoogleAccountService {

    private final UserRepository userRepository;

    /**
     * 1. Known Google id: that is the user.
     * 2. Same email as an existing account: link them (Google has verified the email).
     * 3. Otherwise create a new attendee account.
     */
    @Transactional
    public User findOrCreate(GoogleIdentity identity) {
        String email = identity.email().trim().toLowerCase(Locale.ROOT);

        return userRepository.findByGoogleId(identity.subject())
                .orElseGet(() -> userRepository.findByEmail(email)
                        .map(existing -> link(existing, identity))
                        .orElseGet(() -> create(identity, email)));
    }

    private User link(User existing, GoogleIdentity identity) {
        if (existing.getGoogleId() != null && !existing.getGoogleId().equals(identity.subject())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This email is already linked to a different Google account");
        }

        existing.setGoogleId(identity.subject());
        existing.setAuthProvider(AuthProvider.GOOGLE);
        // Registration does not verify emails, so an old password may have been set by someone
        // who never owned this address. Google has now proven who the owner is, so the password goes.
        existing.setPasswordHash(null);
        return existing;
    }

    private User create(GoogleIdentity identity, String email) {
        User user = new User();
        user.setName(displayName(identity.name(), email));
        user.setEmail(email);
        user.setPasswordHash(null);
        user.setAuthProvider(AuthProvider.GOOGLE);
        user.setRole(Role.ATTENDEE);
        user.setGoogleId(identity.subject());
        // Flush now, so a duplicate (two sign-ins at once) fails here and can be retried
        return userRepository.saveAndFlush(user);
    }

    private String displayName(String googleName, String email) {
        String name = googleName == null ? "" : googleName.trim();
        if (name.isEmpty()) {
            name = email.substring(0, email.indexOf('@'));
        }
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}