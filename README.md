# Витрина интернет-магазина

Многомодульный реактивный Spring Boot проект, реализующий онлайн-витрину с корзиной, оформлением заказов и сервисом оплаты.

---

## Содержание

- [Архитектура](#архитектура)
- [Модули](#модули)
- [Технологический стек](#технологический-стек)
- [Быстрый старт (Docker Compose)](#быстрый-старт-docker-compose)
- [Локальная разработка](#локальная-разработка)
- [Конфигурация](#конфигурация)
- [API сервиса оплаты](#api-сервиса-оплаты)
- [Структура базы данных](#структура-базы-данных)
- [Запуск тестов](#запуск-тестов)
- [Известные ограничения и следующие шаги](#известные-ограничения-и-следующие-шаги)

---

## Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                      Browser / Client                   │
└────────────────────────┬────────────────────────────────┘
                         │ HTTP (Thymeleaf SSR)
                         ▼
┌────────────────────────────────────────────────────────────┐
│                      market-app  :8080                     │
│                                                            │
│  ItemController  CartController  OrderController           │
│        │               │               │                   │
│   ItemService     CartService     OrderService             │
│        │               │               │                   │
│   Redis cache      R2DBC (PG)    PaymentClientService      │
│   (read-through)                       │                   │
└────────────────────────────────────────┼───────────────────┘
                                         │ HTTP (WebClient)
                                         ▼
                          ┌──────────────────────────┐
                          │  payment-service  :8081   │
                          │                           │
                          │  PaymentController        │
                          │  BalanceStore (AtomicLong)│
                          └──────────────────────────┘

      ┌──────────────────┐       ┌─────────────────────────┐
      │  PostgreSQL :5432│       │      Redis :6379        │
      │  (market-app DB) │       │  (item + page cache)    │
      └──────────────────┘       └─────────────────────────┘
```

Всё приложение работает в **полностью реактивной** модели (Project Reactor / WebFlux) без блокирующих вызовов.

---

## Модули

### `market-app`
Основное приложение-витрина. Server-side rendering на Thymeleaf + Spring WebFlux.

| Пакет | Назначение |
|---|---|
| `controller` | `ItemController`, `CartController`, `OrderController` — маршруты и шаблоны |
| `service` | `ItemService` (Redis cache), `CartService`, `OrderService` (оформление заказа), `PaymentClientService` (фасад над HTTP-клиентом) |
| `config` | `RedisConfig` (typed templates), `PaymentClientConfig` (generated WebClient beans) |
| `entity` | `Item`, `CartItem`, `CustomerOrder`, `OrderItem` |
| `repository` | R2DBC-репозитории |
| `exception` | `EntityNotFoundException`, `CartIsEmptyException`, `PaymentFailedException` |
| `web` | `GlobalExceptionHandler` |

### `payment-service`
Микросервис управления балансом. Реализует OpenAPI-контракт (`payment-api.yaml`).

| Класс | Назначение |
|---|---|
| `BalanceStore` | `AtomicLong`-хранилище баланса; CAS-цикл в `deduct()` гарантирует корректность под конкурентной нагрузкой |
| `PaymentController` | Реализует сгенерированные интерфейсы `BalanceApi` и `PaymentApi` |
| `InsufficientFundsException` | Бросается при нехватке средств; превращается в HTTP 402 через `PaymentExceptionHandler` |
| `PaymentExceptionHandler` | `@RestControllerAdvice` → сериализует `PaymentError` с полем `balance` |

---

## Технологический стек

| Слой | Технология |
|---|---|
| Web / SSR | Spring WebFlux, Thymeleaf |
| Реактивность | Project Reactor (Mono / Flux) |
| База данных | PostgreSQL + Spring Data R2DBC |
| Кэш | Redis (Reactive Lettuce), TTL 5 мин |
| Межсервисная связь | OpenAPI Generator 7.6.0 → WebClient (`java/webclient`) |
| Серверные стабы | OpenAPI Generator 7.6.0 → Spring (`spring`, `interfaceOnly=true`, `reactive=true`) |
| Сборка | Maven 3 (многомодульный), Spring Boot 3.4.x, Java 21 |
| Контейнеризация | Docker Compose v2, многоэтапные Dockerfile |
| Тесты | JUnit 5, Mockito, StepVerifier, `@WebFluxTest` |

---

## Быстрый старт (Docker Compose)

### Предварительные требования

- Docker Desktop ≥ 4.x (или Docker Engine + Compose plugin)
- Свободные порты: `5432`, `6379`, `8080`, `8081`

### Запуск

```bash
# 1. Клонировать репозиторий
git clone <repo-url>
cd <repo-dir>

# 2. (Опционально) задать начальный баланс
cp .env.example .env
# Отредактировать .env: PAYMENT_INITIAL_BALANCE=1000000  (в копейках)

# 3. Собрать образы и поднять все сервисы
docker compose up --build
```

После старта:

| Сервис | URL |
|---|---|
| Витрина магазина | http://localhost:8080/items |
| Корзина | http://localhost:8080/cart/items |
| Заказы | http://localhost:8080/orders |
| Баланс (payment API) | http://localhost:8081/balance |

### Порядок запуска (depends_on)

```
redis (healthy) ──┐
                  ├──▶ market-app (healthy)
payment-service ──┘
   (healthy)
```

Healthcheck в `market-app` и `payment-service` опрашивает `/actuator/health`.

### Остановка

```bash
docker compose down          # остановить, сохранить тома
docker compose down -v       # остановить и удалить тома (сброс БД)
```

---

## Локальная разработка

### Требования

- JDK 21
- Maven 3.9+
- Запущенные PostgreSQL и Redis (локально или через Docker)

### Запустить только инфраструктуру

```bash
docker compose up redis postgres -d
```

Или используйте готовый профиль:

```bash
docker run -d -p 6379:6379 redis:7-alpine
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=shopfront \
  -e POSTGRES_USER=shopfront \
  -e POSTGRES_PASSWORD=shopfront \
  postgres:16-alpine
```

### Сборка всего проекта

```bash
# Сгенерировать стабы + скомпилировать + выполнить тесты
mvn verify

# Только сгенерировать стабы и скомпилировать (пропустить тесты)
mvn package -DskipTests
```

### Запуск сервисов по отдельности

```bash
# payment-service (порт 8081)
mvn spring-boot:run -pl payment-service

# market-app (порт 8080) — в отдельном терминале
mvn spring-boot:run -pl market-app
```

### Регенерация OpenAPI-стабов

Стабы генерируются автоматически на фазе `generate-sources`. Для явного запуска:

```bash
mvn generate-sources -pl market-app      # WebClient-клиент (java/webclient)
mvn generate-sources -pl payment-service # Spring-интерфейсы (interfaceOnly)
```

Единственный источник истины — `payment-service/src/main/resources/openapi/payment-api.yaml`. Клиент в `market-app` читает тот же файл через относительный путь.

---

## Конфигурация

### `market-app` — `application.yml`

| Свойство | По умолчанию | Описание |
|---|---|---|
| `spring.r2dbc.url` | `r2dbc:postgresql://localhost:5432/shopfront` | URL БД |
| `spring.r2dbc.username` | `shopfront` | Пользователь БД |
| `spring.r2dbc.password` | `shopfront` | Пароль БД |
| `spring.data.redis.host` | `localhost` | Хост Redis |
| `spring.data.redis.port` | `6379` | Порт Redis |
| `cache.item.ttl-minutes` | `5` | TTL кэша товаров и страниц |
| `payment.service.url` | `http://localhost:8081` | Базовый URL payment-service |

В Docker эти свойства переопределяются переменными окружения:

```
SPRING_DATA_REDIS_URL=redis://market-redis:6379
PAYMENT_SERVICE_URL=http://market-payment:8081
```

### `payment-service` — `application.yml`

| Свойство | По умолчанию | Описание |
|---|---|---|
| `server.port` | `8081` | Порт сервера |
| `payment.initial-balance` | `100000` | Начальный баланс в копейках (= 1 000 руб.) |

В Docker:

```
PAYMENT_INITIAL_BALANCE=1000000   # из .env
```

---

## API сервиса оплаты

Полный контракт: [`payment-service/src/main/resources/openapi/payment-api.yaml`](payment-service/src/main/resources/openapi/payment-api.yaml)

### `GET /balance`

Возвращает текущий баланс счёта.

```json
HTTP 200
{ "balance": 100000 }
```

### `POST /payment`

Списывает сумму со счёта.

**Запрос:**
```json
{ "orderId": 42, "amount": 5000 }
```

**Успех (HTTP 200):**
```json
{ "success": true, "remainingBalance": 95000 }
```

**Нехватка средств (HTTP 402):**
```json
{ "message": "Insufficient funds", "balance": 3000 }
```

> Поле `balance` в ответе 402 считывается в `market-app` (`OrderService.extractBalance()`) для отображения пользователю в сообщении об ошибке.

---

## Структура базы данных

Схема инициализируется автоматически через `schema.sql` при старте `market-app` (`spring.sql.init.mode=always`).

```sql
items            -- каталог товаров
cart_items       -- строки корзины (session_id + item_id + count)
customer_orders  -- заголовок заказа (session_id, total_sum, created_at)
order_items      -- строки заказа (snapshot: title, price на момент покупки)
```

`order_items` хранит денормализованный снапшот (`title`, `price`) — изменение цены товара после оформления заказа не влияет на историю.

---

## Запуск тестов

```bash
# Все тесты во всех модулях
mvn test

# Только market-app
mvn test -pl market-app

# Только payment-service
mvn test -pl payment-service
```

### Покрытие тестами

**`market-app`**

| Класс | Тип теста | Что проверяется |
|---|---|---|
| `ItemServiceTest` | Юнит (Mockito) | Кэш-хит, кэш-мисс, Redis-недоступен (fallback), поиск, 404 |
| `OrderServiceTest` | Юнит (Mockito) | Успешный заказ, расчёт суммы, пустая корзина, 402 → `PaymentFailedException`, сломанный JSON в теле 402 |
| `PaymentClientServiceTest` | Юнит (Mockito) | `getBalance`, `pay` (успех), проброс ошибки |
| `ItemControllerTest` | `@WebFluxTest` | Рендер страницы товаров, пагинация |
| `OrderControllerTest` | `@WebFluxTest` | Список заказов, детальная страница, POST /buy |

**`payment-service`**

| Класс | Тип теста | Что проверяется |
|---|---|---|
| `BalanceStoreTest` | Юнит | Начальный баланс, списание, точное обнуление, нехватка средств (баланс не изменяется), конкурентный старт (ровно 1 победитель), итоговый баланс при конкурентных списаниях |
| `PaymentControllerTest` | `@WebFluxTest` | `GET /balance` → 200, `POST /payment` → 200, `POST /payment` → 402 (поля `message` и `balance`) |

---

## Известные ограничения и следующие шаги

**Баланс в памяти.** `BalanceStore` сбрасывается при перезапуске `payment-service`. Для production необходимо персистентное хранилище (PostgreSQL / Redis с `PERSIST`).

**Незавершённые заказы.** При отказе оплаты строка `customer_orders` остаётся в БД без статуса. В следующем спринте — добавить колонку `status` (`PENDING` / `PAID` / `FAILED`) и обновлять её.

**Аутентификация.** Сессия идентифицируется только по `sessionId` (Spring WebSession). Нет авторизации пользователей.

**Сообщение об ошибке в URL.** `PaymentFailedException` передаётся как query-параметр `?error=` (виден в адресной строке и логах). Для production — flash-атрибуты на базе Redis.

**Один счёт на всех.** В текущей реализации все пользователи разделяют один баланс. В следующем спринте — сделать баланс per-user.