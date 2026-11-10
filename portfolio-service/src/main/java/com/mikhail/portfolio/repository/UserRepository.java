package com.mikhail.portfolio.repository;

import com.mikhail.portfolio.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class UserRepository {

    private static final String COLUMNS = "id, email, password_hash, created_at";

    private static final RowMapper<User> ROW_MAPPER = (rs, rowNum) -> new User(
            rs.getLong("id"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getObject("created_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public UserRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public User insert(String email, String passwordHash) {
        return jdbcClient.sql("""
                INSERT INTO users (email, password_hash)
                VALUES (:email, :passwordHash)
                RETURNING %s
                """.formatted(COLUMNS))
                .param("email", email)
                .param("passwordHash", passwordHash)
                .query(ROW_MAPPER)
                .single();
    }

    public Optional<User> findByEmail(String email) {
        return jdbcClient.sql("SELECT %s FROM users WHERE email = :email".formatted(COLUMNS))
                .param("email", email)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<User> findById(Long id) {
        return jdbcClient.sql("SELECT %s FROM users WHERE id = :id".formatted(COLUMNS))
                .param("id", id)
                .query(ROW_MAPPER)
                .optional();
    }

    public boolean existsByEmail(String email) {
        Integer count = jdbcClient.sql("SELECT count(*) FROM users WHERE email = :email")
                .param("email", email)
                .query(Integer.class)
                .single();
        return count > 0;
    }
}
