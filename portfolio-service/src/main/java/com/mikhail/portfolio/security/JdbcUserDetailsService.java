package com.mikhail.portfolio.security;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Читает учётные записи из таблицы users (пароль хранится в виде BCrypt-хеша). */
@Service
public class JdbcUserDetailsService implements UserDetailsService {

    private final JdbcClient jdbcClient;

    public JdbcUserDetailsService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return jdbcClient.sql("SELECT email, password_hash FROM users WHERE email = :email")
                .param("email", email)
                .query((rs, rowNum) -> User.withUsername(rs.getString("email"))
                        .password(rs.getString("password_hash"))
                        .roles("USER")
                        .build())
                .optional()
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь " + email + " не найден"));
    }
}
