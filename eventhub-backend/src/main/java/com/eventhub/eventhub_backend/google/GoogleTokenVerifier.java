package com.eventhub.eventhub_backend.google;

public interface GoogleTokenVerifier {

    /** Checks the token really came from Google for our app. Throws a 401 if it did not. */
    GoogleIdentity verify(String idToken);
}