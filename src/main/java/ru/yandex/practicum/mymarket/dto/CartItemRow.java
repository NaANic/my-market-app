package ru.yandex.practicum.mymarket.dto;

/**
 * Flat projection returned by
 * {@link ru.yandex.practicum.mymarket.repository.CartItemRepository#findWithItemsBySessionId}.
 *
 * <p>Column aliases in the SQL query use snake_case; Spring Data R2DBC's default
 * {@code NamingStrategy} maps them to these camelCase record components automatically:
 * <pre>
 *   cart_item_id   → cartItemId
 *   session_id     → sessionId      (matches ci.session_id directly)
 *   count          → count
 *   item_id        → itemId
 *   item_title     → itemTitle
 *   item_description → itemDescription
 *   item_img_path  → itemImgPath
 *   item_price     → itemPrice
 * </pre>
 *
 * <p>This type is used by the one-query JOIN approach. The two-query IN approach
 * (used in CartService by default) does not need this projection.
 */
public record CartItemRow(
    Long   cartItemId,
    String sessionId,
    int    count,
    Long   itemId,
    String itemTitle,
    String itemDescription,
    String itemImgPath,
    long   itemPrice
) {
  /** Converts the flat row directly to the DTO the controllers expect. */
  public ItemDto toItemDto() {
    return new ItemDto(itemId, itemTitle, itemDescription, itemImgPath, itemPrice, count);
  }
}
