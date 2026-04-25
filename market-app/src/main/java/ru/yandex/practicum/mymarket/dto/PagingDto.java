package ru.yandex.practicum.mymarket.dto;

public record PagingDto(int pageSize, int pageNumber, boolean hasPrevious, boolean hasNext) {}
