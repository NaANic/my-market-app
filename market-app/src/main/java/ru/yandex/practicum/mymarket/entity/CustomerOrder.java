package ru.yandex.practicum.mymarket.entity;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("customer_orders")
public class CustomerOrder {

  @Id
  private Long id;

  @Column("user_id")
  private Long userId;

  @Column("total_sum")
  private long totalSum;

  @CreatedDate
  @Column("created_at")
  private LocalDateTime createdAt;

  public CustomerOrder() {}

  public CustomerOrder(Long userId, long totalSum) {
    this.userId = userId;
    this.totalSum = totalSum;
  }

  public Long getId() { return id; }

  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }

  public long getTotalSum() { return totalSum; }
  public void setTotalSum(long totalSum) { this.totalSum = totalSum; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
