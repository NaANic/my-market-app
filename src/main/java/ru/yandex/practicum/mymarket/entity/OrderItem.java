package ru.yandex.practicum.mymarket.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("order_items")
public class OrderItem {

  @Id
  private Long id;

  @Column("order_id")
  private Long orderId;

  @Column("item_id")
  private Long itemId;

  private String title;

  private long price;

  private int count;

  public OrderItem() {}

  public OrderItem(Long orderId, Long itemId, String title, long price, int count) {
    this.orderId = orderId;
    this.itemId = itemId;
    this.title = title;
    this.price = price;
    this.count = count;
  }

  public Long getId() { return id; }

  public Long getOrderId() { return orderId; }
  public void setOrderId(Long orderId) { this.orderId = orderId; }

  public Long getItemId() { return itemId; }
  public void setItemId(Long itemId) { this.itemId = itemId; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public long getPrice() { return price; }
  public void setPrice(long price) { this.price = price; }

  public int getCount() { return count; }
  public void setCount(int count) { this.count = count; }
}
