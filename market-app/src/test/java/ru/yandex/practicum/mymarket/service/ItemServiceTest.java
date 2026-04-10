package ru.yandex.practicum.mymarket.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // @BeforeEach stubs not required in every test
class ItemServiceTest {

  @Mock ItemRepository itemRepository;

  @Mock ReactiveRedisTemplate<String, Item>   itemCache;
  @Mock ReactiveValueOperations<String, Item> itemValueOps;

  @Mock ReactiveRedisTemplate<String, String>   stringCache;
  @Mock ReactiveValueOperations<String, String> stringValueOps;

  ItemService itemService;

  @BeforeEach
  void setUp() {
    // Wire opsForValue() on both templates
    when(itemCache.opsForValue()).thenReturn(itemValueOps);
    when(stringCache.opsForValue()).thenReturn(stringValueOps);

    // Default behaviour: cache miss on every read, successful no-op on every write
    when(itemValueOps.get(anyString())).thenReturn(Mono.empty());
    when(itemValueOps.set(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(true));
    when(stringValueOps.get(anyString())).thenReturn(Mono.empty());
    when(stringValueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

    itemService = new ItemService(
        itemRepository, itemCache, stringCache, new ObjectMapper(), 5L);
  }

  // ---------------------------------------------------------------------------
  // findItems
  // ---------------------------------------------------------------------------

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
  void findItems_cacheHit_doesNotQueryDb() {
    Item item = createItem(1L, "Cached", 999);
    String json = toJson(item);
    when(stringValueOps.get(anyString())).thenReturn(Mono.just(
        "{\"items\":[{\"id\":1,\"title\":\"Cached\",\"description\":\"D\",\"imgPath\":\"/i\",\"price\":999}],\"total\":1}"));

    StepVerifier.create(itemService.findItems(null, PageRequest.of(0, 5)))
        .assertNext(page -> assertThat(page.getContent()).hasSize(1))
        .verifyComplete();

    verify(itemRepository, never()).findAllBy(any());
    verify(itemRepository, never()).count();
  }

  // ---------------------------------------------------------------------------
  // findById
  // ---------------------------------------------------------------------------

  @Test
  void findById_cacheMiss_queriesDbAndCaches() {
    Item item = createItem(1L, "Test", 100);
    when(itemRepository.findById(1L)).thenReturn(Mono.just(item));

    StepVerifier.create(itemService.findById(1L))
        .assertNext(result -> assertThat(result.getTitle()).isEqualTo("Test"))
        .verifyComplete();

    verify(itemRepository).findById(1L);
    verify(itemValueOps).set(eq("item::1"), eq(item), any(Duration.class));
  }

  @Test
  void findById_cacheHit_doesNotQueryDb() {
    Item cachedItem = createItem(1L, "FromCache", 200);
    when(itemValueOps.get("item::1")).thenReturn(Mono.just(cachedItem));

    StepVerifier.create(itemService.findById(1L))
        .assertNext(result -> assertThat(result.getTitle()).isEqualTo("FromCache"))
        .verifyComplete();

    verify(itemRepository, never()).findById(any(Long.class));
  }

  @Test
  void findById_notFound_emitsEntityNotFoundException() {
    when(itemRepository.findById(999L)).thenReturn(Mono.empty());

    StepVerifier.create(itemService.findById(999L))
        .expectErrorMatches(ex ->
            ex instanceof EntityNotFoundException && ex.getMessage().contains("999"))
        .verify();
  }

  @Test
  void findById_redisDown_fallsBackToDb() {
    Item item = createItem(1L, "Fallback", 300);
    when(itemValueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));
    when(itemRepository.findById(1L)).thenReturn(Mono.just(item));

    StepVerifier.create(itemService.findById(1L))
        .assertNext(result -> assertThat(result.getTitle()).isEqualTo("Fallback"))
        .verifyComplete();
  }

  // ---------------------------------------------------------------------------

  private Item createItem(Long id, String title, long price) {
    Item item = new Item(title, "D", "/img/test.jpg", price);
    item.setId(id);
    return item;
  }

  private String toJson(Item item) {
    try {
      return new ObjectMapper().writeValueAsString(item);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
