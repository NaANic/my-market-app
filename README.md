# my-market-app

Учебный многомодульный проект интернет-витрины: Spring Boot 3 + WebFlux + Thymeleaf + R2DBC.

```
┌──────────────────┐     OAuth2 client_credentials    ┌────────────────┐
│   market-app     │◄────────────────────────────────►│   Keycloak     │
│  (порт 8080)     │                                  │  (порт 8082)   │
│  SSR Thymeleaf   │                                  └────────────────┘
└────────┬─────────┘                                          ▲
         │                                                    │
         │ Bearer JWT                                         │ JWKS
         ▼                                                    │
┌──────────────────┐                                          │
│ payment-service  │──────────────────────────────────────────┘
│  (порт 8081)     │   проверка подписи JWT
│  REST + WebFlux  │
└──────────────────┘
         │
         ▼
PostgreSQL + Redis
```

---

## Содержание

- [Что нового в Sprint 8](#что-нового-в-sprint-8)
- [Модули](#модули)
- [Технологический стек](#технологический-стек)
- [Быстрый старт (Docker Compose)](#быстрый-старт-docker-compose)
- [Локальная разработка](#локальная-разработка)
- [Аутентификация и авторизация](#аутентификация-и-авторизация-sprint-8)
- [Конфигурация](#конфигурация)
- [Тесты](#тесты)
- [Полезные ссылки](#полезные-ссылки)

---

## Что нового в Sprint 8

- **Form-login** через Spring Security: пароли хранятся в БД (BCrypt), учётные записи создаются автоматически при первом старте.
- **Per-user корзина и заказы**: `cart_items` и `customer_orders` теперь привязаны к `user_id`, а не к сессионной cookie. Корзина alice не видна bob.
- **OAuth2 client_credentials** между `market-app` и `payment-service`: токены выпускает Keycloak, payment-service валидирует подпись через JWKS.
- **Защищённые эндпоинты**: каталог (`/items`) остаётся публичным, всё остальное требует входа.
- **Logout** очищает сессионные cookies через заголовок `Clear-Site-Data`.

---

## Модули

### `market-app`
Витрина с server-side rendering на Thymeleaf и Spring WebFlux.

| Пакет | Назначение |
|---|---|
| `controller` | `ItemController`, `CartController`, `OrderController` — маршруты и шаблоны |
| `service` | `ItemService` (Redis cache), `CartService`, `OrderService`, `PaymentClientService`, `CurrentUserService` |
| `config` | `SecurityConfig`, `PaymentClientConfig` (OAuth2 client), `RedisConfig`, `DataInitializer` |
| `entity` | `Item`, `CartItem`, `CustomerOrder`, `OrderItem`, `User` |
| `repository` | R2DBC-репозитории |
| `exception` | `EntityNotFoundException`, `CartIsEmptyException`, `PaymentFailedException` |
| `web` | `GlobalExceptionHandler` |

### `payment-service`
Микросервис управления балансом. Реализует OpenAPI-контракт (`payment-api.yaml`).

| Класс | Назначение |
|---|---|
| `BalanceStore` | `AtomicLong`-хранилище баланса; CAS-цикл в `deduct()` гарантирует корректность под конкурентной нагрузкой |
| `PaymentController` | Реализует сгенерированные интерфейсы `BalanceApi` и `PaymentApi` |
| `SecurityConfig` | OAuth2 resource server: проверяет JWT на каждом запросе, оставляет `/actuator/health` публичным |
| `InsufficientFundsException` | Бросается при нехватке средств; превращается в HTTP 402 через `PaymentExceptionHandler` |
| `PaymentExceptionHandler` | `@RestControllerAdvice` → сериализует `PaymentError` с полем `balance` |

---

## Технологический стек

| Слой | Технология |
|---|---|
| Web / SSR | Spring WebFlux, Thymeleaf, Spring Security 6 |
| Реактивность | Project Reactor (Mono / Flux) |
| Аутентификация | Form-login + BCrypt; OAuth2 client_credentials между сервисами |
| База данных | PostgreSQL + Spring Data R2DBC |
| Кэш | Redis (Reactive Lettuce), TTL 5 мин |
| Identity Provider | Keycloak 25 (`docker compose`) |
| Межсервисная связь | OpenAPI Generator 7.6.0 → WebClient (`java/webclient`) с OAuth2-фильтром |
| Серверные стабы | OpenAPI Generator 7.6.0 → Spring (`spring`, `interfaceOnly=true`, `reactive=true`) |
| Сборка | Maven 3 (многомодульный), Spring Boot 3.4.x, Java 21 |
| Контейнеризация | Docker Compose v2, многоэтапные Dockerfile |
| Тесты | JUnit 5, Mockito, StepVerifier, `@WebFluxTest`, `spring-security-test` |

---

## Быстрый старт (Docker Compose)

### Предварительные требования

- Docker Desktop ≥ 4.x (или Docker Engine + Compose plugin)
- Свободные порты: `5432`, `6379`, `8080`, `8081`, `8082`

### Шаги

```bash
# 1. Клонировать репозиторий
git clone <repo-url>
cd my-market-app

# 2. Подготовить .env
cp .env.example .env
# Пока не редактируем — заполним KEYCLOAK_CLIENT_SECRET после шага 4

# 3. Запустить только Keycloak (он стартует ~30 секунд)
docker compose up keycloak -d
docker compose ps           # дождаться status=healthy

# 4. Настроить realm и client в Keycloak
#    См. docs/keycloak-setup.md — пошаговый runbook
#    Скопировать сгенерированный client secret в .env:
#       KEYCLOAK_CLIENT_SECRET=<скопированный секрет>

# 5. Запустить весь стек
docker compose up --build
```

После старта:

| Сервис | URL |
|---|---|
| Витрина магазина | http://localhost:8080/items |
| Корзина (требует login) | http://localhost:8080/cart/items |
| Заказы (требует login) | http://localhost:8080/orders |
| Login | http://localhost:8080/login |
| Баланс (payment API, нужен JWT) | http://localhost:8081/balance |
| Keycloak admin | http://localhost:8082/admin/ |

### Тестовые пользователи

| Username | Password |
|---|---|
| `alice` | `alice123` |
| `bob`   | `bob123` |

### Порядок запуска (depends_on)

```
       keycloak (healthy) ──┐
       db (healthy) ────────┤
                            ├──▶ market-app (healthy)
       redis (healthy) ─────┤
                            │
payment-service (healthy) ──┘
   (зависит от keycloak)
```

`market-app` и `payment-service` имеют healthcheck на `/actuator/health`.

### Остановка

```bash
docker compose down          # остановить, сохранить тома
docker compose down -v       # остановить и удалить тома (сброс БД и Keycloak)
```

---

## Локальная разработка

### Требования

- JDK 21
- Maven 3.9+
- Запущенные PostgreSQL и Redis (локально или через Docker)

### Запустить только инфраструктуру

```bash
docker compose up db redis -d
```

Или вручную:

```bash
docker run -d -p 6379:6379 redis:7-alpine
docker run -d -p 5432:5432 \
  -e POSTGRES_DB=shopfront \
  -e POSTGRES_USER=shopfront \
  -e POSTGRES_PASSWORD=shopfront \
  postgres:16-alpine
```

### Сборка проекта

```bash
# Сгенерировать стабы + скомпилировать + выполнить тесты
mvn verify

# Только сборка без тестов
mvn package -DskipTests
```

### Запуск сервисов по отдельности

```bash
# payment-service (порт 8081)
mvn spring-boot:run -pl payment-service

# market-app (порт 8080) — в отдельном терминале
mvn spring-boot:run -pl market-app
```

> Локальный запуск без Keycloak: профиль `oauth2` **не активен** по умолчанию,
> поэтому `PaymentClientConfig` (с OAuth2-фильтром) не загружается. WebClient
> ходит в payment-service без авторизации. Это удобно для UI-разработки.

> Для полноценного OAuth2 нужно либо запустить Keycloak (`docker compose up keycloak`)
> и активировать профиль `SPRING_PROFILES_ACTIVE=oauth2`, либо использовать Docker Compose целиком.

### Регенерация OpenAPI-стабов

Стабы генерируются автоматически на фазе `generate-sources`. Для явного запуска:

```bash
mvn generate-sources -pl market-app      # WebClient-клиент (java/webclient)
mvn generate-sources -pl payment-service # Spring-интерфейсы (interfaceOnly)
```

Единственный источник истины — `payment-service/src/main/resources/openapi/payment-api.yaml`. Клиент в `market-app` читает тот же файл через относительный путь.

---

## Аутентификация и авторизация (Sprint 8)

### Что защищено

| Эндпоинт | Метод | Доступ |
|---|---|---|
| `/items`, `/items/{id}` | GET | публично |
| `/login`, `/logout`, статика, `/actuator/health` | — | публично |
| `/items` | POST (изменение корзины) | требует login |
| `/items/{id}` | POST | требует login |
| `/cart/items` | GET, POST | требует login |
| `/orders`, `/orders/{id}`, `/buy` | GET, POST | требует login |

### Form-login

`market-app` использует Spring Security form-login. Пароли хранятся в таблице
`users` (BCrypt-хеш). При первом старте `DataInitializer` создаёт двух
тестовых пользователей: `alice` и `bob`.

При успешном логине:
- Браузер получает SESSION cookie.
- `CurrentUserService` извлекает `Authentication` из реактивного контекста
  и возвращает `Long userId` для использования в сервисах корзины/заказов.
- Корзина и заказы привязаны к `user_id`, а не к `session_id`. Если alice
  выйдет и зайдёт bob, bob увидит свою (пустую) корзину.

При logout:
- Запускается `Clear-Site-Data` заголовок: cookies + storage + cache очищаются на стороне браузера.
- Происходит редирект на `/items`.

### OAuth2 client_credentials: market-app → payment-service

Между сервисами используется машинная аутентификация:

```
[market-app]  ──client_credentials──▶  [Keycloak]
       │                                    │
       └────────────── access_token ────────┘
                            │ Authorization: Bearer
                            ▼
        [payment-service]  validates JWT via JWKS
```

- `market-app` запрашивает access_token у Keycloak (grant_type=client_credentials).
- Токен прикрепляется к каждому запросу к payment-service через
  `ServerOAuth2AuthorizedClientExchangeFilterFunction`.
- `payment-service` валидирует подпись JWT через JWKS-эндпоинт Keycloak.
- Токен кэшируется и автоматически обновляется при истечении.

Без валидного JWT payment-service отвечает HTTP 401 на `/balance` и `/payment`.
`/actuator/health` остаётся публичным — его опрашивает Docker healthcheck.

### Настройка Keycloak

Один раз после первого `docker compose up keycloak` нужно:

1. Открыть http://localhost:8082/admin/ (admin/admin).
2. Создать realm `market`.
3. Создать client `market-app` (Client authentication ON, Service accounts roles ON).
4. Скопировать **Client secret** из вкладки **Credentials**.
5. Подставить секрет в `.env`:
   ```dotenv
   KEYCLOAK_CLIENT_SECRET=<скопированный секрет>
   ```

Подробный пошаговый runbook — в [docs/keycloak-setup.md](docs/keycloak-setup.md).

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

### `market-app` — профиль `oauth2` (`application-oauth2.yml`)

Активируется через `SPRING_PROFILES_ACTIVE=oauth2`. В Docker Compose активирован по умолчанию.

| Свойство | По умолчанию | Описание |
|---|---|---|
| `spring.security.oauth2.client.registration.keycloak.client-id` | `market-app` | OAuth2 client ID |
| `spring.security.oauth2.client.registration.keycloak.client-secret` | `please-override` | Client secret |
| `spring.security.oauth2.client.registration.keycloak.authorization-grant-type` | `client_credentials` | Grant type |
| `spring.security.oauth2.client.provider.keycloak.issuer-uri` | `http://localhost:8082/realms/market` | Keycloak issuer URL |

### `payment-service` — `application.yml`

| Свойство | По умолчанию | Описание |
|---|---|---|
| `server.port` | `8081` | Порт сервера |
| `payment.initial-balance` | `100000` | Начальный баланс в копейках (= 1 000 руб.) |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `http://localhost:8082/realms/market` | Keycloak issuer для JWKS |

### Переменные окружения в Docker Compose

`market-app`:
```
SPRING_R2DBC_URL=r2dbc:postgresql://db:5432/shopfront
SPRING_DATA_REDIS_HOST=redis
SPRING_PROFILES_ACTIVE=oauth2
PAYMENT_SERVICE_URL=http://payment-service:8081
KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/market
KEYCLOAK_CLIENT_ID=${KEYCLOAK_CLIENT_ID:-market-app}
KEYCLOAK_CLIENT_SECRET=${KEYCLOAK_CLIENT_SECRET:-please-override}
```

`payment-service`:
```
PAYMENT_INITIAL_BALANCE=${PAYMENT_INITIAL_BALANCE:-1000000}
KEYCLOAK_ISSUER_URI=http://keycloak:8080/realms/market
```

### `.env`

Создаётся из `.env.example`:

```dotenv
PAYMENT_INITIAL_BALANCE=1000000
KEYCLOAK_CLIENT_ID=market-app
KEYCLOAK_CLIENT_SECRET=please-override-after-keycloak-setup
```

---
## Ручная проверка (smoke test)

После запуска стека и настройки Keycloak (см. выше) можно прогнать
end-to-end сценарий через браузер.

### Запуск стека

```bash
# 1. Подготовить .env (если ещё не сделано)
cp .env.example .env

# 2. Поднять Keycloak отдельно и дождаться готовности
docker compose up -d keycloak
sleep 30

# 3. Один раз настроить realm + client (см. docs/keycloak-setup.md)
#    - Создать realm "market"
#    - Создать client "market-app" с client_credentials
#    - Скопировать Client secret и подставить в .env

# 4. Проверить, что Keycloak выдаёт токен
source .env
curl -s -X POST \
  http://localhost:8082/realms/market/protocol/openid-connect/token \
  -d grant_type=client_credentials \
  -d client_id=market-app \
  -d client_secret=$KEYCLOAK_CLIENT_SECRET \
  -w "\nHTTP %{http_code}\n"
# Ожидаем HTTP 200 и поле access_token

# 5. Поднять весь стек
docker compose up -d
sleep 30
docker compose ps    # все 5 контейнеров должны быть Up
```

### Сценарий проверки

| Шаг | Действие | Ожидаемый результат |
|---|---|---|
| 1 | Открыть `http://localhost:8080/items` | Виден каталог товаров (анонимный режим, без шапки авторизации) |
| 2 | Открыть `http://localhost:8080/login`, ввести `alice` / `alice123` | Вход успешен, в шапке появляются "Заказы / Корзина / Выйти" |
| 3 | Нажать **+** на любом товаре в каталоге | Счётчик количества меняется на `1` |
| 4 | Открыть **Корзина** | Видна корзина с добавленным товаром и итоговой суммой |
| 5 | Нажать **Купить** на странице корзины | Редирект на страницу заказа: «Поздравляем! Успешная покупка!» |
| 6 | Открыть **Заказы** | Виден созданный заказ |
| 7 | Нажать **Выйти**, затем войти как `bob` / `bob123` | Шапка снова показывает "Заказы / Корзина / Выйти" |
| 8 | Открыть **Корзина** | Корзина пустая — товар alice не виден |
| 9 | Открыть **Заказы** | Список пуст — заказ alice не виден |

### Что проверяет сценарий

- Шаги 2 и 7 — form-login Spring Security работает
- Шаг 5 — OAuth2 client_credentials цепочка: market-app → Keycloak → JWT → payment-service
- Шаги 8 и 9 — корзина и заказы изолированы по `user_id`, не по сессионной cookie

### Остановка

```bash
docker compose down       # остановить контейнеры (данные сохраняются)
docker compose down -v    # остановить и удалить тома (сброс БД и Keycloak)
```

> ⚠️ После `down -v` потребуется повторно настроить realm и client в Keycloak.
> 
## Тесты

```bash
mvn test                       # все тесты
mvn -pl market-app test        # только market-app
mvn -pl payment-service test   # только payment-service
```

### Структура

| Модуль | Класс | Что проверяет |
|---|---|---|
| market-app | `ItemControllerTest`, `CartControllerTest`, `OrderControllerTest` | `@WebFluxTest` slice — рендеринг страниц |
| market-app | `CartServiceTest`, `OrderServiceTest` | юнит-тесты сервисов |
| market-app | `CartItemRepositoryTest`, `OrderRepositoryTest`, `UserRepositoryTest`, `ItemRepositoryTest` | `@DataR2dbcTest` — SQL-запросы |
| market-app | `FullFlowIntegrationTest` | `@SpringBootTest` — корзина → оформление заказа |
| market-app | `AuthAccessControlTest` | анонимный → редирект на login; авторизованный → 200 |
| market-app | `UserScopedCartTest` | корзины alice и bob изолированы по `user_id` |
| payment-service | `PaymentControllerTest`, `BalanceStoreTest` | контроллер + потокобезопасность |
| payment-service | `SecurityIntegrationTest` | без JWT → 401, с JWT → 200, `/actuator/health` всегда 200 |

Все тесты выполняются без Keycloak — auto-configuration ресурс-сервера и OAuth2 client отключены в `application-test.properties`.

---

## Полезные ссылки

- [docs/keycloak-setup.md](docs/keycloak-setup.md) — пошаговая настройка Keycloak
- [Spring Security Reactive](https://docs.spring.io/spring-security/reference/reactive/index.html)
- [Spring Authorization Server](https://docs.spring.io/spring-authorization-server/reference/index.html)
- [Keycloak documentation](https://www.keycloak.org/documentation)
- [OpenAPI Generator](https://openapi-generator.tech/)
```
