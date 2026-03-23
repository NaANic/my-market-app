package ru.yandex.practicum.mymarket.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import ru.yandex.practicum.mymarket.dto.*;
import ru.yandex.practicum.mymarket.entity.Item;
import ru.yandex.practicum.mymarket.service.CartService;
import ru.yandex.practicum.mymarket.service.ItemService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class ItemController {

  private static final int COLUMNS = 3;

  private final ItemService itemService;
  private final CartService cartService;

  public ItemController(ItemService itemService, CartService cartService) {
    this.itemService = itemService;
    this.cartService = cartService;
  }

  @GetMapping({"/", "/items"})
  public String items(
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "NO") SortType sort,
      @RequestParam(defaultValue = "1") int pageNumber,
      @RequestParam(defaultValue = "5") int pageSize,
      HttpSession session,
      Model model
  ) {
    Pageable pageable = PageRequest.of(
        Math.max(0, pageNumber - 1),
        pageSize,
        sort.toSort()
    );

    Page<Item> page = itemService.findItems(search, pageable);
    Map<Long, Integer> cartCounts = cartService.getCartItemCounts(session.getId());

    List<ItemDto> dtos = page.getContent().stream()
        .map(item -> ItemDto.from(item, cartCounts.getOrDefault(item.getId(), 0)))
        .toList();

    List<List<ItemDto>> grid = toGrid(dtos, COLUMNS);

    PagingDto paging = new PagingDto(
        pageSize,
        pageNumber,
        pageNumber > 1,
        page.hasNext()
    );

    model.addAttribute("items", grid);
    model.addAttribute("search", search);
    model.addAttribute("sort", sort.name());
    model.addAttribute("paging", paging);
    return "items";
  }

  @PostMapping("/items")
  public String modifyCartFromItems(
      @RequestParam long id,
      @RequestParam CartAction action,
      @RequestParam(required = false) String search,
      @RequestParam(defaultValue = "NO") SortType sort,
      @RequestParam(defaultValue = "1") int pageNumber,
      @RequestParam(defaultValue = "5") int pageSize,
      HttpSession session
  ) {
    cartService.handleAction(session.getId(), id, action);

    UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/items");
    if (search != null && !search.isEmpty()) {
      builder.queryParam("search", search);
    }
    builder.queryParam("sort", sort.name())
        .queryParam("pageNumber", pageNumber)
        .queryParam("pageSize", pageSize);

    return "redirect:" + builder.toUriString();
  }

  @GetMapping("/items/{id}")
  public String item(@PathVariable long id,
      HttpSession session,
      Model model) {
    Item item = itemService.findById(id);
    int count = cartService.getItemCount(session.getId(), id);
    model.addAttribute("item", ItemDto.from(item, count));
    return "item";
  }

  @PostMapping("/items/{id}")
  public String modifyCartFromItem(
      @PathVariable long id,
      @RequestParam CartAction action,
      HttpSession session,
      Model model
  ) {
    cartService.handleAction(session.getId(), id, action);

    Item item = itemService.findById(id);
    int count = cartService.getItemCount(session.getId(), id);
    model.addAttribute("item", ItemDto.from(item, count));
    return "item";
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
