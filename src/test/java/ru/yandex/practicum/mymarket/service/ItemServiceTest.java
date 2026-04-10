package ru.yandex.practicum.mymarket.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

  @Mock
  ItemRepository itemRepository;

  @InjectMocks
  ItemService itemService;

  @Test
  void findItems_withSearch_usesSearchRepository() {
    Item item = createItem(1L, "Мяч", 500);
    when(itemRepository.searchBy(eq("мяч"), any())).thenReturn(Flux.just(item));
    when(itemRepository.countBySearch(eq("мяч"))).thenReturn(Mono.just(1L));

    StepVerifier.create(itemService.findItems("мяч", PageRequest.of(0, 5)))
        .assertNext(page -> {
          assertThat(page.getContent()).hasSize(1);
          assertThat(page.getTotalElements()).isEqualTo(1);
          assertThat(page.getContent().get(0).getTitle()).isEqualTo("Мяч");
        })
        .verifyComplete();

    verify(itemRepository).searchBy(eq("мяч"), any());
    verify(itemRepository, never()).findAllBy(any());
  }

  @Test
  void findItems_withBlankSearch_usesFindAll() {
    when(itemRepository.findAllBy(any())).thenReturn(Flux.empty());
    when(itemRepository.count()).thenReturn(Mono.just(0L));

    StepVerifier.create(itemService.findItems("   ", PageRequest.of(0, 5)))
        .assertNext(page -> assertThat(page.getContent()).isEmpty())
        .verifyComplete();

    verify(itemRepository).findAllBy(any());
    verify(itemRepository, never()).searchBy(any(), any());
  }

  @Test
  void findItems_withNullSearch_usesFindAll() {
    when(itemRepository.findAllBy(any())).thenReturn(Flux.empty());
    when(itemRepository.count()).thenReturn(Mono.just(0L));

    StepVerifier.create(itemService.findItems(null, PageRequest.of(0, 5)))
        .assertNext(page -> assertThat(page.getContent()).isEmpty())
        .verifyComplete();

    verify(itemRepository).findAllBy(any());
  }

  @Test
  void findItems_noSearch_pageReflectsTotalCount() {
    when(itemRepository.findAllBy(any())).thenReturn(
        Flux.just(createItem(1L, "A", 100), createItem(2L, "B", 200)));
    when(itemRepository.count()).thenReturn(Mono.just(20L));

    StepVerifier.create(itemService.findItems(null, PageRequest.of(0, 5)))
        .assertNext(page -> {
          assertThat(page.getContent()).hasSize(2);
          assertThat(page.getTotalElements()).isEqualTo(20);
        })
        .verifyComplete();
  }

  @Test
  void findById_existingItem_returnsItem() {
    Item item = createItem(1L, "Test", 100);
    when(itemRepository.findById(1L)).thenReturn(Mono.just(item));

    StepVerifier.create(itemService.findById(1L))
        .assertNext(result -> assertThat(result.getTitle()).isEqualTo("Test"))
        .verifyComplete();
  }

  @Test
  void findById_notFound_emitsError() {
    when(itemRepository.findById(999L)).thenReturn(Mono.empty());

    StepVerifier.create(itemService.findById(999L))
        .expectErrorMatches(ex ->
            ex instanceof RuntimeException && ex.getMessage().contains("999"))
        .verify();
  }

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "Desc", "/img/test.jpg", price);
    item.setId(id);
    return item;
  }
}
