package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

  @Mock
  ItemRepository itemRepository;

  @InjectMocks
  ItemService itemService;

  @Test
  void findItems_withSearch_delegatesToSearchMethod() {
    Page<Item> expected = new PageImpl<>(List.of(createItem(1L, "Мяч", 500)));
    when(itemRepository.search(eq("мяч"), any(Pageable.class))).thenReturn(expected);

    Page<Item> result = itemService.findItems("мяч", PageRequest.of(0, 5));

    assertThat(result.getContent()).hasSize(1);
    verify(itemRepository).search(eq("мяч"), any(Pageable.class));
    verify(itemRepository, never()).findAll(any(Pageable.class));
  }

  @Test
  void findItems_withBlankSearch_delegatesToFindAll() {
    when(itemRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

    itemService.findItems("   ", PageRequest.of(0, 5));

    verify(itemRepository).findAll(any(Pageable.class));
    verify(itemRepository, never()).search(any(), any());
  }

  @Test
  void findItems_withNullSearch_delegatesToFindAll() {
    when(itemRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

    itemService.findItems(null, PageRequest.of(0, 5));

    verify(itemRepository).findAll(any(Pageable.class));
  }

  @Test
  void findById_existingItem_returnsItem() {
    Item item = createItem(1L, "Test", 100);
    when(itemRepository.findById(1L)).thenReturn(Optional.of(item));

    Item result = itemService.findById(1L);

    assertThat(result.getTitle()).isEqualTo("Test");
  }

  @Test
  void findById_nonExisting_throwsException() {
    when(itemRepository.findById(999L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> itemService.findById(999L))
        .isInstanceOf(RuntimeException.class);
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/test.jpg", price);
    item.setId(id);
    return item;
  }
}
