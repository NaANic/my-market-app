package ru.yandex.practicum.payment;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BalanceStoreTest {

    // -----------------------------------------------------------------------
    // getBalance
    // -----------------------------------------------------------------------

    @Test
    void getBalance_returnsInitialBalance() {
        BalanceStore store = new BalanceStore(50_000L);
        assertThat(store.getBalance()).isEqualTo(50_000L);
    }

    // -----------------------------------------------------------------------
    // deduct — happy path
    // -----------------------------------------------------------------------

    @Test
    void deduct_reducesBalance() {
        BalanceStore store = new BalanceStore(10_000L);
        long remaining = store.deduct(3_000L);
        assertThat(remaining).isEqualTo(7_000L);
        assertThat(store.getBalance()).isEqualTo(7_000L);
    }

    @Test
    void deduct_exactBalance_reducesToZero() {
        BalanceStore store = new BalanceStore(5_000L);
        long remaining = store.deduct(5_000L);
        assertThat(remaining).isEqualTo(0L);
        assertThat(store.getBalance()).isEqualTo(0L);
    }

    @Test
    void deduct_sequentialCalls_accumulateCorrectly() {
        BalanceStore store = new BalanceStore(10_000L);
        store.deduct(3_000L);
        store.deduct(2_000L);
        assertThat(store.getBalance()).isEqualTo(5_000L);
    }

    // -----------------------------------------------------------------------
    // deduct — insufficient funds
    // -----------------------------------------------------------------------

    @Test
    void deduct_insufficientFunds_throwsAndLeavesBalanceUnchanged() {
        BalanceStore store = new BalanceStore(1_000L);

        assertThatThrownBy(() -> store.deduct(2_000L))
            .isInstanceOf(InsufficientFundsException.class)
            .extracting(ex -> ((InsufficientFundsException) ex).getCurrentBalance())
            .isEqualTo(1_000L);

        // Balance must be unchanged after a failed deduct
        assertThat(store.getBalance()).isEqualTo(1_000L);
    }

    @Test
    void deduct_zeroBalance_throwsImmediately() {
        BalanceStore store = new BalanceStore(0L);

        assertThatThrownBy(() -> store.deduct(1L))
            .isInstanceOf(InsufficientFundsException.class);
    }

    // -----------------------------------------------------------------------
    // deduct — concurrency: only one thread should win when balance is scarce
    // -----------------------------------------------------------------------

    @Test
    void deduct_concurrent_exactlyOneThreadSucceeds_whenBalanceCoversOneRequest()
        throws InterruptedException {

        int threads = 10;
        long deductAmount = 1_000L;
        // Only enough for a single deduction
        BalanceStore store = new BalanceStore(deductAmount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();                   // all threads start simultaneously
                    store.deduct(deductAmount);
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();   // wait until all threads are parked at start
        start.countDown(); // release them all at once
        done.await();    // wait for all to finish
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failureCount.get()).isEqualTo(threads - 1);
        assertThat(store.getBalance()).isEqualTo(0L);
    }

    @Test
    void deduct_concurrent_finalBalanceIsConsistent() throws InterruptedException {
        int threads = 20;
        long initial = 100_000L;
        long perThread = 1_000L;
        // Enough for all threads to succeed
        BalanceStore store = new BalanceStore(initial);

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    store.deduct(perThread);
                } catch (InsufficientFundsException | InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(store.getBalance()).isEqualTo(initial - (long) threads * perThread);
    }
}
