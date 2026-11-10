# market-data-service

Сервис рыночных данных: котировки (текущие и исторические), дивиденды и новости
по бумагам из портфелей. Работает отдельно от portfolio-service, предоставляет
данные остальному проекту через REST и публикует события об изменении цены.

## Источники данных

| Источник | Что даёт | Токен | Приоритет |
|---|---|---|---|
| T-Invest API (Т-Банк), REST-прокси `invest-public-api.tbank.ru/rest` | инструменты, последние цены, свечи, дивиденды | да, только чтение | первый (если задан `T_INVEST_TOKEN`) |
| MOEX ISS (`iss.moex.com`) | котировки, справка по бумаге, дневные свечи | нет | второй, и основной без токена T-Invest |
| smart-lab.ru (парсинг HTML) | календарь и история дивидендов | нет | fallback для дивидендов |
| RSS-ленты (по умолчанию smart-lab.ru «новости акций», interfax.ru, lenta.ru) | новости по бумагам | нет | единственный для новостей |

Источники опрашиваются по порядку; недоступный источник переключается на
следующий, а котировки — на последние сохранённые в БД (см. «Отказоустойчивость»).

## Выбор схемы БД

Общая база `portfolio_db` (PostgreSQL из docker-compose), **своя схема
`market_data`**:

- сервис владеет только своими таблицами `market_data.price_quotes` и
  `market_data.tracked_tickers` — миграции Flyway выполняются в схеме
  `market_data` (`spring.flyway.schemas`), история миграций лежит отдельно от
  `public`, где живёт portfolio-service;
- из таблиц portfolio-service читается одно: `public.position.ticker` — список
  бумаг для обновления котировок (только чтение, запрос защищён проверкой
  наличия таблицы: если миграции portfolio-service ещё не выполнялись,
  источник возвращает пустой список);
- общий инстанс PostgreSQL оправдан объёмом данных (котировки — маленькие
  строки) и желанием запускать проект одной командой `docker compose up`.

Почему не отдельная БД: дублировать контейнер ради одной таблицы нерационально,
а изоляция на уровне схемы закрывает риск конфликтов имён и миграций.

## Расписание обновления

Spring `@Scheduled` (кварц не понадобился):

- интервал — `market-data.refresh-interval-ms`, по умолчанию 5 минут;
- обновление выполняется только внутри окна торгов
  (`market-data.trading-hours`: 10:00–18:35 Europe/Moscow, пн–пт);
  выключается флагом `market-data.trading-hours.enforced=false`;
- список бумаг — объединение конфига `market-data.tickers`, таблицы
  `tracked_tickers` (POST /api/tickers) и тикеров из `public.position`;
- каждая котировка добавляется в `price_quotes` (append-only, «последняя» —
  строка с максимальным `quoted_at`).

## Отказоустойчивость

Resilience4j (core-библиотеки, без Spring-интеграции), вызов каждого источника:

1. **Retry** — до `market-data.resilience.retry.max-attempts` попыток
   (по умолчанию 3) с экспоненциальным backoff от `wait-duration-ms`;
2. **Circuit breaker** — отдельный на каждый источник (t-invest, moex-iss,
   smart-lab, news-rss): при `failure-rate-threshold-percent` неудач в окне
   `sliding-window-size` (минимум `minimum-number-of-calls` вызовов) открывается
   и не даёт недоступному API развалить сервис; состояние логируется;
3. **Fallback** — если доступен не один источник, `/api/quotes` отдаёт последнюю
   сохранённую котировку с флагом `cached: true`; эндпоинт дивидендов/новостей
   при недоступности всех источников отвечает 503 (сервис не падает).

Токен T-Invest читается из переменной окружения `T_INVEST_TOKEN` (секреты не
коммитятся); без токена T-Invest-провайдер просто отключён. Обратите внимание:
корневой сертификат НУЦ Минцифры, которым выпускается `*.tbank.ru`, нужно
добавить в truststore JVM, иначе вызовы упадут с `SSLHandshakeException`
(подробности — в NOTES.md).

## Асинхронная публикация событий

После сохранения котировки, если изменение к прошлой цене превысило
`market-data.events.change-threshold-percent` (по умолчанию 0.5 %),
сервис асинхронно отправляет POST на
`market-data.events.webhook-url` (REST-вебхук telegram-bot-service):

```json
{
  "event": "PRICE_CHANGED",
  "ticker": "SBER",
  "previousPrice": 300.10,
  "newPrice": 305.50,
  "changePercent": 1.7994,
  "currency": "RUB",
  "quotedAt": "2026-11-10T15:00:00Z",
  "source": "t-invest"
}
```

Если адрес не задан — публикация отключена; ошибки доставки только логируются.
Вместо вебхука можно поднять Kafka — интерфейс `PriceEventPublisher` это
позволяет, но в docker-compose Kafka пока нет, поэтому выбран вебхук.

## Эндпоинты

| Метод и путь | Описание |
|---|---|
| `GET /api/quotes?tickers=SBER,GAZP` | котировки; без параметра — по всем отслеживаемым |
| `GET /api/quotes/{ticker}` | одна котировка (404, если нет нигде) |
| `GET /api/quotes/{ticker}/history?from=&to=` | дневные свечи (по умолчанию 30 дней) |
| `GET /api/tickers` | отслеживаемые тикеры |
| `POST /api/tickers` `{"tickers":["LKOH"]}` | добавить тикеры (идемпотентно) |
| `DELETE /api/tickers/{ticker}` | убрать из отслеживаемых |
| `POST /api/refresh` | вручную обновить котировки сейчас |
| `GET /api/dividends/{ticker}` | предстоящие и прошедшие выплаты (`source` показывает источник) |
| `GET /api/news?tickers=SBER&limit=20` | новости из RSS по бумагам |
| `GET /actuator/health` | здоровье сервиса |

Формат котировки: `{ticker, price, currency, quotedAt, source, cached}` —
`cached=true` означает, что внешние API были недоступны и отданы сохранённые
данные.

## Конфигурация

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` | PostgreSQL | `jdbc:postgresql://localhost:5432/portfolio_db`, `portfolio`/`portfolio` |
| `T_INVEST_TOKEN` | токен T-Invest API (только чтение) | пусто — T-Invest отключён |
| `MARKET_DATA_WEBHOOK_URL` | адрес вебхука событий | пусто — публикация выключена |

Остальные настройки (интервал, окно торгов, ленты новостей, параметры
retry/circuit breaker) — в `src/main/resources/application.yaml`.

## Запуск и проверка

```bash
docker compose up -d postgres
mvn -pl market-data-service -am spring-boot:run
# в другом окне:
curl "http://localhost:8081/api/quotes?tickers=SBER,GAZP"
curl "http://localhost:8081/api/dividends/SBER"
curl "http://localhost:8081/api/news?tickers=SBER"
```

Интеграционные тесты: `mvn -pl market-data-service -am test` — Testcontainers
поднимает настоящую PostgreSQL, WireMock подменяет T-Invest/MOEX/smart-lab/RSS
и вебхук; сценарии покрывают сохранение котировок, fallback при обрыве API,
открытие circuit breaker, дивиденды из обоих источников и новости.
