package ru.yandex.practicum.mymarket.dto;

import org.springframework.data.domain.Sort;

public enum SortType {

  NO, ALPHA, PRICE;

  public Sort toSort() {
    return switch (this) {
      case ALPHA -> Sort.by("title");
      case PRICE -> Sort.by("price");
      default -> Sort.unsorted();
    };
  }
}
