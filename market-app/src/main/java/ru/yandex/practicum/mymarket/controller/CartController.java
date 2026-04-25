package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.PaymentClientService;

@PreAuthorize("isAuthenticated()")
@Controller
@RequestMapping("/cart")
public class CartController {

  private final CartService          cartService;
  private final PaymentClientService paymentClientService;

  public CartController(CartService cartService,
      PaymentClientService paymentClientService) {
    this.cartService          = cartService;
    this.paymentClientService = paymentClientService;
  }

  /**
   * Renders the cart page.
   *
   * @param error optional payment-failure message forwarded from
   *              {@link ru.yandex.practicum.mymarket.web.GlobalExceptionHandler}
   *              via a {@code ?error=} redirect query parameter
   */
  @GetMapping("/items")
  public Mono<String> cart(
      @RequestParam(required = false) String error,
      WebSession session,
      Model model
  ) {
    if (error != null && !error.isBlank()) {
      model.addAttribute("error", error);
    }
    return populateCartModel(session.getId(), model).thenReturn("cart");
  }

  @PostMapping("/items")
  public Mono<String> modifyCart(
      @RequestParam long id,
      @RequestParam CartAction action,
      WebSession session,
      Model model
  ) {
    return cartService.handleAction(session.getId(), id, action)
        .then(populateCartModel(session.getId(), model))
        .thenReturn("cart");
  }

  /**
   * Populates the cart model with items, total, and a {@code canAfford} flag.
   *
   * <p>Three reactive sources are zipped in parallel:
   * <ol>
   *   <li>Cart item DTOs (list)</li>
   *   <li>Cart total in kopecks</li>
   *   <li>Current balance from payment-service</li>
   * </ol>
   *
   * <p>{@code canAfford} is {@code true} when:
   * <ul>
   *   <li>The cart is non-empty, AND</li>
   *   <li>The balance retrieved from the payment-service is ≥ the cart total.</li>
   * </ul>
   *
   * <p>{@code onErrorReturn(0L)} on the balance call treats any payment-service
   * failure (network error, 5xx, timeout) as a zero balance, which disables
   * the "Купить" button rather than showing an unhandled error to the user.
   */
  private Mono<Void> populateCartModel(String sessionId, Model model) {
    Mono<java.util.List<ru.yandex.practicum.mymarket.dto.ItemDto>> itemsMono =
        cartService.getCartItemDtos(sessionId).collectList();

    Mono<Long> totalMono  = cartService.getCartTotal(sessionId);

    Mono<Long> balanceMono = paymentClientService.getBalance()
        .onErrorReturn(0L);   // payment service unavailable → treat as insufficient funds

    return Mono.zip(itemsMono, totalMono, balanceMono)
        .doOnNext(tuple -> {
          var items   = tuple.getT1();
          long total   = tuple.getT2();
          long balance = tuple.getT3();

          boolean canAfford = !items.isEmpty() && balance >= total;

          model.addAttribute("items",     items);
          model.addAttribute("total",     total);
          model.addAttribute("canAfford", canAfford);
        })
        .then();
  }
}
