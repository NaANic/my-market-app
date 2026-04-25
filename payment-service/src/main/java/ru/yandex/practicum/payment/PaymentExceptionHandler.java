package ru.yandex.practicum.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.payment.model.PaymentError;

@RestControllerAdvice
public class PaymentExceptionHandler {

    @ExceptionHandler(InsufficientFundsException.class)
    @ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
    public Mono<PaymentError> handleInsufficientFunds(InsufficientFundsException ex) {
        return Mono.just(new PaymentError()
                .message("Insufficient funds")
                .balance(ex.getCurrentBalance()));
    }
}
