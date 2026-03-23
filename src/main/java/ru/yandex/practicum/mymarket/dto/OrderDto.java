package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import java.util.List;

public record OrderDto(long id, List<ItemDto> items, long totalSum) {

  public static OrderDto from(CustomerOrder order) {
    List<ItemDto> items = order.getItems().stream()
        .map(ItemDto::fromOrderItem)
        .toList();
    return new OrderDto(order.getId(), items, order.getTotalSum());
  }
}
