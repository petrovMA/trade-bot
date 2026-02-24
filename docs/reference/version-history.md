---
title: История версий Trade Bot
description: Хронология всех изменений проекта — версии, проблемы, решения, затронутые файлы
tags: [reference, changelog, versions]
status: active
related_files: [CLAUDE.md, docs/index.md]
---

# Trade Bot - История версий

## Context

Changelog проекта. Обновляется после каждого изменения кода (см. правило в `CLAUDE.md`).
Текущая версия: v2.1.13. Формат: semver. Каждая запись содержит проблему, решение и затронутые файлы.

## О документе

История изменений и доработок Trade Bot с описанием решённых проблем и добавленных функций.

---

## Версии

### v2.1.13 - Spike: always LIMIT at avgSpikePrice, cancel old orders on redistribute (2026-02-24)

**Статус:** Реализовано

**Проблема 1:**
Spike safety cutoff и timeout отправляли MARKET ордер по `currentPrice`. Если цена уже откатилась ниже средней покупки, MARKET SELL продавал в убыток. Пример: avg BUY 0.03041, safety cutoff MARKET SELL @ 0.02925 → убыток -0.50 USDT.

**Проблема 2:**
`redistributeGridAfterSpike()` обнуляла `orderId` в БД, но не отменяла старые ордера на бирже. Старые ордера оставались live, но untracked → их fill-ы терялись ("not found in DB"), вызывая разбалансировку позиции.

**Решение:**
1. `executeSpikeCounterOrders()`: убран параметр `useMarketPrice`. Всегда используется **LIMIT** с ценой `avgSpikePrice ± offset` (гарантирует прибыль). Ордер заполнится когда цена вернётся к выгодному уровню.
2. `redistributeGridAfterSpike()`: перед обнулением `orderId` — отмена старых ордеров на бирже через `cancelOrder()`.

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.12 - Fix spike aggregation double-buy bug (2026-02-23)

**Статус:** Реализовано

**Проблема:**
`executeSpikeCounterOrders()` отправлял на биржу И аггрегированный ордер (BUY 330 REACT), И 3 отдельных grid-ордера через `redistributeGridAfterSpike()` (ещё 330 REACT). Итого ~660 REACT куплено вместо ~330. Это вызывало разбалансировку: бот тратил все USDT на покупку REACT.

**Решение:**
`redistributeGridAfterSpike()` теперь только обновляет БД (переворачивает side, очищает orderId) **без отправки на биржу**. Аггрегированный ордер уже покрывает всю counter-позицию. Grid-ордера будут размещены на бирже при следующем `synchronizeOrders()`.

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.11 - Grid range change detection on resume (2026-02-21)

**Статус:** Реализовано

**Задача:**
При изменении `trading_range` в settings.json и `/resume` бота, существующие ордера не корректировались — бот работал со старой решёткой.

**Решение:**
При resume в `synchronizeOrders()` добавлена автоматическая проверка диапазона:
1. **Contraction** (сужение): ордера за пределами нового диапазона отменяются на бирже и удаляются из БД
2. **Expansion** (расширение): для новых grid-уровней внутри диапазона создаются ордера с правильным side (BUY ≤ currentPrice, SELL > currentPrice)
3. Существующие ордера внутри нового диапазона не затрагиваются
4. При добавлении >30% новых ордеров автоматически включается `forceSyncEnabled`

Новая функция `buildOrderForGridLevel()` зеркалит логику конструктора `GridOrders` для создания одного ордера на заданном уровне.

**Файлы:**
- `src/main/kotlin/bot/trade/exchanges/AlgorithmGrid.kt` — range-change detection в `synchronizeOrders()`, новая функция `buildOrderForGridLevel()`

---

### v2.1.10 - Grid Suitability: Estimated Earnings + Gate/Extended scan (2026-02-21)

**Статус:** Реализовано

**Задача:**
Добавить расчёт потенциального заработка со вложенного капитала; добавить Gate.io и Extended в сканер.

**Решение:**
- `GridSuitabilityResult` + `GridSuitabilityService`: новые поля `numGridLevels`, `estimatedDailyReturnPct`, `estimatedMonthlyReturnPct`
- Формула: `dailyReturnPct = (fillsPerDay/2) × profitDistancePct / numGridLevels`
- UI: поле ввода капитала ($100 по умолчанию); блок "Estimated Earnings" с daily/monthly/annual; пересчёт при изменении капитала без повторного запроса
- Gate.io scan: прямой HTTP к Gate public API (`/api/v4/spot/tickers` + `/api/v4/spot/candlesticks`) — обход сломанного `getCandlestickBars()` в ClientGate
- Extended (Starknet) scan: использует `getAllPairs()` + `getCandlestickBars()` (оба работают)
- Scan dropdown добавлены: Gate.io, Extended

**Затронутые файлы:**
- `src/main/kotlin/bot/trade/analytics/GridSuitabilityService.kt`
- `src/main/kotlin/bot/trade/rest_controller/MainController.kt`
- `pages/grid-suitability.html`

---

### v2.1.9 - Grid Suitability: Exchange Scanner + link fix (2026-02-21)

**Статус:** Реализовано

**Задача:**
Добавить сканирование биржи для поиска лучших пар для грид-бота; исправить ссылку в main.html.

**Решение:**
- Новый endpoint `POST /grid-suitability-scan`: принимает exchange/quoteCurrency/limit, возвращает `List<GridSuitabilityResult>` отсортированный по score
- Для Binance: использует `marketDataService.getTickers()` (XChange) → сортировка по quoteVolume
- Для ByBit: прямой HTTP запрос к публичному `/v5/market/tickers` API → сортировка по turnover24h
- Последовательный анализ пар (168 свечей = 1 неделя hourly) через Kotlin `mapNotNull`
- UI: таблица результатов с кликом по строке для быстрого детального анализа; показывает время выполнения
- Ссылка на Grid Suitability добавлена в `pages/main.html`
- Локальный URL: `localhost:8080/grid-suitability`

**Затронутые файлы:**
- `src/main/kotlin/bot/trade/rest_controller/MainController.kt`
- `pages/grid-suitability.html`
- `pages/main.html`

---

### v2.1.8 - Grid Suitability Checker (2026-02-20)

**Статус:** Реализовано

**Задача:**
Инструмент для быстрой оценки пары на бирже — насколько она подходит для запуска грид-бота.

**Решение:**
- Новый сервис `GridSuitabilityService` вычисляет метрики из ~500 свечей (1h): дневную волатильность, диапазон цены, объём, расчётное количество заполнений сетки в день
- Оценка пригодности (score 0–100) и лейбл (Excellent/Good/Fair/Poor/Not Suitable)
- Предлагаемые параметры: торговый диапазон, order distance %, profit distance %
- Новые REST-эндпоинты: `POST /grid-suitability` (JSON-ответ), `GET /grid-suitability` (HTML-страница)
- Фронтенд: `pages/grid-suitability.html` — выбор биржи из dropdown (только поддерживаемые), ввод пары вручную

**Затронутые файлы:**
- `src/main/kotlin/bot/trade/analytics/GridSuitabilityService.kt` (новый)
- `src/main/kotlin/bot/trade/rest_controller/MainController.kt`
- `pages/grid-suitability.html` (новый)

---

### v2.1.7 - Fix inflated amount persisting in DB after min notional adjustment (2026-02-20)

**Статус:** Реализовано

**Проблема:**
`adjustAmountForMinNotional` увеличивала BUY amount с ~64 до ~108 REACT для отправки на биржу (чтобы пройти минимум 3 USDT). Но увеличенный amount **сохранялся в БД** вместо оригинального. `createNextOrder()` копировала inflated amount в counter-ордера → grid slot навсегда «раздувался». При восстановлении цены (0.027 → 0.048) ордер в 108 REACT × 0.048 = 5.2 USDT вместо оригинальных 3.1 USDT.

Это также приводило к тому, что бот тратил все USDT за один цикл force sync: 25+ BUY ордеров × 108 REACT × 0.027 = ~73 USDT.

**Решение:**
В обоих местах, где DB update восстанавливает оригинальные `price`/`stopPrice`, теперь также восстанавливается оригинальный `amount`:
1. **`synchronizeOrders()` individual retry** (строка 515): `amount = order.amount`
2. **`fallbackToGridCounterOrders()`** (строка 963): `amount = newOrder.amount`

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.6 - Fix min notional: adjust amount when price is lowered to currentPrice (2026-02-20)

**Статус:** Реализовано

**Проблема:**
После спайка force sync (individual retry) отправлял BUY counter-ордера по `currentPrice` (~0.027), но с оригинальным количеством (~80 REACT). Notional = 80 × 0.027 = 2.16 USDT < 3 USDT (минимум Gate.io). Биржа отклоняла с `OrderAmountUnderMinimumException: Your order size 2.22 USDT is too small. The minimum is 3 USDT`.

**Решение:**
1. **`BotSettingsGrid.Parameters`**: добавлено поле `min_notional_usdt: BigDecimal? = null`. Устанавливается в `settings.json` бота (напр. `3.0` для Gate.io).
2. **`AlgorithmGrid.kt`**: добавлен приватный хелпер `adjustAmountForMinNotional(amount, sendPrice)` — если `amount × sendPrice < minNotionalUsdt`, увеличивает amount до `ceil(minNotional / sendPrice)` с точностью `countOfDigitsAfterDotForAmount`.
3. Хелпер применяется в **`synchronizeOrders()` individual retry** при корректировке BUY цены на currentPrice.
4. Хелпер применяется в **`fallbackToGridCounterOrders()`** при корректировке BUY цены на currentPrice.

**Использование:** добавить в `exchangeBots/<botName>/settings.json` → `parameters` → `"min_notional_usdt": 3`.

**Файлы:** `BotSettingsGrid.kt`, `AlgorithmGrid.kt`

---

### v2.1.5 - Force sync command: bypass safety check on demand (2026-02-20)

**Статус:** Реализовано

**Проблема:**
При большом количестве отсутствующих на бирже ордеров (>30% от общего числа) `synchronizeOrders()` блокировалась safety check с сообщением "⚠️ Sync warning: N orders not found on exchange". Пользователь не мог вручную разрешить синхронизацию без перезапуска бота.

**Решение:**
Добавлена команда `/forcesync <botName>` для принудительной синхронизации:
1. **`BotEvent.kt`**: новый тип `FORCE_SYNC`
2. **`AlgorithmGrid.kt`**: поле `forceSyncEnabled`, обработчик `FORCE_SYNC` события, safety check теперь пропускается при `forceSyncEnabled = true` + сообщение в warning содержит подсказку `/forcesync`
3. **`Commands.kt`**: regex `commandForceSyncTradeBot`
4. **`Communicator.kt`**: функция `forceSyncBot()`, команда обрабатывается в обоих обработчиках (Telegram и REST)
5. **`MainController.kt`**: endpoint `POST /force_sync_bot`
6. **`BotActions.tsx`**: кнопка "⚡ Force Sync" (оранжевая, `btn-warning`)
7. **`bot.service.ts`**: метод `forceSyncBot()`
8. **`global.css`**: стиль `.btn-warning`

**Файлы:** `BotEvent.kt`, `AlgorithmGrid.kt`, `Commands.kt`, `Communicator.kt`, `MainController.kt`, `BotActions.tsx`, `bot.service.ts`, `global.css`

---

### v2.1.4 - Fix spike fallback: per-order resilience + price-band adjustment (2026-02-20)

**Статус:** Реализовано

**Проблема:**
При массивном спайке (30+ sell-ордеров) aggregated counter-BUY отклонялся биржей (цена спайка слишком далеко от текущей). `fallbackToGridCounterOrders()` пытался отправить все counter-BUY одним батчем — при сбое любого ордера весь батч падал. После сбоя `finally`-блок очищал `pendingSpikeOrders`, оставляя 30 DB-записей в состоянии SELL/filled без counter-ордеров ("дыры" в сетке). На фьючерсных биржах (Gate.io perpetual) limit-ордер, цена которого далеко выше рыночной, отклоняется из-за price-band ограничений — это была причина отказа как aggregated, так и fallback ордеров.

**Решение:**
1. `fallbackToGridCounterOrders()` переписан с пакетной отправки на **поордерную** с `try-catch` — сбой одного ордера не останавливает остальные.
2. Для BUY counter-ордеров, где цена grid-слота выше текущей рыночной (`order.price > currentPrice`), отправляется по `currentPrice` вместо цены слота (fills немедленно или в пределах price-band). Оригинальная цена слота восстанавливается в DB после отправки (сохраняется структура сетки).
3. `synchronizeOrders()` batch-отправка обёрнута в `try-catch` с **individual retry** — при сбое батча каждый ордер отправляется по отдельности с той же price-band корректировкой.
4. При сбое отдельных fallback-ордеров DB-записи остаются нетронутыми (SELL/filled), что позволяет `synchronizeOrders` восстановить их при следующем перезапуске бота.

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.3 - Fix spike dedup: duplicate counter-orders during fast spike reversal (2026-02-19)

**Статус:** Реализовано

**Проблема:**
Во время ценового спайка первые 1-2 sell-ордера исполнялись до срабатывания spike detection и получали индивидуальные counter-buy ордера. Эти counter-buy мгновенно исполнялись (цена резко возвращалась). После этого spike aggregation включала эти же sell-слоты в `ordersWithoutCounter` и создавала дублирующие counter-buy через `redistributeGridAfterSpike`.

**Причина:** `createNextOrder` переиспользует тот же DB `id` для counter-ордера (только меняет `orderSide`). Проверка дедупликации содержала guard `existingReversedOrder.id != order.id`, который отклонял совпадение, т.к. id одинаковые — хотя `orderSide == reversedSide` уже гарантирует корректность матчинга.

**Решение:**
Удалён `existingReversedOrder.id != order.id` из трёх мест:
1. WebSocket FILLED handler
2. `executeSpikeCounterOrders()`
3. `fallbackToGridCounterOrders()`

Проверка `orderSide == reversedSide` достаточна для корректного обнаружения уже существующего counter-ордера.

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.2 - Fix duplicate orders not removed on resume/sync (2026-02-17)

**Статус:** Реализовано

**Проблема:**
При resume (восстановлении) бота `synchronizeOrders()` не проверяла дубликаты ордеров по цене. Если в БД уже существовали два ордера с одинаковой ценой и стороной (например, два BUY по 0.02817), оба сохранялись и отправлялись на биржу, приводя к 101 ордеру вместо 100.

**Решение:**
Добавлена дедупликация в начале resume-пути `synchronizeOrders()`: группировка DB ордеров по `(price, side)`, при обнаружении дубликатов — отмена лишних на бирже и удаление из БД. Первый (старший) ордер сохраняется, остальные удаляются.

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.1 - Spike Aggregation: Fix duplicate counter-orders and balance error (2026-02-16)

**Статус:** Реализовано

**Проблема:**
При ценовом спайке WebSocket FILLED handler и Trade trigger обрабатывали одни и те же ордера независимо. WebSocket handler отправлял counter-sell ордера ДО того, как spike detector обнаружил спайк (не вызывал `registerFill()`). Затем Trade trigger обнаруживал spike и буферизировал те же ордера. При timeout:
1. Агрегированный ордер пытался продать больше REACT, чем доступно (часть уже заблокирована counter-sell от WebSocket) → `Not enough balance`
2. Fallback создавал дублирующие counter-ордера → 101 ордер вместо 100

**Решение:**
1. WebSocket FILLED handler теперь вызывает `spikeDetector.registerFill()` — участвует в spike detection
2. Если spike обнаружен/активен в WebSocket handler — ордер буферизируется, counter не создаётся
3. `pendingSpikeOrders` дедуплицируется по `orderId` при добавлении
4. `executeSpikeCounterOrders()` и `fallbackToGridCounterOrders()` проверяют наличие уже отправленных counter-ордеров через `getOrderByPrice()` и исключают их

**Файлы:** `AlgorithmGrid.kt`

---

### v2.1.0 - Extended Exchange Integration (Starknet Perpetual Futures DEX) (2026-02-04)

**Статус:** Реализовано

**Описание:**

Добавлена поддержка Extended Exchange — DEX на Starknet для торговли бессрочными фьючерсами (perpetual futures). Реализация `ClientFutures` интерфейса с REST API и WebSocket стримом.

**Ключевые особенности:**
- REST клиент через OkHttp3 с аутентификацией через `X-Api-Key` header
- WebSocket стрим для trade/orderbook (публичный) и order/position обновлений (приватный)
- Поддержка всех операций: ордера, позиции, баланс, свечи, книга ордеров
- Формат пар: `BTC-USD` (dash-separated, совместим с TradePair regex-парсингом)
- Stark-подпись (starkKey) опциональна, требуется только для создания ордеров

**Новые файлы:**
- `exchange_api/extended/rest/response/ExtendedResponses.kt` — DTO для Extended API
- `exchange_api/extended/rest/client/ExtendedRestApiClient.kt` — REST клиент
- `bot/trade/exchanges/clients/ClientExtended.kt` — имплементация ClientFutures
- `bot/trade/exchanges/clients/stream/StreamExtendedImpl.kt` — WebSocket стрим (OkHttp3)
- `exchangeConfigs/EXTENDED.conf` — конфигурация (API ключ, Stark ключ)

**Изменяемые файлы:**
- `ExchangeEnum.kt` — добавлен EXTENDED enum и factory методы

---

### v2.0.2 - Fix nginx proxy_pass breaking static assets (2026-02-03)

**Проблема:** JS/CSS файлы фронтенда не загружались — вместо JavaScript сервер возвращал index.html (658 байт) с Content-Type text/html. Причина: в конфиге внешнего nginx (Python-проект) `proxy_pass https://$variable/trade-bot/;` при использовании переменной заменяет оригинальный URI на `/trade-bot/` для ВСЕХ запросов (документированное поведение nginx).

**Решение:**
1. Внешний nginx (`prod.conf`): убран URI из proxy_pass (`proxy_pass https://$trade_bot_fe;`), добавлен `rewrite` для API location
2. Внутренний nginx (`nginx-ssl.conf`): заменён `alias` на `root`, вынесен отдельный `location /trade-bot/assets/` для статики
3. `Dockerfile.ssl`: `dist` копируется в `/usr/share/nginx/html/trade-bot/` (для `root` директивы)

**Файлы:** `frontend/nginx/nginx-ssl.conf`, `frontend/nginx/nginx.conf`, `frontend/nginx/Dockerfile.ssl`, `frontend/nginx/Dockerfile`, `docs/GOOGLE_AUTH_SETUP.md`

---

### v2.0.1 - Telegram notification on server startup (2026-02-02)

**Описание:** При старте сервера отправляется Telegram-уведомление о том, что сервер запущен и боты не активны.

**Файлы:** `Communicator.kt`

---

### v2.0.0 - 1inch DEX Support (BSC, Limit Orders) (2026-02-02)

**Статус:** Реализовано

**Описание:**

Добавлена поддержка 1inch DEX агрегатора для grid-бота. Позволяет размещать лимитные ордера на BSC (BNB Chain) через 1inch Limit Order Protocol v4.

**Ключевые особенности:**
- EIP-712 подписание ордеров off-chain с исполнением on-chain
- Автоматическое одобрение (approve) ERC-20 токенов для протокола 1inch
- Polling-based стрим для отслеживания цен и статусов ордеров (WebSocket недоступен на DEX)
- Реестр BSC-токенов с конвертацией символов (BTC -> BTCB) и wei/decimals
- Ордер ID = EIP-712 хеш, отмена через expiry (on-chain cancel стоит газ)

**Новые файлы:**
- `exchangeConfigs/ONEINCH.conf` — конфигурация (API ключ, приватный ключ BSC, адреса токенов)
- `exchange_api/oneinch/rest/response/OneInchResponses.kt` — DTO для 1inch API
- `exchange_api/oneinch/token/BscTokenRegistry.kt` — маппинг символов на BSC-адреса контрактов
- `exchange_api/oneinch/rest/client/OneInchRestApiClient.kt` — REST клиент для api.1inch.dev
- `exchange_api/oneinch/order/LimitOrderBuilder.kt` — EIP-712 построение и подписание ордеров (web3j)
- `exchange_api/oneinch/token/TokenApprovalHelper.kt` — одобрение ERC-20 токенов через BSC RPC
- `bot/trade/exchanges/clients/stream/StreamOneInchPolling.kt` — polling стрим для цен и статусов
- `bot/trade/exchanges/clients/ClientOneInch.kt` — имплементация Client интерфейса

**Изменяемые файлы:**
- `build.gradle.kts` — добавлена зависимость web3j:core:4.10.3
- `ExchangeEnum.kt` — добавлен ONEINCH enum и factory методы

---

### v1.9.1 - Delete Bot Button in BotDetail (2026-02-01)

**Статус:** Реализовано

**Описание:**

Добавлена кнопка "Delete Bot" на странице деталей бота (BotDetail).

**Проблема:**
- При попытке загрузить уже работающего бота появлялось сообщение "Bot already exists", но не было кнопки для удаления бота прямо из интерфейса
- Приходилось вручную отправлять команду `/delete` через Telegram или консоль

**Решение:**
- Добавлена кнопка "Delete Bot" в компонент `BotActions` (рядом с Load, Start, Resume, View JSON)
- Диалог подтверждения перед удалением
- Использует существующий эндпоинт `POST /delete_bot`

**Затронутые файлы:**
- `frontend/src/components/bot/BotActions.tsx` — добавлена кнопка удаления с confirmation dialog

---

### v1.9.0 - Spike Aggregation Mode (2026-01-31)

**Статус:** Реализовано

**Описание:**

Оптимизация Grid-бота для обработки ценовых спайков (резких скачков цены, при которых за 1-5 секунд исполняются сразу несколько ордеров сетки).

**Проблема:**
- При спайке цены 10+ ордеров исполняются одновременно
- Бот мгновенно выставляет обратные ордера на уровнях сетки
- Обратные ордера с лимитом выше рынка исполняются как тейкерные по завышенной цене
- Потеря ~47% потенциальной прибыли (анализ REACT/USDT 30.01.2026: 1.02 vs 1.91 USDT)

**Решение: Spike Aggregation Mode**
- Детекция спайка: N ордеров за T секунд (настраиваемый порог)
- Буферизация исполненных ордеров вместо мгновенного откупа
- Ожидание стабилизации цены (контроль волатильности в скользящем окне)
- Агрегированный откуп одним ордером по стабилизированной цене
- Перестройка grid state в БД после откупа
- Safety cutoff при дрифте цены + таймаут + fallback на стандартную логику

**Конфигурация:** Отдельный файл `exchangeBots/{botName}/spike_aggregation.conf` (HOCON формат)
- Поддержка комментариев для описания 10+ настраиваемых параметров
- Загрузка через Typesafe Config (`readConf()`)
- Если файл отсутствует — фича отключена, поведение не меняется

**Новые файлы:**
- `spike/SpikeConfig.kt` — конфигурация режима (загрузка из .conf)
- `spike/SpikeDetector.kt` — детектор спайков
- `spike/PriceStabilizer.kt` — монитор стабилизации цены
- `spike/SpikeDetectorTest.kt` — unit-тесты детектора
- `spike/PriceStabilizerTest.kt` — unit-тесты стабилизатора
- `exchangeBots/REACT_USDT_GRID/spike_aggregation.conf` — пример конфигурации

**Изменяемые файлы:**
- `AlgorithmGrid.kt` — интеграция spike detection в handle()

**Дизайн-документ:** `docs/SPIKE_AGGREGATION_DESIGN.md`

---

### v1.8.0 - Partial Fill Bug Fix & Error Resilience (2026-01-29)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. Partial Fill обрабатывался как Full Fill — бот падал с "Not enough balance"
- **Проблема:** Gate.io отправляет `UserTrade` на каждое частичное исполнение ордера. `StreamGateImpl` помечал каждый UserTrade как `STATUS.FILLED`, даже если ордер был исполнен частично (например, 3.85 из 92.00 REACT). Бот создавал встречный ордер на полную сумму (92.00) → биржа отклоняла с "Not enough balance" → бот падал
- **Решение:** Добавлена верификация через `client.getOrder()` в WebSocket FILLED handler. Теперь бот проверяет реальный статус ордера на бирже перед созданием встречного ордера:
  - `FILLED` → создаёт встречный ордер
  - `PARTIALLY_FILLED` → пропускает, ждёт полного исполнения
  - Trade trigger через ценовой триггер подхватит полностью исполненный ордер
- **Файлы:** `AlgorithmGrid.kt`

#### 2. Любая ошибка в handle() убивала бот
- **Проблема:** Внутренний `catch` в `Algorithm.run()` ловил только `InterruptedException`. Любое другое исключение (включая `FundsExceededException`) пробрасывалось во внешний catch и останавливало бот полностью
- **Решение:** Добавлена обработка `FundsExceededException` и общих `Exception` внутри основного цикла. Бот логирует ошибку, отправляет уведомление в Telegram и продолжает работать
- **Файлы:** `Algorithm.kt`

---

### v1.7.0 - Grid Analysis Page Fix & Deploy Sync (2026-01-29)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. Страница Grid Analysis пустая на сервере
- **Проблема:** При переходе на `/trade-bot/grid-analysis` nginx отдавал React SPA (у которого нет роута для этого пути) вместо серверной страницы Spring Boot
- **Решение:**
  - Добавлен `location = /trade-bot/grid-analysis` в nginx-ssl.conf (exact match имеет приоритет над `^~`)
  - Проксирует к backend: `proxy_pass http://trade-bot:8080/grid-analysis`
- **Файлы:** `frontend/nginx/nginx-ssl.conf`, `frontend/nginx/nginx.conf`

#### 2. API эндпоинты не доступны через nginx
- **Проблема:** Эндпоинты `/api/trades` и `/api/grid-analytics` имели префикс `/api/` в контроллере, но nginx (`/trade-bot/api/`) стрипает этот префикс при проксировании
- **Решение:** Убран `/api/` префикс: `/api/trades` → `/trades`, `/api/grid-analytics` → `/grid-analytics`
- **Файлы:** `MainController.kt`

#### 3. AJAX вызовы из grid-analysis.html не работали в production
- **Проблема:** HTML страница вызывала `/api/trades`, но в production nginx не имеет location для `/api/`
- **Решение:** Динамическое определение API base path: `/trade-bot/api` в production, пустая строка для локальной разработки
- **Файлы:** `pages/grid-analysis.html`

#### 4. Ссылки на Grid Analysis не соответствовали production пути
- **Проблема:** Ссылки вели на `/grid-analysis` вместо `/trade-bot/grid-analysis`
- **Решение:** Обновлены ссылки в React App и pages/main.html
- **Файлы:** `frontend/src/App.tsx`, `pages/main.html`, `frontend/vite.config.ts`

#### 5. Deploy скрипт не синхронизировал папку pages/
- **Проблема:** `grid-analysis.html` не попадал на сервер, т.к. `images_3_restart_containers.sh` не синхронизировал папку `pages/` (volume-mounted)
- **Решение:** Добавлена функция `sync_pages()` с `rsync` в deploy скрипт
- **Файлы:** `deploy/images_3_restart_containers.sh`

---

### v1.6.0 - Grid Bot Trade Analysis (2026-01-25)

**Статус:** ✅ Завершено

**Добавленные возможности:**

#### 1. REST API для получения истории сделок
- **Функционал:** Эндпоинт `/api/trades` для получения и анализа истории сделок с биржи
- **Параметры фильтрации:**
  - Выбор биржи (Gate.io)
  - Торговая пара (например, REACT_USDT)
  - Диапазон дат (from/to)
  - Диапазон размера ордера (minAmount/maxAmount)
- **Возвращает:** Список сделок + сводку с расчётом прибыли
- **Файлы:** `MainController.kt`, `GateRestApiClient.kt`

#### 2. Метод getMyTrades для Gate.io
- **Функционал:** Получение истории сделок через Gate.io REST API v4 (`/api/v4/spot/my_trades`)
- **Параметры:** symbol, from, to, limit
- **Файлы:** `GateRestApiClient.kt`

#### 3. Веб-страница Grid Bot Analysis
- **URL:** `/grid-analysis`
- **Функционал:**
  - Форма с фильтрами (биржа, пара, даты, размер ордера)
  - Сводка: общее кол-во сделок, средние цены покупки/продажи, объёмы, комиссии, чистая прибыль
  - Таблица сделок с цветовой индикацией Buy/Sell
- **Файлы:** `pages/grid-analysis.html`, `pages/main.html`

---

### v1.5.0 - Gate.io Grid Bot Improvements (2026-01-25)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. Защита от дублирования ботов
- **Проблема:** При повторной отправке команд `load` и `start` создавались дублирующие боты, каждый с отдельным набором ордеров (24 ордера вместо 12)
- **Решение:** Добавлены проверки в `Communicator.kt`:
  - При `load`: если бот уже загружен — отклонить с сообщением
  - При `start`: если бот уже запущен (`isAlive`) — отклонить с сообщением
- **Файлы:** `Communicator.kt`

#### 2. Логика StartBot vs ResumeBot в synchronizeOrders
- **Проблема:** При StartBot отправлялось предупреждение "Sync warning: 100 orders not found on exchange"
- **Решение:** Добавлена детекция fresh start (`orderId == null` для всех ордеров)
  - Fresh start (StartBot): отправить все новые ордера на биржу
  - Resume (ResumeBot): синхронизировать существующие ордера
- **Файлы:** `AlgorithmGrid.kt`

#### 3. Исправление режима Telegram
- **Проблема:** Telegram бот не отвечал на команды
- **Причина:** В `common.conf` был установлен `type = "console"` вместо `type = "telegram"`
- **Решение:** Изменён тип на `type = "telegram"`
- **Файлы:** `common.conf`

---

### v1.4.0 - Gate.io WebSocket & Order Sync (2026-01-25)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. Добавление getUserTrades для Gate.io
- **Проблема:** После отключения `getOrderChanges()` (NotYetImplementedException) не было уведомлений о выполнении ордеров через WebSocket
- **Решение:** Добавлена подписка на `getUserTrades()` в `StreamGateImpl.kt`
  - При выполнении лимитного ордера приходит UserTrade уведомление
  - Конвертируется в Order с status=FILLED для обработки в AlgorithmGrid
- **Файлы:** `StreamGateImpl.kt`

#### 2. Исправление Gate.io stream ошибок
- **Проблема:** ClassCastException в getOrderBook, NotYetImplementedException в getOrderChanges
- **Решение:**
  - Отключена подписка на OrderBook (баг в xchange-stream-gateio)
  - Отключена подписка на OrderChanges (не реализовано в библиотеке)
  - Оставлены trades + добавлены userTrades
- **Файлы:** `StreamGateImpl.kt`

---

### v1.3.0 - StartBot Order Management (2026-01-24)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. StartBot отменяет ордера до проверки баланса
- **Проблема:** При недостаточном балансе ордера уже были отменены
- **Решение:** Изменён порядок операций:
  1. Проверка баланса ПЕРВЫМ
  2. Если баланс недостаточен — вернуть ошибку БЕЗ отмены ордеров
  3. Только при достаточном балансе — отменить ордера и создать новую сетку
- **Файлы:** `Communicator.kt`

#### 2. StartBot должен отменять ордера на бирже
- **Проблема:** При StartBot ордера не отменялись на бирже перед созданием новой сетки
- **Решение:** Добавлен код отмены всех открытых ордеров на бирже перед созданием новой сетки
- **Файлы:** `Communicator.kt`

---

### v1.2.0 - DeleteBot & Resume Fix (2026-01-24)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. DeleteBot зависает
- **Проблема:** Команда delete бота зависала бесконечно
- **Причина:** `AlgorithmGrid.handle()` не обрабатывал `BotEvent.Type.INTERRUPT`
- **Решение:**
  - Добавлена обработка INTERRUPT в AlgorithmGrid
  - Добавлен timeout в `join()` (30s + 5s force interrupt)
- **Файлы:** `AlgorithmGrid.kt`, `Communicator.kt`

#### 2. ResumeBot создаёт дубликаты ордеров
- **Проблема:** При ResumeBot создавались новые ордера вместо синхронизации существующих (130 вместо 100)
- **Причина:** NPE в `ClientGate.getOpenOrders()` на null `originalAmount`/`cumulativeAmount`
- **Решение:**
  - Добавлены null-проверки в ClientGate.kt
  - Добавлена защита в synchronizeOrders: если >30% ордеров "отсутствуют" — не создавать новые
- **Файлы:** `ClientGate.kt`, `AlgorithmGrid.kt`

---

### v1.1.0 - Nginx DNS & Deployment (2026-01-24)

**Статус:** ✅ Завершено

**Решённые проблемы:**

#### 1. 502 ошибки после перезапуска сервера
- **Проблема:** Frontend возвращал 502 Bad Gateway после перезапуска Docker контейнеров
- **Причина:** Nginx кэшировал DNS и не обновлял IP адреса контейнеров
- **Решение:**
  - Добавлен динамический DNS resolver в nginx config
  - Добавлена функция `reload_nginx()` в deploy скрипты
  - Добавлена функция `wait_for_backend_healthy()`
- **Файлы:** `frontend/nginx/conf.d/prod.conf`, `deploy/images_3_restart_containers.sh`

---

## Формат версионирования

Используется [Semantic Versioning](https://semver.org/):

- **MAJOR** (x.0.0): Несовместимые изменения API или архитектуры
- **MINOR** (1.x.0): Новые возможности, исправления багов
- **PATCH** (1.0.x): Мелкие исправления

---

## Ключевые файлы

| Файл | Описание |
|------|----------|
| `Communicator.kt` | Оркестрация ботов, обработка команд |
| `AlgorithmGrid.kt` | Grid trading алгоритм |
| `ClientGate.kt` | Gate.io REST API клиент (XChange) |
| `GateRestApiClient.kt` | Gate.io REST API v4 клиент (native) |
| `StreamGateImpl.kt` | Gate.io WebSocket stream |
| `MainController.kt` | REST API контроллер |
| `pages/grid-analysis.html` | UI анализа сделок Grid Bot |
| `common.conf` | Конфигурация бота (Telegram/Console) |
| `docs/SPIKE_AGGREGATION_DESIGN.md` | Дизайн Spike Aggregation Mode |

---

## Обновления документа

- **2026-01-31:** Добавлен v1.9.0 (Spike Aggregation Mode — проектирование)
- **2026-01-25:** Создан документ, добавлены v1.1.0 - v1.5.0

---

_Документ обновляется после каждой итерации разработки._