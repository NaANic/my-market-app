package ru.yandex.practicum.mymarket.dto;

import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.entity.OrderItem;

public class ItemDto {

  private final long id;
  private final String title;
  private final String description;
  private final String imgPath;
  private final long price;
  private final int count;

  public ItemDto(long id, String title, String description,
      String imgPath, long price, int count) {
    this.id = id;
    this.title = title;
    this.description = description;
    this.imgPath = imgPath;
    this.price = price;
    this.count = count;
  }

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

  public long getId() { return id; }
  public String getTitle() { return title; }
  public String getDescription() { return description; }
  public String getImgPath() { return imgPath; }
  public long getPrice() { return price; }
  public int getCount() { return count; }
}
