package ru.yandex.practicum.mymarket.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.mymarket.dto.*;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.CurrentUserService;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class ItemController {

  private static final int COLUMNS = 3;

  private final ItemService        itemService;
  private final CartService        cartService;
  private final CurrentUserService currentUserService;

  public ItemController(ItemService itemService,
      CartService cartService,
      CurrentUserService currentUserService) {
    this.itemService        = itemService;
    this.cartService        = cartService;
    this.currentUserService = currentUserService;
  }

  @GetMapping({"/", "/items"})
  public Mono<String> items(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "NO") SortType sort,
      @RequestParam(defaultValue = "1") int pageNumber,
      @RequestParam(defaultValue = "5") int pageSize,
      Model model
  ) {
    Pageable pageable = PageRequest.of(
        Math.max(0, pageNumber - 1),
        pageSize,
        sort.toSort()
    );

    // Anonymous users see the catalogue without per-row counts
    Mono<Map<Long, Integer>> cartCountsMono = currentUserService.getCurrentUserId()
        .flatMap(cartService::getCartItemCounts)
        .defaultIfEmpty(Map.of());

    return Mono.zip(
        itemService.findItems(search, pageable),
        cartCountsMono
    ).map(tuple -> {
      Page<Item> page = tuple.getT1();
      Map<Long, Integer> cartCounts = tuple.getT2();

      List<ItemDto> dtos = page.getContent().stream()
          .map(item -> ItemDto.from(item, cartCounts.getOrDefault(item.getId(), 0)))
          .toList();

      PagingDto paging = new PagingDto(pageSize, pageNumber, pageNumber > 1, page.hasNext());

      model.addAttribute("items", toGrid(dtos, COLUMNS));
      model.addAttribute("search", search);
      model.addAttribute("sort", sort.name());
      model.addAttribute("paging", paging);
      return "items";
    });
  }

  @PreAuthorize("isAuthenticated()")
  @PostMapping("/items")
  public Mono<String> modifyCartFromItems(
      @RequestParam long id,
      @RequestParam CartAction action,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "NO") SortType sort,
      @RequestParam(defaultValue = "1") int pageNumber,
      @RequestParam(defaultValue = "5") int pageSize
  ) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/items");
    if (search != null && !search.isEmpty()) {
      builder.queryParam("search", search);
    }
    builder.queryParam("sort", sort.name())
        .queryParam("pageNumber", pageNumber)
        .queryParam("pageSize", pageSize);

    String redirectUrl = "redirect:" + builder.toUriString();
    return currentUserService.getCurrentUserId()
        .flatMap(userId -> cartService.handleAction(userId, id, action))
        .thenReturn(redirectUrl);
  }

  @GetMapping("/items/{id}")
  public Mono<String> item(
      @PathVariable long id,
      Model model
  ) {
    Mono<Integer> countMono = currentUserService.getCurrentUserId()
        .flatMap(userId -> cartService.getItemCount(userId, id))
        .defaultIfEmpty(0);

    return Mono.zip(
        itemService.findById(id),
        countMono
    ).map(tuple -> {
      model.addAttribute("item", ItemDto.from(tuple.getT1(), tuple.getT2()));
      return "item";
    });
  }

  @PreAuthorize("isAuthenticated()")
  @PostMapping("/items/{id}")
  public Mono<String> modifyCartFromItem(
      @PathVariable long id,
      @RequestParam CartAction action,
      Model model
  ) {
    return currentUserService.getCurrentUserId()
        .flatMap(userId -> cartService.handleAction(userId, id, action)
            .then(Mono.zip(
                itemService.findById(id),
                cartService.getItemCount(userId, id)
            )))
        .map(tuple -> {
          model.addAttribute("item", ItemDto.from(tuple.getT1(), tuple.getT2()));
          return "item";
        });
  }

  private List<List<ItemDto>> toGrid(List<ItemDto> items, int columns) {
    List<List<ItemDto>> grid = new ArrayList<>();
    for (int i = 0; i < items.size(); i += columns) {
      List<ItemDto> row = new ArrayList<>(
          items.subList(i, Math.min(i + columns, items.size()))
      );
      while (row.size() < columns) {
        row.add(ItemDto.empty());
      }
      grid.add(row);
    }
    return grid;
  }
}
