package ru.yandex.practicum.mymarket.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
class OrderRepositoryTest {

  @Autowired
  OrderRepository orderRepository;

  @Autowired
  TestEntityManager em;

  @BeforeEach
  void setUp() {
    orderRepository.deleteAll();
  }

  @Test
  void save_cascadesToOrderItems() {
    CustomerOrder order = new CustomerOrder("s1", 5000);
    order.addItem(new OrderItem(1L, "Товар А", 2000, 2));
    order.addItem(new OrderItem(2L, "Товар Б", 1000, 1));

    CustomerOrder saved = orderRepository.save(order);
    em.flush();
    em.clear();

    CustomerOrder loaded = orderRepository.findById(saved.getId()).orElseThrow();
    assertThat(loaded.getItems()).hasSize(2);
    assertThat(loaded.getTotalSum()).isEqualTo(5000);
  }

  @Test
  void findBySessionId_orderedByCreatedAtDesc() throws InterruptedException {
    CustomerOrder order1 = new CustomerOrder("s1", 1000);
    order1.addItem(new OrderItem(1L, "A", 1000, 1));
    orderRepository.save(order1);
    em.flush();

    Thread.sleep(50);

    CustomerOrder order2 = new CustomerOrder("s1", 2000);
    order2.addItem(new OrderItem(2L, "B", 2000, 1));
    orderRepository.save(order2);
    em.flush();
    em.clear();

    List<CustomerOrder> orders = orderRepository
        .findBySessionIdOrderByCreatedAtDesc("s1");

    assertThat(orders).hasSize(2);
    assertThat(orders.get(0).getTotalSum()).isEqualTo(2000);
    assertThat(orders.get(1).getTotalSum()).isEqualTo(1000);
  }

  @Test
  void findBySessionId_loadsItemsEagerly() {
    CustomerOrder order = new CustomerOrder("s1", 3000);
    order.addItem(new OrderItem(1L, "Мяч", 1500, 2));
    orderRepository.save(order);
    em.flush();
    em.clear();

    List<CustomerOrder> orders = orderRepository
        .findBySessionIdOrderByCreatedAtDesc("s1");

    assertThat(orders.get(0).getItems()).hasSize(1);
    assertThat(orders.get(0).getItems().get(0).getTitle()).isEqualTo("Мяч");
  }

  @Test
  void findBySessionId_differentSession_returnsEmpty() {
    CustomerOrder order = new CustomerOrder("s1", 1000);
    order.addItem(new OrderItem(1L, "A", 1000, 1));
    orderRepository.save(order);

    List<CustomerOrder> result = orderRepository
        .findBySessionIdOrderByCreatedAtDesc("s2");

    assertThat(result).isEmpty();
  }

  @Test
  void findById_withEntityGraph_loadsItems() {
    CustomerOrder order = new CustomerOrder("s1", 5000);
    order.addItem(new OrderItem(1L, "X", 2500, 2));
    CustomerOrder saved = orderRepository.save(order);
    em.flush();
    em.clear();

    Optional<CustomerOrder> result = orderRepository.findById(saved.getId());

    assertThat(result).isPresent();
    assertThat(result.get().getItems()).hasSize(1);
  }
}
