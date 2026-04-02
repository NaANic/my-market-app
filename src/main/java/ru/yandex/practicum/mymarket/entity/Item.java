package ru.yandex.practicum.mymarket.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("items")
public class Item {

  @Id
  private Long id;

  private String title;

  private String description;

  @Column("img_path")
  private String imgPath;

  private long price;

  public Item() {}

  public Item(String title, String description, String imgPath, long price) {
    this.title = title;
    this.description = description;
    this.imgPath = imgPath;
    this.price = price;
  }

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }

  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }

  public String getImgPath() { return imgPath; }
  public void setImgPath(String imgPath) { this.imgPath = imgPath; }

  public long getPrice() { return price; }
  public void setPrice(long price) { this.price = price; }
}
