package com.mikhail.portfolio.service;

import com.mikhail.portfolio.model.User;
import com.mikhail.portfolio.model.UserSettings;
import com.mikhail.portfolio.repository.UserRepository;
import com.mikhail.portfolio.repository.UserSettingsRepository;
import com.mikhail.portfolio.web.SettingsDto;
import com.mikhail.portfolio.web.UpdateSettingsRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Настройки пользователя: порог алертов и одноразовый код привязки Telegram-бота.
 *
 * Код выдаётся на 15 минут и заменяет предыдущий при повторном запросе;
 * проверку кода выполняет telegram-bot-service — контракт: код хранится в
 * user_settings (столбцы telegram_link_code / telegram_link_code_expires_at)
 * и запрашивается у portfolio-service по тому же JWT-пользователю.
 */
@Service
public class SettingsService {

    /** Срок жизни одноразового кода привязки. */
    static final Duration LINK_CODE_TTL = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    public SettingsService(UserSettingsRepository userSettingsRepository,
                           UserRepository userRepository) {
        this.userSettingsRepository = userSettingsRepository;
        this.userRepository = userRepository;
    }

    /** Текущие настройки: несохранённые поля отдаются как null. */
    @Transactional(readOnly = true)
    public SettingsDto get(User user) {
        return toDto(userSettingsRepository.findByUserId(user.getId()));
    }

    /** Сохраняет порог алертов; {@code null} в DTO сбрасывает порог. */
    @Transactional
    public SettingsDto update(User user, UpdateSettingsRequestDto dto) {
        userSettingsRepository.saveThreshold(user.getId(), dto.priceAlertThreshold());
        return get(user);
    }

    /** Выдаёт новый одноразовый код привязки, предыдущий код при этом перестаёт действовать. */
    @Transactional
    public SettingsDto generateLinkCode(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        userSettingsRepository.saveLinkCode(user.getId(), code, OffsetDateTime.now().plus(LINK_CODE_TTL));
        return get(user);
    }

    /**
     * Погашает одноразовый код привязки: находит пользователя по активному коду
     * и немедленно гасит код, чтобы он не сработал повторно.
     *
     * @return данные пользователя для привязки; пусто, если код не найден или истёк
     */
    @Transactional
    public Optional<LinkGrant> consumeLinkCode(String code) {
        return userSettingsRepository.findActiveLinkCode(code).map(settings -> {
            userSettingsRepository.clearLinkCode(settings.userId());
            User user = userRepository.findById(settings.userId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Пользователь по коду привязки не найден: " + settings.userId()));
            return new LinkGrant(user, settings.priceAlertThreshold());
        });
    }

    /**
     * Результат погашения кода привязки.
     *
     * @param user                пользователь, которому принадлежал код
     * @param priceAlertThreshold порог алертов из настроек, null — не задан
     */
    public record LinkGrant(User user, BigDecimal priceAlertThreshold) {
    }

    /** Пользователь из проверенного токена: subject — email учётной записи. */
    public User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь из токена не найден: " + email));
    }

    private SettingsDto toDto(Optional<UserSettings> settings) {
        if (settings.isEmpty()) {
            return new SettingsDto(null, null, null);
        }
        UserSettings found = settings.get();
        boolean codeActive = found.telegramLinkCode() != null
                && found.telegramLinkCodeExpiresAt() != null
                && found.telegramLinkCodeExpiresAt().isAfter(OffsetDateTime.now());
        return new SettingsDto(
                found.priceAlertThreshold(),
                codeActive ? found.telegramLinkCode() : null,
                codeActive ? found.telegramLinkCodeExpiresAt() : null);
    }
}
