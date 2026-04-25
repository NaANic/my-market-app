package ru.yandex.practicum.mymarket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.yandex.practicum.payment.client.ApiClient;
import ru.yandex.practicum.payment.client.api.PaymentApi;

/**
 * Configures the generated OpenAPI WebClient stub so it points at the
 * payment-service instance defined by {@code payment.service.url}.
 *
 * <p>Both endpoints ({@code GET /balance} and {@code POST /payment}) are
 * generated into a single {@link PaymentApi} class because they share the
 * {@code payment} tag in {@code payment-api.yaml}. There is no separate
 * {@code BalanceApi} class produced by the {@code java/webclient} generator.
 */
@Configuration
public class PaymentClientConfig {

  /**
   * Base {@link ApiClient} used by the generated {@link PaymentApi}.
   * The webclient generator's no-arg constructor creates a default
   * {@link org.springframework.web.reactive.function.client.WebClient};
   * we only need to override the base path.
   */
  @Bean
  public ApiClient paymentApiClient(
      @Value("${payment.service.url:http://localhost:8081}") String baseUrl) {
    ApiClient client = new ApiClient();
    client.setBasePath(baseUrl);
    return client;
  }

  /**
   * Generated stub covering both {@code GET /balance} and
   * {@code POST /payment} endpoints.
   */
  @Bean
  public PaymentApi paymentApi(ApiClient paymentApiClient) {
    return new PaymentApi(paymentApiClient);
  }
}
