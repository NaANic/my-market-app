package ru.yandex.practicum.mymarket.web;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Starts the WebSession eagerly for every request so controllers can call
 * session.getId() without needing to call session.start() themselves.
 *
 * Runs at @Order(1) — after WebConfig.formWebFilter at @Order(-1) — so
 * form data is already decoded and cached before session handling begins.
 */
@Component
@Order(1)
public class SessionWebFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    return exchange.getSession()
        .doOnNext(session -> {
          if (!session.isStarted()) {
            session.start();
          }
        })
        .then(chain.filter(exchange));
  }
}
