package ru.yandex.practicum.mymarket.web;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Starts the WebSession for every incoming request so that controllers do not
 * have to call {@code session.start()} individually.
 *
 * After registering this filter you can remove every {@code session.start()}
 * call from {@link ru.yandex.practicum.mymarket.controller.CartController},
 * {@link ru.yandex.practicum.mymarket.controller.ItemController}, and
 * {@link ru.yandex.practicum.mymarket.controller.OrderController}.
 */
@Component
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
