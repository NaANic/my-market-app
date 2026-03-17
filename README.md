# Витрина интернет-магазина

Веб-приложение на Spring Boot — витрина товаров с корзиной и оформлением заказов.

## Стек технологий

| Категория | Технология |
|-----------|------------|
| Язык | Java 21 |
| Фреймворк | Spring Boot 3.4, Spring MVC |
| Шаблонизатор | Thymeleaf |
| ORM | Spring Data JPA, Hibernate |
| База данных | PostgreSQL 17 (prod), H2 (тесты) |
| Тесты | JUnit 5, Mockito, MockMvc, AssertJ |
| Сборка | Maven |
| Контейнеризация | Docker, Docker Compose |

## Функциональность

- **Каталог товаров** — плитка, пагинация, поиск по названию/описанию, сортировка (по алфавиту, по цене)
- **Страница товара** — детальная информация, кнопки ±
- **Корзина** — список товаров, количество, общая сумма, удаление, кнопка «Купить»
- **Заказы** — список оформленных заказов, детальная страница заказа

## Запуск

### Docker Compose (рекомендуется)

```bash
docker compose up --build
```

Приложение: http://localhost:8080

Остановка:

```bash
docker compose down
```

### Локально с Maven

1. Запустить PostgreSQL:

```bash
docker run -d --name shopfront-db \
  -e POSTGRES_DB=shopfront \
  -e POSTGRES_USER=shopfront \
  -e POSTGRES_PASSWORD=shopfront \
  -p 5432:5432 \
  postgres:17-alpine
```

2. Запустить приложение:

```bash
./mvnw spring-boot:run
```

### Executable JAR

```bash
./mvnw clean package
java -jar target/my-market-app-0.0.1-SNAPSHOT.jar
```

## Тесты

```bash
./mvnw test
```

55 тестов: unit (Mockito), @DataJpaTest, @WebMvcTest, @SpringBootTest.

## Структура проекта

```
src/main/java/ru/yandex/practicum/mymarket/
├── entity/          — JPA-сущности (Item, CartItem, CustomerOrder, OrderItem)
├── dto/             — DTO и перечисления (ItemDto, OrderDto, PagingDto, SortType, CartAction)
├── repository/      — Spring Data JPA репозитории
├── service/         — бизнес-логика (ItemService, CartService, OrderService)
├── controller/      — Spring MVC контроллеры
└── config/          — DataInitializer (загрузка тестовых данных)
```

## Эндпоинты

| Метод | URL | Описание |
|-------|-----|----------|
| GET | `/`, `/items` | Каталог товаров |
| POST | `/items` | ± товар в корзину (redirect) |
| GET | `/items/{id}` | Страница товара |
| POST | `/items/{id}` | ± товар в корзину |
| GET | `/cart/items` | Корзина |
| POST | `/cart/items` | ±/удалить товар в корзине |
| GET | `/orders` | Список заказов |
| GET | `/orders/{id}` | Детали заказа |
| POST | `/buy` | Оформить заказ |
