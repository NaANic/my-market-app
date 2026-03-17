package ru.yandex.practicum.mymarket.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "order_items")
public class OrderItem {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "order_id", nullable = false)
  private CustomerOrder order;

  @Column(name = "item_id")
  private Long itemId;

  @Column(nullable = false)
  private String title;

  @Column(nullable = false)
  private long price;

  @Column(nullable = false)
  private int count;

  public OrderItem() {}

  public OrderItem(Long itemId, String title, long price, int count) {
    this.itemId = itemId;
    this.title = title;
    this.price = price;
    this.count = count;
  }

  public Long getId() { return id; }

  public CustomerOrder getOrder() { return order; }
  public void setOrder(CustomerOrder order) { this.order = order; }

  public Long getItemId() { return itemId; }
  public void setItemId(Long itemId) { this.itemId = itemId; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public long getPrice() { return price; }
  public void setPrice(long price) { this.price = price; }

  public int getCount() { return count; }
  public void setCount(int count) { this.count = count; }
}
