package com.eventhub.eventhub_backend.google;

/** What Google vouches for about a person. */
public record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {}