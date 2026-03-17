package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.entity.CustomerOrder;
import java.util.List;

public class OrderDto {

  private final long id;
  private final List<ItemDto> items;
  private final long totalSum;

  public OrderDto(long id, List<ItemDto> items, long totalSum) {
    this.id = id;
    this.items = items;
    this.totalSum = totalSum;
  }

  public static OrderDto from(CustomerOrder order) {
    List<ItemDto> items = order.getItems().stream()
        .map(ItemDto::fromOrderItem)
        .toList();
    return new OrderDto(order.getId(), items, order.getTotalSum());
  }

  public long getId() { return id; }
  public List<ItemDto> getItems() { return items; }
  public long getTotalSum() { return totalSum; }
}
