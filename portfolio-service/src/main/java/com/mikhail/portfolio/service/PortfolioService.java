package com.mikhail.portfolio.service;

import com.mikhail.portfolio.error.PortfolioNotFoundException;
import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.repository.PortfolioRepository;
import com.mikhail.portfolio.web.CreatePortfolioRequestDto;
import com.mikhail.portfolio.web.UpdatePortfolioRequestDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class PortfolioService {

    public static final String DEFAULT_CURRENCY = "RUB";

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public Portfolio create(CreatePortfolioRequestDto dto) {
        String name = dto.name().trim();
        String baseCurrency = isBlank(dto.baseCurrency()) ? DEFAULT_CURRENCY : dto.baseCurrency();
        return portfolioRepository.insert(name, baseCurrency);
    }

    @Transactional(readOnly = true)
    public Portfolio getById(Long id) {
        return portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<Portfolio> listAll() {
        return portfolioRepository.findAll();
    }

    public Portfolio update(Long id, UpdatePortfolioRequestDto dto) {
        Portfolio current = getById(id);
        String name = dto.name().trim();
        String baseCurrency = isBlank(dto.baseCurrency()) ? current.getBaseCurrency() : dto.baseCurrency();
        return portfolioRepository.update(id, name, baseCurrency)
                .orElseThrow(() -> new PortfolioNotFoundException(id));
    }

    public void delete(Long id) {
        if (!portfolioRepository.deleteById(id)) {
            throw new PortfolioNotFoundException(id);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
