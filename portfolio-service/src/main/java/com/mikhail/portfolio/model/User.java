package com.mikhail.portfolio.model;

import java.time.OffsetDateTime;

/** Учётная запись пользователя: email и BCrypt-хеш пароля. */
public class User {

    private final Long id;
    private final String email;
    private final String passwordHash;
    private final OffsetDateTime createdAt;

    public User(Long id, String email, String passwordHash, OffsetDateTime createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
