package com.eventhub.eventhub_backend.dto;

import com.eventhub.eventhub_backend.entity.User;
import com.eventhub.eventhub_backend.enums.Role;

public record UserResponse(Long id, String name, String email, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}