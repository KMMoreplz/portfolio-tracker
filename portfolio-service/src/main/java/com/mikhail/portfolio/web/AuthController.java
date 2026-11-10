package com.mikhail.portfolio.web;

import com.mikhail.portfolio.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Регистрация и вход. Оба эндпоинта открыты; все остальные защищены JWT-токеном,
 * полученным здесь.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<TokenResponseDto> register(@Valid @RequestBody RegisterRequestDto dto,
                                                     UriComponentsBuilder uriBuilder) {
        TokenResponseDto token = authService.register(dto);
        URI location = uriBuilder.path("/api/auth/me").build().toUri();
        return ResponseEntity.created(location).body(token);
    }

    @PostMapping("/login")
    public TokenResponseDto login(@Valid @RequestBody LoginRequestDto dto) {
        return authService.login(dto);
    }

    @GetMapping("/me")
    public AuthenticatedUserDto me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(jwt.getSubject());
    }
}
