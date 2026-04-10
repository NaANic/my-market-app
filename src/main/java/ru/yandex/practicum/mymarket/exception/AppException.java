package ru.yandex.practicum.mymarket.exception;

public abstract class AppException extends RuntimeException {

  protected AppException(String message) {
    super(message);
  }
}
