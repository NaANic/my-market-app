package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.service.CartService;

@Controller
@RequestMapping("/cart")
public class CartController {

  private final CartService cartService;

  public CartController(CartService cartService) {
    this.cartService = cartService;
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

  private Mono<Void> populateCartModel(String sessionId, Model model) {
    return Mono.zip(
        cartService.getCartItemDtos(sessionId).collectList(),
        cartService.getCartTotal(sessionId)
    ).doOnNext(tuple -> {
      model.addAttribute("items", tuple.getT1());
      model.addAttribute("total", tuple.getT2());
    }).then();
  }
}
