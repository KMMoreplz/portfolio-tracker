package com.mikhail.portfolio.service;

import com.mikhail.portfolio.error.InvalidCredentialsException;
import com.mikhail.portfolio.error.UserAlreadyExistsException;
import com.mikhail.portfolio.model.User;
import com.mikhail.portfolio.repository.UserRepository;
import com.mikhail.portfolio.security.JwtService;
import com.mikhail.portfolio.web.AuthenticatedUserDto;
import com.mikhail.portfolio.web.LoginRequestDto;
import com.mikhail.portfolio.web.RegisterRequestDto;
import com.mikhail.portfolio.web.TokenResponseDto;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Регистрация и вход. Пароль хранится только в виде BCrypt-хеша,
 * при входе пароль проверяет Spring Security ({@link AuthenticationManager}),
 * после успешной проверки выдаётся JWT.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    /** Создаёт учётную запись и сразу возвращает токен, чтобы клиент не логинился повторно. */
    @Transactional
    public TokenResponseDto register(RegisterRequestDto dto) {
        String email = normalizeEmail(dto.email());
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException(email);
        }
        User user = userRepository.insert(email, passwordEncoder.encode(dto.password()));
        return tokenResponse(user);
    }

    /** Проверяет пароль через Spring Security и выдаёт токен. */
    public TokenResponseDto login(LoginRequestDto dto) {
        String email = normalizeEmail(dto.email());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, dto.password()));
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return tokenResponse(user);
    }

    /** Данные текущего пользователя по email из проверенного токена. */
    public AuthenticatedUserDto currentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);
        return new AuthenticatedUserDto(user.getId(), user.getEmail());
    }

    private TokenResponseDto tokenResponse(User user) {
        String token = jwtService.issueToken(user.getEmail(), user.getId());
        return new TokenResponseDto(token, "Bearer", jwtService.ttlSeconds(), user.getId(), user.getEmail());
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
