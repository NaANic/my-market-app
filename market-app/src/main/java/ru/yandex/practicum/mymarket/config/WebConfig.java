package ru.yandex.practicum.mymarket.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Merges form body fields into getQueryParams() so that @RequestParam
 * in WebFlux controllers can see application/x-www-form-urlencoded values.
 *
 * Spring WebFlux @RequestParam reads from ServerHttpRequest.getQueryParams()
 * only. Form body data is in ServerWebExchange.getFormData() — a separate
 * cache that @RequestParam never touches. Spring Boot's FormWebFilter bridges
 * this by wrapping the request so getQueryParams() returns query string params
 * merged with form body fields. This filter replicates that behaviour.
 */
@Configuration
public class WebConfig {

  @Bean
  @Order(-1)
  public WebFilter formDataMergingFilter() {
    return (exchange, chain) -> {
      MediaType contentType = exchange.getRequest().getHeaders().getContentType();
      if (!MediaType.APPLICATION_FORM_URLENCODED.isCompatibleWith(contentType)) {
        return chain.filter(exchange);
      }
      return exchange.getFormData().flatMap(formData -> {
        if (formData.isEmpty()) {
          return chain.filter(exchange);
        }
        MultiValueMap<String, String> merged = new LinkedMultiValueMap<>();
        merged.addAll(exchange.getRequest().getQueryParams());
        merged.addAll(formData);

        ServerHttpRequest decoratedRequest = new ServerHttpRequestDecorator(exchange.getRequest()) {
          @Override
          public MultiValueMap<String, String> getQueryParams() {
            return merged;
          }
        };

        ServerWebExchange decoratedExchange = new ServerWebExchangeDecorator(exchange) {
          @Override
          public ServerHttpRequest getRequest() {
            return decoratedRequest;
          }
        };

        return chain.filter(decoratedExchange);
      });
    };
  }
}
