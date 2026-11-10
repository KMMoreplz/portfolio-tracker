package com.mikhail.portfolio.repository;

import com.mikhail.portfolio.model.UserSettings;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Настройки пользователя: строка создаётся при первом сохранении,
 * чтение отсутствующей строки — пустой Optional (настройки ещё не задавались).
 */
@Repository
public class UserSettingsRepository {

    private static final String COLUMNS = "user_id, price_alert_threshold, telegram_link_code, telegram_link_code_expires_at";

    private static final RowMapper<UserSettings> ROW_MAPPER = (rs, rowNum) -> new UserSettings(
            rs.getLong("user_id"),
            rs.getBigDecimal("price_alert_threshold"),
            rs.getString("telegram_link_code"),
            rs.getObject("telegram_link_code_expires_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public UserSettingsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<UserSettings> findByUserId(Long userId) {
        return jdbcClient.sql("SELECT %s FROM user_settings WHERE user_id = :userId".formatted(COLUMNS))
                .param("userId", userId)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Сохраняет порог алертов, создавая строку настроек при необходимости. */
    public void saveThreshold(Long userId, BigDecimal threshold) {
        jdbcClient.sql("""
                INSERT INTO user_settings (user_id, price_alert_threshold)
                VALUES (:userId, :threshold)
                ON CONFLICT (user_id) DO UPDATE
                    SET price_alert_threshold = EXCLUDED.price_alert_threshold,
                        updated_at = now()
                """)
                .param("userId", userId)
                .param("threshold", threshold)
                .update();
    }

    /**
     * Активный код привязки: строка с кодом, у которой срок жизни не истёк.
     * Просроченные коды не находятся и считаются не выданными.
     */
    public Optional<UserSettings> findActiveLinkCode(String code) {
        return jdbcClient.sql("""
                SELECT %s FROM user_settings
                WHERE telegram_link_code = :code AND telegram_link_code_expires_at > now()
                """.formatted(COLUMNS))
                .param("code", code)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Гасит код привязки: использованный код не должен сработать повторно. */
    public void clearLinkCode(Long userId) {
        jdbcClient.sql("""
                UPDATE user_settings
                SET telegram_link_code = NULL,
                    telegram_link_code_expires_at = NULL,
                    updated_at = now()
                WHERE user_id = :userId
                """)
                .param("userId", userId)
                .update();
    }

    /** Сохраняет новый одноразовый код привязки, заменяя предыдущий. */
    public void saveLinkCode(Long userId, String code, OffsetDateTime expiresAt) {
        jdbcClient.sql("""
                INSERT INTO user_settings (user_id, telegram_link_code, telegram_link_code_expires_at)
                VALUES (:userId, :code, :expiresAt)
                ON CONFLICT (user_id) DO UPDATE
                    SET telegram_link_code = EXCLUDED.telegram_link_code,
                        telegram_link_code_expires_at = EXCLUDED.telegram_link_code_expires_at,
                        updated_at = now()
                """)
                .param("userId", userId)
                .param("code", code)
                .param("expiresAt", expiresAt)
                .update();
    }
}
