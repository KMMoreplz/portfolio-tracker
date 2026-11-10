package com.mikhail.portfolio.web;

import com.mikhail.common.domain.Transaction;
import com.mikhail.common.domain.TransactionType;
import com.mikhail.portfolio.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * CRUD операций портфеля (покупка, продажа, дивиденд) в пределах портфеля:
 * все маршруты вложены в /api/portfolios/{portfolioId}, чужие операции недоступны.
 */
@RestController
@RequestMapping("/api/portfolios/{portfolioId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public List<Transaction> list(@PathVariable Long portfolioId,
                                  @RequestParam(required = false) TransactionType type) {
        return transactionService.list(portfolioId, type);
    }

    @GetMapping("/{id}")
    public Transaction get(@PathVariable Long portfolioId, @PathVariable Long id) {
        return transactionService.get(portfolioId, id);
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@PathVariable Long portfolioId,
                                              @Valid @RequestBody CreateTransactionRequestDto dto,
                                              UriComponentsBuilder uriBuilder) {
        Transaction created = transactionService.create(portfolioId, dto);
        URI location = uriBuilder.path("/api/portfolios/{portfolioId}/transactions/{id}")
                .buildAndExpand(portfolioId, created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /** Полная замена операции теми же полями, что и при создании. */
    @PutMapping("/{id}")
    public Transaction update(@PathVariable Long portfolioId, @PathVariable Long id,
                              @Valid @RequestBody CreateTransactionRequestDto dto) {
        return transactionService.update(portfolioId, id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long portfolioId, @PathVariable Long id) {
        transactionService.delete(portfolioId, id);
    }
}
