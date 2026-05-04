package ru.yandex.practicum.payment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;

/**
 * Security configuration for payment-service.
 *
 * <p>Configured as an OAuth2 resource server. Every business endpoint
 * ({@code GET /balance}, {@code POST /payment}) requires a valid Keycloak-issued
 * JWT in the {@code Authorization: Bearer <token>} header.
 *
 * <p><b>Public endpoints:</b>
 * <ul>
 *   <li>{@code /actuator/health} — used by Docker Compose healthcheck and
 *       must remain reachable without a token, otherwise the container is
 *       reported unhealthy and {@code market-app} won't start.</li>
 * </ul>
 *
 * <p><b>CSRF</b> is disabled because this service is a stateless JSON API
 * accessed only by other services (no browser, no session cookies).
 *
 * <p>The {@link #securityWebFilterChain} method delegates JWT validation to
 * {@code .oauth2ResourceServer(...).jwt()} — Spring Security autoconfigures
 * a {@code ReactiveJwtDecoder} from the {@code spring.security.oauth2
 * .resourceserver.jwt.issuer-uri} property in {@code application.yml}.
 * That decoder fetches the JWK set from the issuer's
 * {@code /.well-known/openid-configuration} document at startup and uses it
 * to validate the signature, expiry, and issuer claim of every incoming JWT.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

  @Bean
  public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
    return http
        .authorizeExchange(ex -> ex
            .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
            .anyExchange().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(org.springframework.security.config.Customizer.withDefaults()))
        .csrf(ServerHttpSecurity.CsrfSpec::disable)
        .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
        .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
        .build();
  }
}
