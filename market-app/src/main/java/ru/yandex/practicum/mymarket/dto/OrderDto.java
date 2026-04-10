package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import ru.yandex.practicum.mymarket.entity.OrderItem;
import java.util.List;

public record OrderDto(long id, List<ItemDto> items, long totalSum) {

  public static OrderDto of(CustomerOrder order, List<OrderItem> items) {
    List<ItemDto> itemDtos = items.stream()
        .map(ItemDto::fromOrderItem)
        .toList();
    return new OrderDto(order.getId(), itemDtos, order.getTotalSum());
  }
}
