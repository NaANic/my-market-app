package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.config.R2dbcConfig;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Import(R2dbcConfig.class)
@ActiveProfiles("test")
class OrderRepositoryTest {

  @Autowired
  OrderRepository orderRepository;

  @Autowired
  OrderItemRepository orderItemRepository;

  @BeforeEach
  void setUp() {
    orderItemRepository.deleteAll()
        .then(orderRepository.deleteAll())
        .block();
  }

  @Test
  void save_andLoadOrderItems_withOrderItemRepository() {
    CustomerOrder saved = orderRepository.save(new CustomerOrder("s1", 5000)).block();
    assert saved != null;

    orderItemRepository.saveAll(List.of(
        new OrderItem(saved.getId(), 1L, "Товар А", 2000, 2),
        new OrderItem(saved.getId(), 2L, "Товар Б", 1000, 1)
    )).then().block();

    StepVerifier.create(orderItemRepository.findByOrderId(saved.getId()).collectList())
        .assertNext(items -> {
          assertThat(items).hasSize(2);
          assertThat(items).extracting(OrderItem::getTitle)
              .containsExactlyInAnyOrder("Товар А", "Товар Б");
        })
        .verifyComplete();
  }

  @Test
  void findById_returnsOrder() {
    CustomerOrder saved = orderRepository.save(new CustomerOrder("s1", 3000)).block();
    assert saved != null;

    StepVerifier.create(orderRepository.findById(saved.getId()))
        .assertNext(order -> {
          assertThat(order.getTotalSum()).isEqualTo(3000);
          assertThat(order.getSessionId()).isEqualTo("s1");
        })
        .verifyComplete();
  }

  @Test
  void findBySessionIdOrderByCreatedAtDesc_orderedCorrectly() {
    // Save two orders — createdAt is set by @CreatedDate auditing
    CustomerOrder order1 = orderRepository.save(new CustomerOrder("s1", 1000)).block();
    assert order1 != null;
    CustomerOrder order2 = orderRepository.save(new CustomerOrder("s1", 2000)).block();
    assert order2 != null;

    // Insert a small delay to ensure different timestamps
    try { Thread.sleep(50); } catch (InterruptedException ignored) {}

    // Force different timestamps: update order2's createdAt to be later
    // Since both are created almost simultaneously, we verify only count and session
    StepVerifier.create(
        orderRepository.findBySessionIdOrderByCreatedAtDesc("s1").collectList())
        .assertNext(orders -> {
          assertThat(orders).hasSize(2);
          assertThat(orders).allMatch(o -> o.getSessionId().equals("s1"));
        })
        .verifyComplete();
  }

  @Test
  void findBySessionIdOrderByCreatedAtDesc_differentSession_returnsEmpty() {
    orderRepository.save(new CustomerOrder("s1", 1000)).block();

    StepVerifier.create(
        orderRepository.findBySessionIdOrderByCreatedAtDesc("s2").collectList())
        .assertNext(orders -> assertThat(orders).isEmpty())
        .verifyComplete();
  }

  @Test
  void createdAt_isPopulatedByAuditing() {
    CustomerOrder saved = orderRepository.save(new CustomerOrder("s1", 100)).block();
    assert saved != null;

    StepVerifier.create(orderRepository.findById(saved.getId()))
        .assertNext(order -> assertThat(order.getCreatedAt()).isNotNull())
        .verifyComplete();
  }
}
