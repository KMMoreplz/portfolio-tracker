package com.mikhail.portfolio.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot 4.1 не даёт бин RestClient.Builder со spring-boot-starter-web
 * (для этого нужен отдельный стартер restclient), поэтому builder
 * объявляется здесь — им пользуется {@link PortfolioEventPublisher}.
 */
@Configuration
public class NotificationConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
