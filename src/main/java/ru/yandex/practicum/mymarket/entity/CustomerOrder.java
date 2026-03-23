package ru.yandex.practicum.mymarket.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customer_orders")
public class CustomerOrder {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "session_id", nullable = false)
  private String sessionId;

  @Column(name = "total_sum", nullable = false)
  private long totalSum;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<OrderItem> items = new ArrayList<>();

  @PrePersist
  void onCreate() {
    this.createdAt = LocalDateTime.now();
  }

  public CustomerOrder() {}

  public CustomerOrder(String sessionId, long totalSum) {
    this.sessionId = sessionId;
    this.totalSum = totalSum;
  }

  public void addItem(OrderItem orderItem) {
    items.add(orderItem);
    orderItem.setOrder(this);
  }

  public Long getId() { return id; }

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public long getTotalSum() { return totalSum; }
  public void setTotalSum(long totalSum) { this.totalSum = totalSum; }

  public LocalDateTime getCreatedAt() { return createdAt; }

  public List<OrderItem> getItems() { return items; }
  public void setItems(List<OrderItem> items) { this.items = items; }
}
