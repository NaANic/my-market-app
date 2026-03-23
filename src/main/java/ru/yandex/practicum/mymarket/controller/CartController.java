package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.mymarket.dto.CartAction;
import ru.yandex.practicum.mymarket.dto.ItemDto;
import ru.yandex.practicum.mymarket.service.CartService;

import java.util.List;

@Controller
@RequestMapping("/cart")
public class CartController {

  private final CartService cartService;

  public CartController(CartService cartService) {
    this.cartService = cartService;
  }

  @GetMapping("/items")
  public String cart(HttpSession session, Model model) {
    populateCartModel(session.getId(), model);
    return "cart";
  }

  @PostMapping("/items")
  public String modifyCart(
      @RequestParam long id,
      @RequestParam CartAction action,
      HttpSession session,
      Model model
  ) {
    cartService.handleAction(session.getId(), id, action);
    populateCartModel(session.getId(), model);
    return "cart";
  }

  private void populateCartModel(String sessionId, Model model) {
    List<ItemDto> items = cartService.getCartItems(sessionId).stream()
        .map(ci -> ItemDto.from(ci.getItem(), ci.getCount()))
        .toList();
    long total = cartService.getCartTotal(sessionId);

    model.addAttribute("items", items);
    model.addAttribute("total", total);
  }
}
