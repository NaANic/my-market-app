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

  @Column("session_id")
  private String sessionId;

  @Column("total_sum")
  private long totalSum;

  @CreatedDate
  @Column("created_at")
  private LocalDateTime createdAt;

  public CustomerOrder() {}

  public CustomerOrder(String sessionId, long totalSum) {
    this.sessionId = sessionId;
    this.totalSum = totalSum;
  }

  public Long getId() { return id; }

  public String getSessionId() { return sessionId; }
  public void setSessionId(String sessionId) { this.sessionId = sessionId; }

  public long getTotalSum() { return totalSum; }
  public void setTotalSum(long totalSum) { this.totalSum = totalSum; }

  public LocalDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
