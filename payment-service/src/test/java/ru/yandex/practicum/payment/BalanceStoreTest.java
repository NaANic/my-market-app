package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceStoreTest {

    @Test
    void initialBalance_isConfiguredValue() {
        BalanceStore store = new BalanceStore(50_000L);
        assertThat(store.getBalance()).isEqualTo(50_000L);
    }

    @Test
    void deduct_reducesBalance() {
        BalanceStore store = new BalanceStore(100_000L);
        long remaining = store.deduct(30_000L);
        assertThat(remaining).isEqualTo(70_000L);
        assertThat(store.getBalance()).isEqualTo(70_000L);
    }

    @Test
    void deduct_exactAmount_leavesZero() {
        BalanceStore store = new BalanceStore(5_000L);
        long remaining = store.deduct(5_000L);
        assertThat(remaining).isZero();
    }

    @Test
    void deduct_insufficientFunds_throws() {
        BalanceStore store = new BalanceStore(1_000L);
        assertThatThrownBy(() -> store.deduct(2_000L))
                .isInstanceOf(InsufficientFundsException.class)
                .satisfies(ex -> assertThat(((InsufficientFundsException) ex).getCurrentBalance())
                        .isEqualTo(1_000L));
    }
}
