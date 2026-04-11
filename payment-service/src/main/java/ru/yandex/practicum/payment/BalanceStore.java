package ru.yandex.practicum.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe in-memory store for the account balance.
 *
 * <p>Uses an {@link AtomicLong} so that concurrent payment requests from
 * multiple event-loop threads cannot race on the balance: the CAS loop in
 * {@link #deduct} is entirely non-blocking and produces no contention under
 * WebFlux's event-loop threading model.
 *
 * <p>Balance is denominated in <em>kopecks</em> (integer, never fractional).
 * The initial value is supplied by the {@code payment.initial-balance}
 * property (see {@code application.yml}), which is itself overridable via the
 * {@code PAYMENT_INITIAL_BALANCE} environment variable in Docker.
 */
@Component
public class BalanceStore {

    private final AtomicLong balance;

    public BalanceStore(
        @Value("${payment.initial-balance:100000}") long initialBalance) {
        this.balance = new AtomicLong(initialBalance);
    }

    /** Returns the current balance in kopecks. */
    public long getBalance() {
        return balance.get();
    }

    /**
     * Atomically deducts {@code amount} kopecks from the balance.
     *
     * <p>Uses a compare-and-set loop so that two concurrent requests cannot
     * both see the same pre-deduction balance and both succeed when only one
     * should.
     *
     * @param amount kopecks to deduct; must be &gt; 0
     * @return remaining balance after a successful deduction
     * @throws InsufficientFundsException if the current balance is less than
     *                                    {@code amount}; balance is unchanged
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
            // Another thread won the CAS race — retry with the fresh value.
        }
    }
}
