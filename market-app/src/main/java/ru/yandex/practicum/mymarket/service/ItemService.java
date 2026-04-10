package ru.yandex.practicum.mymarket.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.exception.EntityNotFoundException;
import ru.yandex.practicum.mymarket.repository.ItemRepository;

import java.time.Duration;
import java.util.List;

@Service
public class ItemService {

  private static final String ITEM_KEY_PREFIX = "item::";
  private static final String PAGE_KEY_PREFIX = "items::";

  private final ItemRepository itemRepository;
  private final ReactiveRedisTemplate<String, Item>   itemCache;
  private final ReactiveRedisTemplate<String, String> stringCache;
  private final ObjectMapper  objectMapper;
  private final Duration      cacheTtl;

  public ItemService(
      ItemRepository itemRepository,
      ReactiveRedisTemplate<String, Item>   itemCache,
      ReactiveRedisTemplate<String, String> stringCache,
      ObjectMapper objectMapper,
      @Value("${cache.item.ttl-minutes:5}") long ttlMinutes) {
    this.itemRepository = itemRepository;
    this.itemCache      = itemCache;
    this.stringCache    = stringCache;
    this.objectMapper   = objectMapper;
    this.cacheTtl       = Duration.ofMinutes(ttlMinutes);
  }

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /**
   * Returns a page of items. Checks the Redis page-cache first; on a miss
   * fetches from the DB and writes the result back to Redis with a TTL.
   */
  public Mono<Page<Item>> findItems(String search, Pageable pageable) {
    String key = pageKey(search, pageable);
    return stringCache.opsForValue().get(key)
        .onErrorResume(e -> Mono.empty())              // Redis down → DB fallback
        .flatMap(json -> deserializePage(json, pageable))
        // Mono.defer ensures fetchFromDb is only called when the cache is empty
        .switchIfEmpty(Mono.defer(() -> fetchFromDb(search, pageable)
            .flatMap(page -> cachePage(key, page))));
  }

  /**
   * Returns a single item by ID. Checks the Redis item-cache first; on a miss
   * fetches from the DB, writes to Redis, and returns the item.
   *
   * @throws EntityNotFoundException if no item with the given ID exists
   */
  public Mono<Item> findById(long id) {
    String key = ITEM_KEY_PREFIX + id;
    return itemCache.opsForValue().get(key)
        .onErrorResume(e -> Mono.empty())              // Redis down → DB fallback
        // Mono.defer ensures the DB is only queried when the cache is empty
        .switchIfEmpty(Mono.defer(() ->
            itemRepository.findById(id)
                .switchIfEmpty(Mono.error(new EntityNotFoundException("Товар", id)))
                .flatMap(item -> itemCache.opsForValue()
                    .set(key, item, cacheTtl)
                    .onErrorReturn(false)              // write failure → still return item
                    .thenReturn(item))));
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private Mono<Page<Item>> fetchFromDb(String search, Pageable pageable) {
    if (search != null && !search.isBlank()) {
      String q = search.trim();
      return Mono.zip(
          itemRepository.searchBy(q, pageable).collectList(),
          itemRepository.countBySearch(q)
      ).map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
    }
    return Mono.zip(
        itemRepository.findAllBy(pageable).collectList(),
        itemRepository.count()
    ).map(t -> new PageImpl<>(t.getT1(), pageable, t.getT2()));
  }

  private Mono<Page<Item>> cachePage(String key, Page<Item> page) {
    return Mono.fromCallable(
            () -> objectMapper.writeValueAsString(
                new CachedPage(page.getContent(), page.getTotalElements())))
        .flatMap(json -> stringCache.opsForValue().set(key, json, cacheTtl))
        .thenReturn(page)
        .onErrorReturn(page);  // serialisation or Redis write failure → return page as-is
  }

  @SuppressWarnings("unchecked")
  private Mono<Page<Item>> deserializePage(String json, Pageable pageable) {
    return Mono.fromCallable(() -> {
      CachedPage cached = objectMapper.readValue(json, CachedPage.class);
      return (Page<Item>) new PageImpl<>(cached.items(), pageable, cached.total());
    }).onErrorResume(e -> Mono.empty());  // deserialisation failure → treat as cache miss
  }

  /** Composite cache key encoding every parameter that affects the result set. */
  private String pageKey(String search, Pageable pageable) {
    String q = (search != null && !search.isBlank()) ? search.trim() : "";
    String sort = pageable.getSort().stream()
        .map(o -> o.getProperty() + ":" + o.getDirection())
        .reduce("", (a, b) -> a.isEmpty() ? b : a + "," + b);
    return String.format("%sq:%s:page:%d:size:%d:sort:%s",
        PAGE_KEY_PREFIX, q, pageable.getPageNumber(), pageable.getPageSize(), sort);
  }

  /** Internal DTO for serialising a page result to/from Redis. */
  private record CachedPage(
      @JsonProperty("items") List<Item> items,
      @JsonProperty("total")  long total) {
  }
}
