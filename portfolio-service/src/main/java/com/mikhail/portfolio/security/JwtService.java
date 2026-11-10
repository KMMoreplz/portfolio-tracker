package com.mikhail.portfolio.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * Выпуск токенов входа: подписанный JWT (HS256) с subject = email и claim {@code userId}.
 * Проверку подписи и срока выполняет Spring Security через {@link org.springframework.security.oauth2.jwt.JwtDecoder},
 * настроенный в {@link SecurityConfig} тем же секретом.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final SecretKey signingKey;
    private final Duration ttl;

    public JwtService(@Value("${app.security.jwt.secret}") String secret,
                      @Value("${app.security.jwt.ttl-minutes}") long ttlMinutes) {
        this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(signingKey));
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    /** Подписанный токен для пользователя с указанным email. */
    public String issueToken(String email, Long userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("portfolio-service")
                .subject(email)
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("userId", userId)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /** Срок жизни токена в секундах — клиенту в ответе на вход. */
    public long ttlSeconds() {
        return ttl.toSeconds();
    }

    /** Ключ подписи — им же проверяется входящий токен в SecurityConfig. */
    public SecretKey signingKey() {
        return signingKey;
    }
}
