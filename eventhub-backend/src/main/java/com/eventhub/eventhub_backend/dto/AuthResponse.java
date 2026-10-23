package com.eventhub.eventhub_backend.dto;

public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserResponse user) {

}