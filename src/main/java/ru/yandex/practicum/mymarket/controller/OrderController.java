package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.mymarket.dto.OrderDto;
import ru.yandex.practicum.mymarket.service.OrderService;

import java.util.List;

@Controller
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @GetMapping("/orders")
  public String orders(HttpSession session, Model model) {
    List<OrderDto> orders = orderService.getOrders(session.getId()).stream()
        .map(OrderDto::from)
        .toList();
    model.addAttribute("orders", orders);
    return "orders";
  }

  @GetMapping("/orders/{id}")
  public String order(
      @PathVariable long id,
      @RequestParam(defaultValue = "false") boolean newOrder,
      Model model
  ) {
    OrderDto order = OrderDto.from(orderService.getOrder(id));
    model.addAttribute("order", order);
    model.addAttribute("newOrder", newOrder);
    return "order";
  }

  @PostMapping("/buy")
  public String buy(HttpSession session) {
    Long orderId = orderService.createOrder(session.getId());
    return "redirect:/orders/" + orderId + "?newOrder=true";
  }
}
