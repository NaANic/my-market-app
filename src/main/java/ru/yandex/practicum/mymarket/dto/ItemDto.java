package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.OrderItem;

public record ItemDto(long id, String title, String description, String imgPath, long price, int count) {

  public static ItemDto from(Item item, int count) {
    return new ItemDto(item.getId(), item.getTitle(), item.getDescription(),
        item.getImgPath(), item.getPrice(), count);
  }

  public static ItemDto fromOrderItem(OrderItem oi) {
    return new ItemDto(oi.getItemId(), oi.getTitle(), null,
        null, oi.getPrice(), oi.getCount());
  }

  public static ItemDto empty() {
    return new ItemDto(-1, "", "", "", 0, 0);
  }
}
