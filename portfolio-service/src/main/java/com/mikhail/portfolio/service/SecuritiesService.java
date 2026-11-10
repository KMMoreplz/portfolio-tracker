package com.mikhail.portfolio.service;

import com.mikhail.common.domain.Security;
import com.mikhail.portfolio.repository.SecurityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Справочник ценных бумаг: заполняется из операций, читается поиском для интерфейса. */
@Service
public class SecuritiesService {

    private final SecurityRepository securityRepository;

    public SecuritiesService(SecurityRepository securityRepository) {
        this.securityRepository = securityRepository;
    }

    /** Пустой запрос — первые записи справочника; по тексту — поиск по тикеру и наименованию. */
    @Transactional(readOnly = true)
    public List<Security> search(String query) {
        if (query == null || query.isBlank()) {
            return securityRepository.search("");
        }
        return securityRepository.search(query.trim());
    }
}
