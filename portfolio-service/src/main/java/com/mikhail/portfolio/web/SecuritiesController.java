package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Security;
import com.mikhail.portfolio.service.SecuritiesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Поиск бумаг в справочнике securities (подсказки при добавлении операций). */
@RestController
@RequestMapping("/api/securities")
public class SecuritiesController {

    private final SecuritiesService securitiesService;

    public SecuritiesController(SecuritiesService securitiesService) {
        this.securitiesService = securitiesService;
    }

    @GetMapping
    public List<Security> search(@RequestParam(required = false) String query) {
        return securitiesService.search(query);
    }
}
