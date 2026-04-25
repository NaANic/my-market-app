package ru.yandex.practicum.mymarket.exception;

/** Thrown when a requested entity does not exist. Maps to HTTP 404. */
public class EntityNotFoundException extends AppException {

  private final String entityName;
  private final Object entityId;

  public EntityNotFoundException(String entityName, Object entityId) {
    super(entityName + " не найден: " + entityId);
    this.entityName = entityName;
    this.entityId   = entityId;
  }

  public String getEntityName() { return entityName; }
  public Object getEntityId()   { return entityId; }
}
