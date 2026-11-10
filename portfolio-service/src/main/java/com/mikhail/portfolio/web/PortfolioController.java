package com.mikhail.portfolio.web;

import com.mikhail.portfolio.model.Portfolio;
import com.mikhail.portfolio.notification.NotificationEvent;
import com.mikhail.portfolio.notification.PortfolioEventPublisher;
import com.mikhail.portfolio.service.PortfolioService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/portfolios")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final PortfolioEventPublisher eventPublisher;

    public PortfolioController(PortfolioService portfolioService,
                               PortfolioEventPublisher eventPublisher) {
        this.portfolioService = portfolioService;
        this.eventPublisher = eventPublisher;
    }

    @PostMapping
    public ResponseEntity<PortfolioDto> create(@Valid @RequestBody CreatePortfolioRequestDto dto,
                                               UriComponentsBuilder uriBuilder) {
        Portfolio created = portfolioService.create(dto);
        eventPublisher.publish(NotificationEvent.portfolioCreated(created));
        URI location = uriBuilder.path("/api/portfolios/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(PortfolioDto.from(created));
    }

    @GetMapping
    public List<PortfolioDto> list() {
        return portfolioService.listAll().stream().map(PortfolioDto::from).toList();
    }

    @GetMapping("/{id}")
    public PortfolioDto get(@PathVariable Long id) {
        return PortfolioDto.from(portfolioService.getById(id));
    }

    @PutMapping("/{id}")
    public PortfolioDto update(@PathVariable Long id, @Valid @RequestBody UpdatePortfolioRequestDto dto) {
        return PortfolioDto.from(portfolioService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        Portfolio deleted = portfolioService.getById(id);
        portfolioService.delete(id);
        eventPublisher.publish(NotificationEvent.portfolioDeleted(deleted));
    }
}
