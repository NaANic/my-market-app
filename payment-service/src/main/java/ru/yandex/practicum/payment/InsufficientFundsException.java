package ru.yandex.practicum.payment;

public class InsufficientFundsException extends RuntimeException {

    private final long currentBalance;

    public InsufficientFundsException(long currentBalance) {
        super("Insufficient funds");
        this.currentBalance = currentBalance;
    }

    public long getCurrentBalance() {
        return currentBalance;
    }
}
