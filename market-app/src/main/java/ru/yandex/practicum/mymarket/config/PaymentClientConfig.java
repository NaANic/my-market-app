package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import ru.yandex.practicum.payment.client.ApiClient;
import ru.yandex.practicum.payment.client.api.PaymentApi;
import org.springframework.context.annotation.Profile;

/**
 * Configures the generated OpenAPI WebClient stub so it points at the
 * payment-service instance defined by {@code payment.service.url} and
 * automatically attaches a Keycloak-issued bearer token to every outbound call.
 *
 * <p><b>How the token gets attached:</b>
 * <ol>
 *   <li>{@link ReactiveOAuth2AuthorizedClientManager} drives the
 *       {@code client_credentials} grant against Keycloak using the
 *       {@code keycloak} registration declared in {@code application.yml}.</li>
 *   <li>{@link ServerOAuth2AuthorizedClientExchangeFilterFunction} is added to
 *       the WebClient as a filter; before each request it asks the manager for
 *       a (cached) access token and writes it to the {@code Authorization}
 *       header.</li>
 *   <li>The token is cached in {@link ReactiveOAuth2AuthorizedClientService}
 *       and refreshed automatically when it expires.</li>
 * </ol>
 *
 * <p>The {@code keycloak} registration ID is hard-coded into
 * {@link ServerOAuth2AuthorizedClientExchangeFilterFunction#setDefaultClientRegistrationId},
 * so callers do not need to specify it on every request.
 */
@Profile("!test")
@Configuration
public class PaymentClientConfig {

  /**
   * The {@link AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager}
   * variant is used because the {@code client_credentials} grant has no
   * {@code Authentication} (it's machine-to-machine), so the request-scoped
   * {@code DefaultReactiveOAuth2AuthorizedClientManager} is not appropriate.
   */
  @Bean
  public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ReactiveOAuth2AuthorizedClientService authorizedClientService) {

    var authorizedClientProvider = ReactiveOAuth2AuthorizedClientProviderBuilder.builder()
        .clientCredentials()
        .build();

    var manager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
        clientRegistrationRepository, authorizedClientService);
    manager.setAuthorizedClientProvider(authorizedClientProvider);
    return manager;
  }

  /**
   * Base {@link ApiClient} for the generated {@link PaymentApi}, with a
   * pre-configured WebClient that attaches a Keycloak bearer token to every
   * outbound request.
   */
  @Bean
  public ApiClient paymentApiClient(
      ReactiveOAuth2AuthorizedClientManager authorizedClientManager,
      @Value("${payment.service.url:http://localhost:8081}") String baseUrl) {

    var oauth2Filter = new ServerOAuth2AuthorizedClientExchangeFilterFunction(authorizedClientManager);
    oauth2Filter.setDefaultClientRegistrationId("keycloak");

    WebClient webClient = WebClient.builder()
        .baseUrl(baseUrl)
        .filter(oauth2Filter)
        .build();

    ApiClient client = new ApiClient(webClient);
    client.setBasePath(baseUrl);
    return client;
  }

  @Bean
  public PaymentApi paymentApi(ApiClient paymentApiClient) {
    return new PaymentApi(paymentApiClient);
  }
}
