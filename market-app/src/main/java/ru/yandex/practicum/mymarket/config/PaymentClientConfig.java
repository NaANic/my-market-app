package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.payment.client.ApiClient;
import ru.yandex.practicum.payment.client.api.BalanceApi;
import ru.yandex.practicum.payment.client.api.PaymentApi;

/**
 * Configures the generated OpenAPI WebClient stubs so they point at the
 * payment-service instance defined by {@code payment.service.url}.
 *
 * <p>All three beans are prototype-safe: {@link ApiClient} is stateless after
 * construction, and both API classes delegate every call through it without
 * holding per-request state.
 */
@Configuration
public class PaymentClientConfig {

  /**
   * Base {@link ApiClient} shared by all generated API beans.
   * The webclient generator's no-arg constructor creates a default
   * {@link org.springframework.web.reactive.function.client.WebClient};
   * we only need to override the base path.
   */
  @Bean
  public ApiClient paymentApiClient(
      @Value("${payment.service.url:http://localhost:8081}") String baseUrl
  ) {
    ApiClient client = new ApiClient();
    client.setBasePath(baseUrl);
    return client;
  }

  /**
   * Generated stub for the {@code /payment} endpoint.
   */
  @Bean
  public PaymentApi paymentApi(ApiClient paymentApiClient) {
    return new PaymentApi(paymentApiClient);
  }

  /**
   * Generated stub for the {@code /balance} endpoint.
   */
  @Bean
  public BalanceApi balanceApi(ApiClient paymentApiClient) {
    return new BalanceApi(paymentApiClient);
  }
}
