package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import ru.yandex.practicum.mymarket.exception.PaymentFailedException;
import ru.yandex.practicum.mymarket.repository.OrderItemRepository;
import ru.yandex.practicum.mymarket.repository.OrderRepository;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  OrderRepository orderRepository;

  @Mock
  OrderItemRepository orderItemRepository;

  @Mock
  CartService cartService;

  @Mock
  PaymentClientService paymentClientService;

  @InjectMocks
  OrderService orderService;

  // ---------------------------------------------------------------------------
  // createOrder — success path
  // ---------------------------------------------------------------------------

  @Test
  void createOrder_success_savesOrderAndClearsCart() {
    ItemDto itemDto = new ItemDto(1L, "Товар", "Desc", "/img.jpg", 1500, 3);
    when(cartService.getCartItemDtos("s1")).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
      CustomerOrder order = inv.getArgument(0);
      ReflectionTestUtils.setField(order, "id", 42L);
      return Mono.just(order);
    });
    when(paymentClientService.pay(42L, 4500L)).thenReturn(Mono.just(95_500L));
    when(orderItemRepository.saveAll(any(Publisher.class))).thenReturn(Flux.empty());
    when(cartService.clearCart("s1")).thenReturn(Mono.empty());

    StepVerifier.create(orderService.createOrder("s1"))
        .assertNext(orderId -> assertThat(orderId).isEqualTo(42L))
        .verifyComplete();

    verify(orderRepository).save(any(CustomerOrder.class));
    verify(paymentClientService).pay(42L, 4500L);
    verify(cartService).clearCart("s1");
  }

  @Test
  void createOrder_calculatesTotalFromItemDtos() {
    // total = 1000*2 + 500*3 = 3500
    when(cartService.getCartItemDtos("s1")).thenReturn(Flux.just(
        new ItemDto(1L, "A", null, null, 1000, 2),
        new ItemDto(2L, "B", null, null, 500, 3)
    ));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
      CustomerOrder order = inv.getArgument(0);
      ReflectionTestUtils.setField(order, "id", 1L);
      return Mono.just(order);
    });
    when(paymentClientService.pay(1L, 3500L)).thenReturn(Mono.just(96_500L));
    when(orderItemRepository.saveAll(any(Publisher.class))).thenReturn(Flux.empty());
    when(cartService.clearCart("s1")).thenReturn(Mono.empty());

    StepVerifier.create(orderService.createOrder("s1"))
        .assertNext(id -> assertThat(id).isEqualTo(1L))
        .verifyComplete();

    verify(paymentClientService).pay(1L, 3500L);
  }

  // ---------------------------------------------------------------------------
  // createOrder — empty cart
  // ---------------------------------------------------------------------------

  @Test
  void createOrder_emptyCart_emitsError() {
    when(cartService.getCartItemDtos("s1")).thenReturn(Flux.empty());

    StepVerifier.create(orderService.createOrder("s1"))
        .expectErrorMatches(ex ->
            ex instanceof RuntimeException && ex.getMessage().contains("пуста"))
        .verify();

    verify(paymentClientService, never()).pay(any(Long.class), any(Long.class));
  }

  // ---------------------------------------------------------------------------
  // createOrder — payment failure (402)
  // ---------------------------------------------------------------------------

  @Test
  void createOrder_paymentRequired_emitsPaymentFailedException() {
    ItemDto itemDto = new ItemDto(1L, "Товар", "Desc", "/img.jpg", 2000, 1);
    when(cartService.getCartItemDtos("s1")).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
      CustomerOrder order = inv.getArgument(0);
      ReflectionTestUtils.setField(order, "id", 7L);
      return Mono.just(order);
    });

    // Simulate HTTP 402 with a JSON body matching payment-service response format
    byte[] body = "{\"message\":\"Insufficient funds\",\"balance\":500}"
        .getBytes(StandardCharsets.UTF_8);
    WebClientResponseException.PaymentRequired paymentRequired =
        (WebClientResponseException.PaymentRequired)
            WebClientResponseException.create(
                HttpStatus.PAYMENT_REQUIRED.value(),
                "Payment Required",
                HttpHeaders.EMPTY,
                body,
                StandardCharsets.UTF_8);

    when(paymentClientService.pay(7L, 2000L))
        .thenReturn(Mono.error(paymentRequired));

    StepVerifier.create(orderService.createOrder("s1"))
        .expectErrorMatches(ex ->
            ex instanceof PaymentFailedException pfe
                && pfe.getOrderId() == 7L
                && pfe.getCurrentBalance() == 500L)
        .verify();

    // Order items must NOT be saved and cart must NOT be cleared on failure
    verify(orderItemRepository, never()).saveAll(any(Publisher.class));
    verify(cartService, never()).clearCart(any());
  }

  @Test
  void createOrder_paymentRequired_malformedBody_balanceFallsBackToMinusOne() {
    ItemDto itemDto = new ItemDto(1L, "Товар", null, null, 1000, 1);
    when(cartService.getCartItemDtos("s1")).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv -> {
      CustomerOrder order = inv.getArgument(0);
      ReflectionTestUtils.setField(order, "id", 8L);
      return Mono.just(order);
    });

    // Malformed body — balance extraction should fall back to -1
    byte[] body = "not json".getBytes(StandardCharsets.UTF_8);
    WebClientResponseException.PaymentRequired paymentRequired =
        (WebClientResponseException.PaymentRequired)
            WebClientResponseException.create(
                HttpStatus.PAYMENT_REQUIRED.value(),
                "Payment Required",
                HttpHeaders.EMPTY,
                body,
                StandardCharsets.UTF_8);

    when(paymentClientService.pay(8L, 1000L))
        .thenReturn(Mono.error(paymentRequired));

    StepVerifier.create(orderService.createOrder("s1"))
        .expectErrorMatches(ex ->
            ex instanceof PaymentFailedException pfe
                && pfe.getCurrentBalance() == -1L)
        .verify();
  }

  // ---------------------------------------------------------------------------
  // getOrders / getOrder
  // ---------------------------------------------------------------------------

  @Test
  void getOrders_returnsOrderDtos() {
    CustomerOrder order = new CustomerOrder("s1", 3000);
    ReflectionTestUtils.setField(order, "id", 1L);
    OrderItem orderItem = new OrderItem(1L, 10L, "Товар А", 1500, 2);

    when(orderRepository.findBySessionIdOrderByCreatedAtDesc("s1"))
        .thenReturn(Flux.just(order));
    when(orderItemRepository.findByOrderId(1L))
        .thenReturn(Flux.just(orderItem));

    StepVerifier.create(orderService.getOrders("s1"))
        .assertNext(dto -> {
          assertThat(dto.id()).isEqualTo(1L);
          assertThat(dto.totalSum()).isEqualTo(3000);
          assertThat(dto.items()).hasSize(1);
          assertThat(dto.items().get(0).title()).isEqualTo("Товар А");
        })
        .verifyComplete();
  }

  @Test
  void getOrder_existing_returnsOrderDto() {
    CustomerOrder order = new CustomerOrder("s1", 5000);
    ReflectionTestUtils.setField(order, "id", 7L);
    OrderItem orderItem = new OrderItem(7L, 20L, "Ракетка", 5000, 1);

    when(orderRepository.findById(7L)).thenReturn(Mono.just(order));
    when(orderItemRepository.findByOrderId(7L)).thenReturn(Flux.just(orderItem));

    StepVerifier.create(orderService.getOrder(7L))
        .assertNext(dto -> {
          assertThat(dto.id()).isEqualTo(7L);
          assertThat(dto.totalSum()).isEqualTo(5000);
          assertThat(dto.items()).hasSize(1);
        })
        .verifyComplete();
  }

  @Test
  void getOrder_notFound_emitsError() {
    when(orderRepository.findById(999L)).thenReturn(Mono.empty());

    StepVerifier.create(orderService.getOrder(999L))
        .expectErrorMatches(ex ->
            ex instanceof RuntimeException && ex.getMessage().contains("999"))
        .verify();
  }
}
