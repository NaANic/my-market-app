package ru.yandex.practicum.mymarket.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("cart_items")
public class CartItem {

  @Id
  private Long id;

  @Column("session_id")
  private String sessionId;

  @Column("item_id")
  private Long itemId;

  private int count = 1;

  public CartItem() {}

  public CartItem(String sessionId, Long itemId, int count) {
    this.sessionId = sessionId;
    this.itemId = itemId;
    this.count = count;
  }

  public Long getId() { return id; }

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public Long getItemId() { return itemId; }
  public void setItemId(Long itemId) { this.itemId = itemId; }

  public int getCount() { return count; }
  public void setCount(int count) { this.count = count; }
}
