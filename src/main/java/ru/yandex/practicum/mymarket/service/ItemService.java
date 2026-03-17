package ru.yandex.practicum.mymarket.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

@Service
@Transactional(readOnly = true)
public class ItemService {

  private final ItemRepository itemRepository;

  public ItemService(ItemRepository itemRepository) {
    this.itemRepository = itemRepository;
  }

  public Page<Item> findItems(String search, Pageable pageable) {
    if (search != null && !search.isBlank()) {
      return itemRepository.search(search.trim(), pageable);
    }
    return itemRepository.findAll(pageable);
  }

  public Item findById(long id) {
    return itemRepository.findById(id)
        .orElseThrow(() -> new RuntimeException("Товар не найден: id=" + id));
  }
}
