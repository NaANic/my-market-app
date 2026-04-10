package ru.yandex.practicum.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory, thread-safe store for the single account balance (in kopecks).
 */
@Component
public class BalanceStore {

    private final AtomicLong balance;

    public BalanceStore(@Value("${payment.initial-balance:100000}") long initialBalance) {
        this.balance = new AtomicLong(initialBalance);
    }

    public long getBalance() {
        return balance.get();
    }

    /**
     * Atomically deduct {@code amount} kopecks if the balance is sufficient.
     *
     * @return remaining balance after deduction
     * @throws InsufficientFundsException if current balance < amount
     */
    public long deduct(long amount) {
        while (true) {
            long current = balance.get();
            if (current < amount) {
                throw new InsufficientFundsException(current);
            }
            long updated = current - amount;
            if (balance.compareAndSet(current, updated)) {
                return updated;
            }
            // Another thread modified balance concurrently — retry
        }
    }
}
