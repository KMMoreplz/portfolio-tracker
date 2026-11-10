package com.mikhail.portfolio.service;

import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.error.PositionAlreadyExistsException;
import com.mikhail.portfolio.error.PositionNotFoundException;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.repository.PositionRepository;
import com.mikhail.portfolio.web.CreatePositionRequestDto;
import com.mikhail.portfolio.web.UpdatePositionRequestDto;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PositionService {

    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;

    public PositionService(PortfolioRepository portfolioRepository, PositionRepository positionRepository) {
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional(readOnly = true)
    public List<Position> listByPortfolio(Long portfolioId) {
        requirePortfolioExists(portfolioId);
        return positionRepository.findByPortfolioId(portfolioId);
    }

    @Transactional(readOnly = true)
    public Position get(Long portfolioId, Long positionId) {
        requirePortfolioExists(portfolioId);
        return positionRepository.findById(portfolioId, positionId)
                .orElseThrow(() -> new PositionNotFoundException(portfolioId, positionId));
    }

    public Position add(Long portfolioId, CreatePositionRequestDto dto) {
        requirePortfolioExists(portfolioId);
        String ticker = dto.ticker().trim();
        String currency = isBlank(dto.currency()) ? PortfolioService.DEFAULT_CURRENCY : dto.currency();
        try {
            return positionRepository.insert(portfolioId, ticker, dto.instrumentName(),
                    dto.quantity(), dto.averagePrice(), currency);
        } catch (DuplicateKeyException e) {
            throw new PositionAlreadyExistsException(portfolioId, ticker);
        }
    }

    public Position update(Long portfolioId, Long positionId, UpdatePositionRequestDto dto) {
        requirePortfolioExists(portfolioId);
        Position current = positionRepository.findById(portfolioId, positionId)
                .orElseThrow(() -> new PositionNotFoundException(portfolioId, positionId));
        String instrumentName = dto.instrumentName() != null ? dto.instrumentName() : current.getInstrumentName();
        String currency = isBlank(dto.currency()) ? current.getCurrency() : dto.currency();
        return positionRepository.update(portfolioId, positionId, instrumentName,
                        dto.quantity(), dto.averagePrice(), currency)
                .orElseThrow(() -> new PositionNotFoundException(portfolioId, positionId));
    }

    public void delete(Long portfolioId, Long positionId) {
        requirePortfolioExists(portfolioId);
        if (!positionRepository.delete(portfolioId, positionId)) {
            throw new PositionNotFoundException(portfolioId, positionId);
        }
    }

    private void requirePortfolioExists(Long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
