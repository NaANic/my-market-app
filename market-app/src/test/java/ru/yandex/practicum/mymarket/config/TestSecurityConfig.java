package ru.yandex.practicum.mymarket.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Replaces the real SecurityWebFilterChain in @WebFluxTest slices.
 * Permits all requests and disables CSRF so existing controller tests
 * remain focused on controller behaviour, not security rules.
 * Security-specific behaviour is tested separately in AuthAccessControlTest.
 */
@TestConfiguration
public class TestSecurityConfig {

  @Bean
  public SecurityWebFilterChain testSecurityFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(ex -> ex.anyExchange().permitAll())
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .build();
    // CSRF stays ON — templates reference ${_csrf.parameterName}
  }
}
