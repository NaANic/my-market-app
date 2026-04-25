package ru.yandex.practicum.mymarket.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.service.OrderService;

@Controller
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping("/orders")
  public Mono<String> orders(WebSession session, Model model) {
    return orderService.getOrders(session.getId())
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
  public Mono<String> buy(WebSession session) {
    return orderService.createOrder(session.getId())
        .map(orderId -> "redirect:/orders/" + orderId + "?newOrder=true");
  }
}
