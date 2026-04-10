package ru.yandex.practicum.payment;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.api.BalanceApi;
import ru.yandex.practicum.payment.api.PaymentApi;
import ru.yandex.practicum.payment.model.BalanceResponse;
import ru.yandex.practicum.payment.model.PaymentRequest;
import ru.yandex.practicum.payment.model.PaymentResponse;

@RestController
public class PaymentController implements BalanceApi, PaymentApi {

    private final BalanceStore balanceStore;

    public PaymentController(BalanceStore balanceStore) {
        this.balanceStore = balanceStore;
    }

    @Override
    public Mono<BalanceResponse> getBalance(ServerWebExchange exchange) {
        return Mono.fromCallable(() ->
                new BalanceResponse().balance(balanceStore.getBalance()));
    }

    @Override
    public Mono<PaymentResponse> processPayment(Mono<PaymentRequest> paymentRequest,
                                                ServerWebExchange exchange) {
        return paymentRequest.map(req -> {
            long remaining = balanceStore.deduct(req.getAmount());
            return new PaymentResponse()
                    .success(true)
                    .remainingBalance(remaining);
        });
    }
}
