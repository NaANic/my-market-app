package ru.yandex.practicum.mymarket.dto;

public class PagingDto {

  private final int pageSize;
  private final int pageNumber;
  private final boolean hasPrevious;
  private final boolean hasNext;

  public PagingDto(int pageSize, int pageNumber,
      boolean hasPrevious, boolean hasNext) {
    this.pageSize = pageSize;
    this.pageNumber = pageNumber;
    this.hasPrevious = hasPrevious;
    this.hasNext = hasNext;
  }

  public int getPageSize() { return pageSize; }
  public int getPageNumber() { return pageNumber; }
  public boolean isHasPrevious() { return hasPrevious; }
  public boolean isHasNext() { return hasNext; }
}
