package ru.yandex.practicum.mymarket.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.CurrentUserService;
import ru.yandex.practicum.mymarket.service.OrderService;

@PreAuthorize("isAuthenticated()")
@Controller
public class OrderController {

  private final OrderService       orderService;
  private final CurrentUserService currentUserService;

  public OrderController(OrderService orderService,
      CurrentUserService currentUserService) {
    this.orderService       = orderService;
    this.currentUserService = currentUserService;
  }

  @GetMapping("/orders")
  public Mono<String> orders(Model model) {
    return currentUserService.getCurrentUserId()
        .flatMapMany(orderService::getOrders)
        .collectList()
        .doOnNext(orders -> model.addAttribute("orders", orders))
        .thenReturn("orders");
  }

  @GetMapping("/orders/{id}")
  public Mono<String> order(
      @PathVariable long id,
      @RequestParam(defaultValue = "false") boolean newOrder,
      Model model
  ) {
    return orderService.getOrder(id)
        .doOnNext(order -> {
          model.addAttribute("order", order);
          model.addAttribute("newOrder", newOrder);
        })
        .thenReturn("order");
  }

  @PostMapping("/buy")
  public Mono<String> buy() {
    return currentUserService.getCurrentUserId()
        .flatMap(orderService::createOrder)
        .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true");
  }
}
