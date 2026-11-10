package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Currency;
import com.mikhail.common.domain.Security;
import com.mikhail.common.domain.Transaction;
import com.mikhail.common.domain.TransactionType;
import com.mikhail.common.marketdata.InstrumentInfo;
import com.mikhail.common.marketdata.MarketDataProvider;
import com.mikhail.portfolio.error.InsufficientQuantityException;
import com.mikhail.portfolio.error.InvalidTransactionException;
import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.error.TransactionNotFoundException;
import com.mikhail.portfolio.model.DividendTotal;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.repository.DividendEventRepository;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.repository.PositionRepository;
import com.mikhail.portfolio.repository.SecurityRepository;
import com.mikhail.portfolio.repository.TransactionRepository;
import com.mikhail.portfolio.web.CreateTransactionRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Операции над портфелем: покупка, продажа, дивиденд.
 *
 * Покупка увеличивает позицию (средневзвешенная цена пересчитывается вместе с комиссией),
 * продажа уменьшает (не больше, чем есть), дивиденд не меняет позицию, но создаёт
 * запись в календаре дивидендов. Изменение и удаление операции сначала откатывают
 * её эффект, затем применяют новый — так позиция всегда соответствует журналу сделок.
 */
@Service
public class TransactionService {

    private static final Pattern TICKER_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9._-]{0,31}$");
    private static final int PRICE_SCALE = 8;

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityRepository securityRepository;
    private final DividendEventRepository dividendEventRepository;
    private final MarketDataProvider marketDataProvider;

    public TransactionService(PortfolioRepository portfolioRepository,
                              PositionRepository positionRepository,
                              TransactionRepository transactionRepository,
                              SecurityRepository securityRepository,
                              DividendEventRepository dividendEventRepository,
                              MarketDataProvider marketDataProvider) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.securityRepository = securityRepository;
        this.dividendEventRepository = dividendEventRepository;
        this.marketDataProvider = marketDataProvider;
    }

    @Transactional
    public Transaction create(Long portfolioId, CreateTransactionRequestDto dto) {
        Portfolio portfolio = requirePortfolio(portfolioId);
        Resolved resolved = resolve(dto, portfolio);
        Transaction transaction = transactionRepository.insert(
                portfolioId, resolved.type(), resolved.ticker(), resolved.quantity(),
                resolved.price(), resolved.commission(), resolved.currency(), resolved.executedAt());
        upsertSecurity(transaction, resolved.instrumentName());
        applyEffect(transaction);
        return transaction;
    }

    /** Полная замена операции: эффект старой версии откатывается, новая применяется заново. */
    @Transactional
    public Transaction update(Long portfolioId, Long id, CreateTransactionRequestDto dto) {
        Transaction previous = requireTransaction(portfolioId, id);
        Portfolio portfolio = requirePortfolio(portfolioId);
        undoEffect(previous);
        Resolved resolved = resolve(dto, portfolio);
        Transaction updated = transactionRepository.update(
                previous.id(), portfolioId, resolved.type(), resolved.ticker(), resolved.quantity(),
                resolved.price(), resolved.commission(), resolved.currency(), resolved.executedAt());
        upsertSecurity(updated, resolved.instrumentName());
        applyEffect(updated);
        return updated;
    }

    /** Удаляет операцию и откатывает её эффект на позицию. */
    @Transactional
    public void delete(Long portfolioId, Long id) {
        Transaction transaction = requireTransaction(portfolioId, id);
        undoEffect(transaction);
        transactionRepository.delete(portfolioId, id);
    }

    @Transactional(readOnly = true)
    public List<Transaction> list(Long portfolioId, TransactionType type) {
        requirePortfolio(portfolioId);
        return transactionRepository.findByPortfolioId(portfolioId, type);
    }

    @Transactional(readOnly = true)
    public Transaction get(Long portfolioId, Long id) {
        requirePortfolio(portfolioId);
        return requireTransaction(portfolioId, id);
    }

    // --- применение и откат эффектов -------------------------------------

    private void applyEffect(Transaction transaction) {
        switch (transaction.type()) {
            case BUY -> applyBuy(transaction);
            case SELL -> applySell(transaction);
            case DIVIDEND -> dividendEventRepository.insert(
                    transaction.portfolioId(),
                    transaction.id(),
                    transaction.ticker(),
                    transaction.executedAt().toLocalDate(),
                    transaction.price(),
                    transaction.quantity(),
                    transaction.grossAmount(),
                    transaction.currency().name());
        }
    }

    private void undoEffect(Transaction transaction) {
        switch (transaction.type()) {
            case BUY -> undoBuy(transaction);
            case SELL -> undoSell(transaction);
            case DIVIDEND -> dividendEventRepository.deleteByTransactionId(transaction.id());
        }
    }

    private void applyBuy(Transaction transaction) {
        Optional<Position> existing = positionRepository.findByPortfolioIdAndTicker(
                transaction.portfolioId(), transaction.ticker());
        if (existing.isEmpty()) {
            BigDecimal averagePrice = transaction.netAmount()
                    .divide(transaction.quantity(), PRICE_SCALE, RoundingMode.HALF_UP);
            positionRepository.insert(
                    transaction.portfolioId(),
                    transaction.ticker(),
                    instrumentName(transaction.ticker()),
                    transaction.quantity(),
                    averagePrice,
                    transaction.currency().name());
            return;
        }
        Position position = existing.get();
        requireSameCurrency(position, transaction);
        BigDecimal newQuantity = position.getQuantity().add(transaction.quantity());
        BigDecimal oldCost = position.getQuantity().multiply(position.getAveragePrice());
        // средневзвешенная: (прежняя стоимость + сумма сделки с комиссией) / новое количество
        BigDecimal newAveragePrice = oldCost.add(transaction.netAmount())
                .divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP);
        positionRepository.update(
                transaction.portfolioId(), position.getId(), position.getInstrumentName(),
                newQuantity, newAveragePrice, position.getCurrency());
    }

    private void applySell(Transaction transaction) {
        Position position = positionRepository
                .findByPortfolioIdAndTicker(transaction.portfolioId(), transaction.ticker())
                .orElseThrow(() -> new InsufficientQuantityException(
                        "нет позиции по тикеру " + transaction.ticker() + " — продажа невозможна"));
        requireSameCurrency(position, transaction);
        if (position.getQuantity().compareTo(transaction.quantity()) < 0) {
            throw new InsufficientQuantityException(
                    "недостаточно бумаг %s: в портфеле %s, в сделке %s".formatted(
                            transaction.ticker(), position.getQuantity(), transaction.quantity()));
        }
        BigDecimal newQuantity = position.getQuantity().subtract(transaction.quantity());
        if (newQuantity.signum() == 0) {
            positionRepository.delete(transaction.portfolioId(), position.getId());
        } else {
            positionRepository.update(
                    transaction.portfolioId(), position.getId(), position.getInstrumentName(),
                    newQuantity, position.getAveragePrice(), position.getCurrency());
        }
    }

    private void undoBuy(Transaction transaction) {
        Optional<Position> existing = positionRepository.findByPortfolioIdAndTicker(
                transaction.portfolioId(), transaction.ticker());
        if (existing.isEmpty()) {
            return;
        }
        Position position = existing.get();
        BigDecimal newQuantity = position.getQuantity().subtract(transaction.quantity());
        if (newQuantity.signum() == 0) {
            positionRepository.delete(transaction.portfolioId(), position.getId());
            return;
        }
        if (newQuantity.signum() < 0) {
            // позицию правили вручную после сделки: точный откат невозможен, не трогаем
            return;
        }
        BigDecimal oldCost = position.getQuantity().multiply(position.getAveragePrice());
        BigDecimal newCost = oldCost.subtract(transaction.netAmount());
        BigDecimal newAveragePrice = newCost.signum() > 0
                ? newCost.divide(newQuantity, PRICE_SCALE, RoundingMode.HALF_UP)
                : position.getAveragePrice();
        positionRepository.update(
                transaction.portfolioId(), position.getId(), position.getInstrumentName(),
                newQuantity, newAveragePrice, position.getCurrency());
    }

    private void undoSell(Transaction transaction) {
        Optional<Position> existing = positionRepository.findByPortfolioIdAndTicker(
                transaction.portfolioId(), transaction.ticker());
        if (existing.isEmpty()) {
            // позицию удалили вручную — восстанавливать базу не из чего
            return;
        }
        Position position = existing.get();
        positionRepository.update(
                transaction.portfolioId(), position.getId(), position.getInstrumentName(),
                position.getQuantity().add(transaction.quantity()),
                position.getAveragePrice(),
                position.getCurrency());
    }

    // --- справочник бумаг и вспомогательные --------------------------------

    /** Тикер попадает в справочник securities при каждой операции; имя берётся из провайдера. */
    private void upsertSecurity(Transaction transaction, String requestedName) {
        Optional<InstrumentInfo> instrument = marketDataProvider.findInstrument(transaction.ticker());
        String name = instrument.map(InstrumentInfo::name)
                .orElse(requestedName == null || requestedName.isBlank()
                        ? transaction.ticker()
                        : requestedName.trim());
        String isin = instrument.map(InstrumentInfo::isin).orElse(null);
        securityRepository.upsert(transaction.ticker(), name, transaction.currency(), isin);
    }

    private String instrumentName(String ticker) {
        return marketDataProvider.findInstrument(ticker).map(InstrumentInfo::name).orElse(null);
    }

    private void requireSameCurrency(Position position, Transaction transaction) {
        if (!position.getCurrency().equals(transaction.currency().name())) {
            throw new InvalidTransactionException(
                    "валюта сделки (%s) не совпадает с валютой позиции (%s)".formatted(
                            transaction.currency().name(), position.getCurrency()));
        }
    }

    private Portfolio requirePortfolio(Long portfolioId) {
        return portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    private Transaction requireTransaction(Long portfolioId, Long id) {
        return transactionRepository.findById(portfolioId, id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private Resolved resolve(CreateTransactionRequestDto dto, Portfolio portfolio) {
        String ticker = dto.ticker().trim().toUpperCase(Locale.ROOT);
        if (!TICKER_PATTERN.matcher(ticker).matches()) {
            throw new InvalidTransactionException("некорректный тикер: " + dto.ticker());
        }
        String code = dto.currency() == null || dto.currency().isBlank()
                ? portfolio.getBaseCurrency()
                : dto.currency();
        Currency currency = Currency.fromCode(code);
        if (currency == null) {
            throw new InvalidTransactionException("неизвестная валюта: " + code);
        }
        OffsetDateTime executedAt = dto.executedAt() == null ? OffsetDateTime.now() : dto.executedAt();
        BigDecimal commission = dto.commission() == null ? BigDecimal.ZERO : dto.commission();
        String instrumentName = dto.instrumentName() == null ? null : dto.instrumentName().trim();
        return new Resolved(dto.type(), ticker, dto.quantity(), dto.price(), commission,
                currency, executedAt, instrumentName);
    }

    /** Нормализованные параметры операции после валидации. */
    private record Resolved(TransactionType type, String ticker, BigDecimal quantity, BigDecimal price,
                            BigDecimal commission, Currency currency, OffsetDateTime executedAt,
                            String instrumentName) {
    }
}
