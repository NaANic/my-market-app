package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock OrderRepository      orderRepository;
  @Mock OrderItemRepository  orderItemRepository;
  @Mock CartService          cartService;
  @Mock PaymentClientService paymentClientService;

  @InjectMocks
  OrderService orderService;

  private static final Long USER_ID = 1L;

  @BeforeEach
  void stubSaveAll() {
    lenient().when(orderItemRepository.saveAll(anyList()))
        .thenReturn(Flux.empty());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private static WebClientResponseException make402(byte[] body) {
    return WebClientResponseException.create(
        HttpStatus.PAYMENT_REQUIRED.value(),
        "Payment Required",
        HttpHeaders.EMPTY,
        body,
        StandardCharsets.UTF_8);
  }

  private static CustomerOrder orderWithId(CustomerOrder order, long id) {
    ReflectionTestUtils.setField(order, "id", id);
    return order;
  }

  // -----------------------------------------------------------------------
  // createOrder — success
  // -----------------------------------------------------------------------

  @Test
  void createOrder_success_savesOrderAndClearsCart() {
    ItemDto itemDto = new ItemDto(1L, "Товар", "Desc", "/img.jpg", 1500, 3);
    when(cartService.getCartItemDtos(USER_ID)).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv ->
        Mono.just(orderWithId(inv.getArgument(0), 42L)));
    when(paymentClientService.pay(42L, 4500L)).thenReturn(Mono.just(95_500L));
    when(orderItemRepository.saveAll(anyList())).thenReturn(Flux.empty());
    when(cartService.clearCart(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(orderService.createOrder(USER_ID))
        .assertNext(orderId -> assertThat(orderId).isEqualTo(42L))
        .verifyComplete();

    verify(orderRepository).save(any(CustomerOrder.class));
    verify(paymentClientService).pay(42L, 4500L);
    verify(orderItemRepository).saveAll(anyList());
    verify(cartService).clearCart(USER_ID);
  }

  @Test
  void createOrder_calculatesTotalFromItemDtos() {
    when(cartService.getCartItemDtos(USER_ID)).thenReturn(Flux.just(
        new ItemDto(1L, "A", null, null, 1000, 2),
        new ItemDto(2L, "B", null, null, 500, 3)
    ));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv ->
        Mono.just(orderWithId(inv.getArgument(0), 1L)));
    when(paymentClientService.pay(1L, 3500L)).thenReturn(Mono.just(96_500L));
    when(cartService.clearCart(USER_ID)).thenReturn(Mono.empty());

    StepVerifier.create(orderService.createOrder(USER_ID))
        .assertNext(id -> assertThat(id).isEqualTo(1L))
        .verifyComplete();

    verify(paymentClientService).pay(1L, 3500L);
  }

  // -----------------------------------------------------------------------
  // createOrder — empty cart
  // -----------------------------------------------------------------------

  @Test
  void createOrder_emptyCart_emitsError() {
    when(cartService.getCartItemDtos(USER_ID)).thenReturn(Flux.empty());

    StepVerifier.create(orderService.createOrder(USER_ID))
        .expectErrorMatches(ex ->
            ex instanceof RuntimeException && ex.getMessage().contains("пуста"))
        .verify();

    verify(paymentClientService, never()).pay(any(Long.class), any(Long.class));
  }

  // -----------------------------------------------------------------------
  // createOrder — 402 payment required
  // -----------------------------------------------------------------------

  @Test
  void createOrder_paymentRequired_emitsPaymentFailedException() {
    ItemDto itemDto = new ItemDto(1L, "Товар", "Desc", "/img.jpg", 2000, 1);
    when(cartService.getCartItemDtos(USER_ID)).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv ->
        Mono.just(orderWithId(inv.getArgument(0), 7L)));

    byte[] body = "{\"message\":\"Insufficient funds\",\"balance\":500}"
        .getBytes(StandardCharsets.UTF_8);
    when(paymentClientService.pay(7L, 2000L))
        .thenReturn(Mono.error(make402(body)));

    StepVerifier.create(orderService.createOrder(USER_ID))
        .expectErrorMatches(ex ->
            ex instanceof PaymentFailedException pfe
                && pfe.getOrderId() == 7L
                && pfe.getCurrentBalance() == 500L)
        .verify();

    verify(orderItemRepository, never()).saveAll(anyList());
    verify(cartService, never()).clearCart(any());
  }

  @Test
  void createOrder_paymentRequired_malformedBody_balanceFallsBackToMinusOne() {
    ItemDto itemDto = new ItemDto(1L, "Товар", null, null, 1000, 1);
    when(cartService.getCartItemDtos(USER_ID)).thenReturn(Flux.just(itemDto));
    when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(inv ->
        Mono.just(orderWithId(inv.getArgument(0), 8L)));

    byte[] malformed = "not json".getBytes(StandardCharsets.UTF_8);
    when(paymentClientService.pay(8L, 1000L))
        .thenReturn(Mono.error(make402(malformed)));

    StepVerifier.create(orderService.createOrder(USER_ID))
        .expectErrorMatches(ex ->
            ex instanceof PaymentFailedException pfe
                && pfe.getCurrentBalance() == -1L)
        .verify();

    verify(orderItemRepository, never()).saveAll(anyList());
    verify(cartService, never()).clearCart(any());
  }

  // -----------------------------------------------------------------------
  // getOrders / getOrder
  // -----------------------------------------------------------------------

  @Test
  void getOrders_returnsOrderDtos() {
    CustomerOrder order = orderWithId(new CustomerOrder(USER_ID, 3000), 1L);
    OrderItem orderItem = new OrderItem(1L, 10L, "Товар А", 1500, 2);

    when(orderRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
        .thenReturn(Flux.just(order));
    when(orderItemRepository.findByOrderId(1L))
        .thenReturn(Flux.just(orderItem));

    StepVerifier.create(orderService.getOrders(USER_ID))
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
    CustomerOrder order = orderWithId(new CustomerOrder(USER_ID, 5000), 7L);
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
