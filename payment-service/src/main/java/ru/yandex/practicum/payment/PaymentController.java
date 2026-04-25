package ru.yandex.practicum.payment;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.api.BalanceApi;
import ru.yandex.practicum.payment.api.PaymentApi;
import ru.yandex.practicum.payment.model.BalanceResponse;
import ru.yandex.practicum.payment.model.PaymentRequest;
import ru.yandex.practicum.payment.model.PaymentResponse;

/**
 * Implements both generated OpenAPI server interfaces in a single controller.
 *
 * <p>The payment flow:
 * <ol>
 *   <li>{@code GET /balance} — returns current balance via {@link BalanceStore#getBalance()}.</li>
 *   <li>{@code POST /payment} — calls {@link BalanceStore#deduct(long)}, which throws
 *       {@link InsufficientFundsException} if funds are insufficient. That exception is
 *       caught by {@link PaymentExceptionHandler} and serialised as HTTP 402 with a
 *       {@code PaymentError} body whose {@code balance} field is parsed by
 *       {@code OrderService.extractBalance()} in market-app.</li>
 * </ol>
 *
 * <p>{@code Mono.fromCallable} is used so that any exception thrown inside
 * {@link BalanceStore#deduct} is captured as a reactive error signal rather
 * than a raw exception escaping the subscriber chain.
 */
@RestController
public class PaymentController implements BalanceApi, PaymentApi {

    private final BalanceStore balanceStore;

    public PaymentController(BalanceStore balanceStore) {
        this.balanceStore = balanceStore;
    }

    /** {@inheritDoc} */
    @Override
    public Mono<BalanceResponse> getBalance(ServerWebExchange exchange) {
        return Mono.fromCallable(() ->
            new BalanceResponse().balance(balanceStore.getBalance()));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wraps {@link BalanceStore#deduct} in {@code Mono.fromCallable} so that
     * {@link InsufficientFundsException} surfaces as a reactive error and is
     * handled by {@link PaymentExceptionHandler} → HTTP 402.
     */
    @Override
    public Mono<PaymentResponse> processPayment(Mono<PaymentRequest> paymentRequest,
        ServerWebExchange exchange) {
        return paymentRequest.flatMap(req ->
            Mono.fromCallable(() -> {
                long remaining = balanceStore.deduct(req.getAmount());
                return new PaymentResponse()
                    .success(true)
                    .remainingBalance(remaining);
            })
        );
    }
}
