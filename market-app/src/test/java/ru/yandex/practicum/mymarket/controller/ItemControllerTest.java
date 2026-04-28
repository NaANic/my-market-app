package ru.yandex.practicum.mymarket.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;
import ru.yandex.practicum.mymarket.service.OrderService;
import ru.yandex.practicum.mymarket.service.PaymentClientService;
import org.springframework.context.annotation.Import;
import ru.yandex.practicum.mymarket.config.TestSecurityConfig;
import ru.yandex.practicum.mymarket.repository.UserRepository;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import ru.yandex.practicum.mymarket.service.CurrentUserService;
import reactor.core.publisher.Mono;

// No controller filter → loads all controllers → same context shared across all @WebFluxTest classes
@WebFluxTest
@Import(TestSecurityConfig.class)
class ItemControllerTest {

  @Autowired
  WebTestClient webTestClient;

  @MockitoBean
  ItemService itemService;

  @MockitoBean
  CartService cartService;

  @MockitoBean
  OrderService orderService;

  @MockitoBean
  UserRepository userRepository;

  @MockitoBean
  CurrentUserService currentUserService;

  /**
   * CartController now depends on PaymentClientService. All @WebFluxTest
   * classes share the same application context (no controller filter is set),
   * so every test class in this package must declare this bean or the shared
   * context cannot be wired and ALL controller tests fail.
   * ItemControllerTest tests don't call any cart page routes, so no stub is
   * needed — Mockito's default (returns null / empty for reactive types) is fine.
   */
  @MockitoBean
  PaymentClientService paymentClientService;

  @BeforeEach
  void stubCurrentUser() {
    when(currentUserService.getCurrentUserId()).thenReturn(Mono.just(1L));
  }

  @Test
  void getItems_returnsItemsPage() {
    when(itemService.findItems(any(), any(Pageable.class)))
        .thenReturn(Mono.just(new PageImpl<>(List.of(createItem(1L, "Test", 500)))));
    when(cartService.getCartItemCounts(any())).thenReturn(Mono.just(Map.of()));

    webTestClient.get().uri("/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Сортировка"));
  }

  @Test
  void getItems_defaultParams_passesCorrectPageable() {
    when(itemService.findItems(any(), any())).thenReturn(Mono.just(new PageImpl<>(List.of())));
    when(cartService.getCartItemCounts(any())).thenReturn(Mono.just(Map.of()));

    webTestClient.get().uri("/items").exchange().expectStatus().isOk();

    verify(itemService).findItems(isNull(), argThat(pageable ->
        pageable.getPageNumber() == 0 && pageable.getPageSize() == 5
    ));
  }

  @Test
  void getItems_withSearch_passesQueryToService() {
    when(itemService.findItems(any(), any())).thenReturn(Mono.just(new PageImpl<>(List.of())));
    when(cartService.getCartItemCounts(any())).thenReturn(Mono.just(Map.of()));

    webTestClient.get().uri("/items?search=мяч").exchange().expectStatus().isOk();

    verify(itemService).findItems(eq("мяч"), any());
  }

  @Test
  void getItems_gridPadsDummyItems_renderedInHtml() {
    when(itemService.findItems(any(), any())).thenReturn(Mono.just(new PageImpl<>(List.of(
        createItem(1L, "Альфа", 100),
        createItem(2L, "Бета", 200)
    ))));
    when(cartService.getCartItemCounts(any())).thenReturn(Mono.just(Map.of()));

    webTestClient.get().uri("/items")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> {
          assertThat(html).contains("Альфа");
          assertThat(html).contains("Бета");
          assertThat(html).contains("&nbsp;");
        });
  }

  @Test
  void getRootPath_sameAsGetItems() {
    when(itemService.findItems(any(), any())).thenReturn(Mono.just(new PageImpl<>(List.of())));
    when(cartService.getCartItemCounts(any())).thenReturn(Mono.just(Map.of()));

    webTestClient.get().uri("/")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Сортировка"));
  }

  @Test
  @WithMockUser
  void postItems_redirectsWithParams() {
    when(cartService.handleAction(any(), eq(1L), eq(CartAction.PLUS)))
        .thenReturn(Mono.empty());

    webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
        .post().uri("/items?id=1&action=PLUS&sort=ALPHA&pageNumber=2&pageSize=10")
        .exchange()
        .expectStatus().is3xxRedirection()
        .expectHeader().value("Location", loc ->
            assertThat(loc)
                .contains("/items?")
                .contains("sort=ALPHA")
                .contains("pageNumber=2")
                .contains("pageSize=10"));

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.PLUS));
  }

  @Test
  void getItem_rendersItemPage() {
    when(itemService.findById(1L)).thenReturn(Mono.just(createItem(1L, "Мяч", 2500)));
    when(cartService.getItemCount(any(), eq(1L))).thenReturn(Mono.just(2));

    webTestClient.get().uri("/items/1")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> {
          assertThat(html).contains("Мяч");
          assertThat(html).contains("2500");
        });
  }

  @Test
  @WithMockUser
  void postItem_modifiesCartAndRendersItemPage() {
    when(cartService.handleAction(any(), eq(1L), eq(CartAction.PLUS))).thenReturn(Mono.empty());
    when(itemService.findById(1L)).thenReturn(Mono.just(createItem(1L, "Мяч", 2500)));
    when(cartService.getItemCount(any(), eq(1L))).thenReturn(Mono.just(1));

    webTestClient.mutateWith(SecurityMockServerConfigurers.csrf())
        .post().uri("/items/1?action=PLUS")
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .value(html -> assertThat(html).contains("Мяч"));

    verify(cartService).handleAction(any(), eq(1L), eq(CartAction.PLUS));
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Description", "/img/test.jpg", price);
    item.setId(id);
    return item;
  }
}
