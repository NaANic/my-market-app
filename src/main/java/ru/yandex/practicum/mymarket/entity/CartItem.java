package ru.yandex.practicum.mymarket.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cart_items",
    uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "item_id"}))
public class CartItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @ManyToOne(optional = false)
  @JoinColumn(name = "item_id", nullable = false)
  private Item item;

  @Column(nullable = false)
  private int count = 1;

  public CartItem() {}

  public CartItem(String sessionId, Item item, int count) {
    this.sessionId = sessionId;
    this.item = item;
    this.count = count;
  }

  public Long getId() { return id; }

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public Item getItem() { return item; }
  public void setItem(Item item) { this.item = item; }

  public int getCount() { return count; }
  public void setCount(int count) { this.count = count; }
}
