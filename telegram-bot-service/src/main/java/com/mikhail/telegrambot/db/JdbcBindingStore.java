package com.mikhail.telegrambot.db;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Связи чат ↔ пользователь в H2 (таблица chat_bindings). */
@Repository
public class JdbcBindingStore implements ChatBindingStore {

    private static final String COLUMNS =
            "chat_id, user_id, email, token, alerts_enabled, bound_at";

    private static final RowMapper<ChatBinding> ROW_MAPPER = (rs, rowNum) -> new ChatBinding(
            rs.getLong("chat_id"),
            rs.getLong("user_id"),
            rs.getString("email"),
            rs.getString("token"),
            rs.getBoolean("alerts_enabled"),
            rs.getObject("bound_at", OffsetDateTime.class));

    private final JdbcClient jdbcClient;

    public JdbcBindingStore(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<ChatBinding> findByChatId(long chatId) {
        return jdbcClient.sql("SELECT %s FROM chat_bindings WHERE chat_id = :chatId".formatted(COLUMNS))
                .param("chatId", chatId)
                .query(ROW_MAPPER)
                .optional();
    }

    @Override
    public List<ChatBinding> findAll() {
        return jdbcClient.sql("SELECT %s FROM chat_bindings ORDER BY chat_id".formatted(COLUMNS))
                .query(ROW_MAPPER)
                .list();
    }

    @Override
    public void save(ChatBinding binding) {
        jdbcClient.sql("""
                MERGE INTO chat_bindings (chat_id, user_id, email, token, alerts_enabled, bound_at)
                KEY (chat_id)
                VALUES (:chatId, :userId, :email, :token, :alertsEnabled, :boundAt)
                """)
                .param("chatId", binding.chatId())
                .param("userId", binding.userId())
                .param("email", binding.email())
                .param("token", binding.token())
                .param("alertsEnabled", binding.alertsEnabled())
                .param("boundAt", binding.boundAt())
                .update();
    }

    @Override
    public boolean deleteByChatId(long chatId) {
        int deleted = jdbcClient.sql("DELETE FROM chat_bindings WHERE chat_id = :chatId")
                .param("chatId", chatId)
                .update();
        return deleted > 0;
    }

    @Override
    public void setAlertsEnabled(long chatId, boolean enabled) {
        jdbcClient.sql("UPDATE chat_bindings SET alerts_enabled = :enabled WHERE chat_id = :chatId")
                .param("enabled", enabled)
                .param("chatId", chatId)
                .update();
    }
}
