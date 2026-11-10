package com.mikhail.portfolio.web;

import com.mikhail.portfolio.model.Position;
import com.mikhail.portfolio.service.PositionService;
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
@RequestMapping("/api/portfolios/{portfolioId}/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public List<PositionDto> list(@PathVariable Long portfolioId) {
        return positionService.listByPortfolio(portfolioId).stream().map(PositionDto::from).toList();
    }

    @PostMapping
    public ResponseEntity<PositionDto> add(@PathVariable Long portfolioId,
                                           @Valid @RequestBody CreatePositionRequestDto dto,
                                           UriComponentsBuilder uriBuilder) {
        Position created = positionService.add(portfolioId, dto);
        URI location = uriBuilder
                .path("/api/portfolios/{portfolioId}/positions/{positionId}")
                .buildAndExpand(portfolioId, created.getId())
                .toUri();
        return ResponseEntity.created(location).body(PositionDto.from(created));
    }

    @GetMapping("/{positionId}")
    public PositionDto get(@PathVariable Long portfolioId, @PathVariable Long positionId) {
        return PositionDto.from(positionService.get(portfolioId, positionId));
    }

    @PutMapping("/{positionId}")
    public PositionDto update(@PathVariable Long portfolioId, @PathVariable Long positionId,
                              @Valid @RequestBody UpdatePositionRequestDto dto) {
        return PositionDto.from(positionService.update(portfolioId, positionId, dto));
    }

    @DeleteMapping("/{positionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long portfolioId, @PathVariable Long positionId) {
        positionService.delete(portfolioId, positionId);
    }
}
