package ru.yandex.practicum.payment;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Verifies the OAuth2 resource server rules of payment-service:
 * <ul>
 *   <li>{@code GET /balance} without Authorization header → {@code 401}.</li>
 *   <li>{@code POST /payment} without Authorization header → {@code 401}.</li>
 *   <li>{@code GET /balance} with a mock JWT → {@code 200}.</li>
 *   <li>{@code GET /actuator/health} → {@code 200} without authentication
 *       (required for Docker Compose healthcheck).</li>
 * </ul>
 *
 * <p>Spring Boot's resource-server auto-config normally tries to fetch the JWK
 * set from Keycloak at startup. To avoid that, this test:
 * <ol>
 *   <li>Re-enables {@code ReactiveOAuth2ResourceServerAutoConfiguration}
 *       (it's excluded by the default test {@code application.properties}).</li>
 *   <li>Provides its own {@link ReactiveJwtDecoder} bean built from an
 *       in-memory RSA key pair, so no network call is made.</li>
 *   <li>Uses {@link org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers#mockJwt}
 *       to inject a valid mock {@code Jwt} into requests that need to succeed.</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    // Override the test-default exclusion to bring the resource server back online
    "spring.autoconfigure.exclude=",
    // Stub issuer-uri unused (we override JwtDecoder via TestConfig)
    "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/none"
})
class SecurityIntegrationTest {

  @Autowired
  WebTestClient webTestClient;

  @Autowired
  ApplicationContext context;

  @MockBean
  BalanceStore balanceStore;

  @TestConfiguration
  static class TestConfig {

    /**
     * Provides a {@link ReactiveJwtDecoder} backed by an in-memory RSA key
     * pair so the resource server can validate tokens without any network
     * call to Keycloak. The mock-JWT helper produces tokens with the same
     * key, satisfying signature verification.
     */
    @Bean
    @Primary
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair kp = gen.generateKeyPair();
      RSAPublicKey pub = (RSAPublicKey) kp.getPublic();
      return NimbusReactiveJwtDecoder.withPublicKey(pub).build();
    }
  }

  // -----------------------------------------------------------------------
  // 401 for unauthenticated calls
  // -----------------------------------------------------------------------

  @Test
  void getBalance_withoutToken_returns401() {
    webTestClient.get().uri("/balance")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  void processPayment_withoutToken_returns401() {
    webTestClient.post().uri("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("""
                        {"orderId": 1, "amount": 1000}
                        """)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  // -----------------------------------------------------------------------
  // 200 with mock JWT
  // -----------------------------------------------------------------------

  // REPLACE the test method:
  @Test
  void getBalance_withMockJwt_returns200() {
    when(balanceStore.getBalance()).thenReturn(50_000L);

    WebTestClient.bindToApplicationContext(context)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build()
        .mutateWith(SecurityMockServerConfigurers.mockJwt())
        .get().uri("/balance")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.balance").isEqualTo(50_000);
  }

  // -----------------------------------------------------------------------
  // /actuator/health remains public for the Docker healthcheck
  // -----------------------------------------------------------------------

  @Test
  void actuatorHealth_isPublic_evenWithoutToken() {
    webTestClient.get().uri("/actuator/health")
        .exchange()
        .expectStatus().isOk();
  }
}
