package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
public class ItemService {

  private final ItemRepository itemRepository;

  public ItemService(ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  public Mono<Page<Item>> findItems(String search, Pageable pageable) {
    if (search != null && !search.isBlank()) {
      String q = search.trim();
      return Mono.zip(
          itemRepository.searchBy(q, pageable).collectList(),
          itemRepository.countBySearch(q)
      ).map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
    }
    return Mono.zip(
        itemRepository.findAllBy(pageable).collectList(),
        itemRepository.count()
    ).map(tuple -> new PageImpl<>(tuple.getT1(), pageable, tuple.getT2()));
  }

  public Mono<Item> findById(long id) {
    return itemRepository.findById(id)
        .switchIfEmpty(Mono.error(new RuntimeException("Товар не найден: id=" + id)));
  }
}
