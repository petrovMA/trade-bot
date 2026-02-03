# Spike Aggregation Mode - Дизайн-документ

## Дата: 2026-01-31
## Статус: Проектирование
## Версия: v1.9.0

---

## 1. Проблема

### 1.1 Описание

При резком скачке цены (спайк/фитиль) за короткий промежуток времени исполняются сразу несколько ордеров сетки. Текущая логика `AlgorithmGrid` мгновенно выставляет обратные ордера на тех же уровнях сетки. Это приводит к неэффективному откупу:

- Обратные ордера с лимитом **выше текущей рыночной цены** исполняются мгновенно как **тейкерные** по ещё завышенной цене (в момент спайка)
- Через 10-30 секунд цена стабилизируется значительно ниже (при спайке вверх) или выше (при спайке вниз)
- Потенциальная прибыль теряется из-за преждевременного откупа

### 1.2 Реальный кейс: REACT/USDT, 30.01.2026, 07:56:12

**Спайк вверх** — 10 sell-ордеров исполнились за 1 секунду:

```![img.png](img.png)
Sell 0.03461 × 91.09  = 3.15 USDT
Sell 0.03496 × 90.19  = 3.15 USDT
Sell 0.03531 × 89.29  = 3.15 USDT
Sell 0.03566 × 88.41  = 3.15 USDT
Sell 0.03602 × 87.53  = 3.15 USDT
Sell 0.03638 × 86.67  = 3.15 USDT
Sell 0.03674 × 85.81  = 3.15 USDT
Sell 0.03711 × 84.96  = 3.15 USDT
Sell 0.03748 × 84.12  = 3.15 USDT
Sell 0.03785 × 83.28  = 3.15 USDT
─────────────────────────────────────
Итого: ~871 токенов на ~31.53 USDT
Средняя цена продажи: 0.03618 USDT
```

**Текущее поведение бота:**
- Через 3 секунды (7:56:15) выставил 10 обратных BUY-ордеров на уровнях сетки
- 6-8 верхних ордеров (лимит 0.03541-0.03719) исполнились мгновенно как тейкер по ~0.035
- Подтверждение: ордер на 0.03487 заполнился по 0.03506 (тейкерный fill)
- Приблизительная стоимость откупа: **~30.51 USDT**
- **Профит: ~1.02 USDT**

**Оптимальное поведение (ожидание стабилизации):**
- Цена стабилизировалась на ~0.0340 через 15-30 секунд
- Стоимость откупа всего объёма по 0.0340: **~29.62 USDT**
- **Профит: ~1.91 USDT (+87% к текущему)**

### 1.3 Симметричный кейс (спайк вниз)

При резком падении цены множество BUY-ордеров исполняются одновременно. Бот сразу выставляет SELL-ордера на уровнях сетки, многие из которых ниже стабилизированной цены. Агрегированная продажа по стабилизированной цене дала бы больший профит.

---

## 2. Текущая логика (как есть)

### 2.1 Trade Trigger (`AlgorithmGrid.kt:101-159`)

```
Trade msg приходит → prevPrice vs currentPrice
  → getInRangeOrders() находит ордера в диапазоне цены
  → Для каждого: client.getOrder() верифицирует STATUS.FILLED
  → createNextOrder() создаёт обратный ордер на ТОМ ЖЕ уровне сетки
  → sendOrders() отправляет ВСЕ обратные ордера пачкой на биржу
  → updateOrdersById() обновляет БД
```

### 2.2 WebSocket FILLED Trigger (`AlgorithmGrid.kt:172-239`)

```
Order msg с STATUS.FILLED приходит
  → client.getOrder() верифицирует (защита от partial fill)
  → Проверка дубликатов (Trade trigger мог уже обработать)
  → createNextOrder() → sendOrders() → updateOrdersById()
```

### 2.3 Ключевая проблема в коде

В `createNextOrder()` (строка 502-512) обратный ордер создаётся на **том же ценовом уровне**:
```kotlin
fun createNextOrder(prevOrder: ActiveOrder, side: SIDE): ActiveOrder = ActiveOrder(
    price = prevOrder.price!!,       // тот же buy-уровень
    stopPrice = prevOrder.stopPrice!!, // тот же sell-уровень
    orderSide = side,                // перевёрнутая сторона
    ...
)
```

В `sendOrders()` (строка 406-418) для LONG + BUY → лимит = `price`. Если `price` (например, 0.03647) выше текущей рыночной цены (0.0345), ордер исполняется мгновенно как тейкер по ~0.035 — дороже, чем если бы подождали.

---

## 3. Проектируемое решение: Spike Aggregation Mode

### 3.1 Общая идея

Добавить в `AlgorithmGrid` детектор спайков, который:
1. Определяет, что за короткое время исполнилось много ордеров (спайк)
2. Вместо мгновенных обратных ордеров **буферизирует** исполненные ордера
3. Ожидает стабилизации цены
4. Выставляет **один агрегированный ордер** по стабилизированной цене
5. После исполнения агрегированного ордера **перестраивает** grid state в БД

### 3.2 Компоненты

```
┌─────────────────────────────────────────────────────────┐
│                    AlgorithmGrid                        │
│                                                         │
│  ┌─────────────┐    ┌──────────────────┐                │
│  │ Trade/Order │───>│ SpikeDetector    │                │
│  │ triggers    │    │                  │                │
│  └─────────────┘    │ - filledOrders[] │                │
│                     │ - firstFillTime  │                │
│                     │ - isActive       │                │
│                     └────────┬─────────┘                │
│                               │                         │
│                    spike?     │  no spike?              │
│                    ┌──────────┴──────────┐              │
│                    ▼                     ▼              │
│         ┌──────────────────┐  ┌─────────────────┐      │
│         │ SpikeBuffer      │  │ createNextOrder  │      │
│         │                  │  │ (текущая логика) │      │
│         │ - pendingOrders  │  └─────────────────┘      │
│         │ - totalAmount    │                            │
│         │ - avgSellPrice   │                            │
│         └────────┬─────────┘                            │
│                  │                                      │
│                  ▼                                      │
│         ┌──────────────────┐                            │
│         │ StabilizationMon │                            │
│         │                  │                            │
│         │ - priceHistory[] │                            │
│         │ - isStable()     │                            │
│         └────────┬─────────┘                            │
│                  │                                      │
│                  ▼                                      │
│         ┌──────────────────┐                            │
│         │ AggregatedOrder  │                            │
│         │                  │                            │
│         │ - execute()      │                            │
│         │ - redistributeGr │                            │
│         └──────────────────┘                            │
└─────────────────────────────────────────────────────────┘
```

---

## 4. Детальный дизайн

### 4.1 Новый класс: `SpikeDetector`

**Файл:** `src/main/kotlin/bot/trade/exchanges/spike/SpikeDetector.kt`

```kotlin
package bot.trade.exchanges.spike

import bot.trade.database.data.entities.ActiveOrder
import java.math.BigDecimal

/**
 * Детектирует спайки — ситуации, когда за короткое время
 * исполняется аномально много ордеров одного направления.
 */
class SpikeDetector(private val config: SpikeConfig) {

    private val recentFills = mutableListOf<TimestampedFill>()
    private var spikeStartTime: Long? = null

    data class TimestampedFill(
        val order: ActiveOrder,
        val fillPrice: BigDecimal,
        val fillTime: Long  // System.currentTimeMillis()
    )

    /**
     * Регистрирует исполненный ордер.
     * @return true если обнаружен спайк (накопилось >= threshold за timeWindow)
     */
    fun registerFill(order: ActiveOrder, fillPrice: BigDecimal): Boolean {
        val now = System.currentTimeMillis()

        // Очищаем старые fills за пределами окна
        recentFills.removeAll { now - it.fillTime > config.timeWindowMs }

        recentFills.add(TimestampedFill(order, fillPrice, now))

        // Проверяем: все fills одного направления (все SELL или все BUY)?
        val sameSideFills = recentFills.filter { it.order.orderSide == order.orderSide }

        if (sameSideFills.size >= config.threshold) {
            if (spikeStartTime == null) {
                spikeStartTime = sameSideFills.first().fillTime
            }
            return true
        }
        return false
    }

    /**
     * Возвращает буферизированные ордера текущего спайка.
     */
    fun getBufferedFills(): List<TimestampedFill> = recentFills.toList()

    /**
     * Суммарное количество токенов для откупа.
     */
    fun getTotalAmount(): BigDecimal =
        recentFills.mapNotNull { it.order.amount }.fold(BigDecimal.ZERO, BigDecimal::add)

    /**
     * Средневзвешенная цена исполненных ордеров.
     */
    fun getWeightedAvgPrice(): BigDecimal {
        val totalValue = recentFills.sumOf {
            (it.fillPrice ?: BigDecimal.ZERO) * (it.order.amount ?: BigDecimal.ZERO)
        }
        val totalAmount = getTotalAmount()
        return if (totalAmount > BigDecimal.ZERO) totalValue / totalAmount
        else BigDecimal.ZERO
    }

    /**
     * Сбрасывает состояние после обработки спайка.
     */
    fun reset() {
        recentFills.clear()
        spikeStartTime = null
    }

    fun isActive(): Boolean = spikeStartTime != null

    fun getSpikeStartTime(): Long? = spikeStartTime
}
```

### 4.2 Новый класс: `PriceStabilizer`

**Файл:** `src/main/kotlin/bot/trade/exchanges/spike/PriceStabilizer.kt`

```kotlin
package bot.trade.exchanges.spike

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Отслеживает стабилизацию цены после спайка.
 * Цена считается стабильной, когда отклонение за последние N замеров
 * не превышает заданного порога.
 */
class PriceStabilizer(private val config: SpikeConfig) {

    private val priceWindow = mutableListOf<TimestampedPrice>()

    data class TimestampedPrice(
        val price: BigDecimal,
        val time: Long
    )

    /**
     * Регистрирует новую цену (вызывается на каждый Trade).
     */
    fun recordPrice(price: BigDecimal) {
        val now = System.currentTimeMillis()
        priceWindow.add(TimestampedPrice(price, now))

        // Сохраняем только цены за последние stabilizationWindowMs
        priceWindow.removeAll { now - it.time > config.stabilizationWindowMs }
    }

    /**
     * Проверяет, стабилизировалась ли цена.
     * Условия:
     * 1. Прошло минимум stabilizationDelayMs с начала мониторинга
     * 2. Разброс цен за последние stabilizationWindowMs < tolerance%
     */
    fun isStable(sinceTime: Long): Boolean {
        val now = System.currentTimeMillis()

        // Условие 1: минимальная задержка
        if (now - sinceTime < config.stabilizationDelayMs) return false

        // Условие 2: достаточно данных
        if (priceWindow.size < config.minPricePoints) return false

        // Условие 3: ценовой разброс в пределах допуска
        val recentPrices = priceWindow.map { it.price }
        val maxPrice = recentPrices.max()
        val minPrice = recentPrices.min()

        if (minPrice <= BigDecimal.ZERO) return false

        val volatility = (maxPrice - minPrice)
            .divide(minPrice, 8, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))

        return volatility <= config.stabilizationTolerancePercent
    }

    /**
     * Возвращает текущую медианную цену (для размещения ордера).
     */
    fun getMedianPrice(): BigDecimal {
        if (priceWindow.isEmpty()) return BigDecimal.ZERO
        val sorted = priceWindow.map { it.price }.sorted()
        return sorted[sorted.size / 2]
    }

    /**
     * Максимальное время ожидания превышено?
     */
    fun isTimedOut(sinceTime: Long): Boolean {
        return System.currentTimeMillis() - sinceTime > config.maxWaitTimeMs
    }

    fun reset() {
        priceWindow.clear()
    }
}
```

### 4.3 Конфигурация: `SpikeConfig`

**Файл:** `src/main/kotlin/bot/trade/exchanges/spike/SpikeConfig.kt`

Конфигурация загружается из **отдельного .conf файла** (HOCON формат) через Typesafe Config:
- **Расположение:** `exchangeBots/{botName}/spike_aggregation.conf`
- **Преимущества:** поддержка комментариев, duration-литералы (`10s`, `120s`), не затрагивает существующий `settings.json`
- **Если файл отсутствует:** фича отключена, поведение бота не меняется

```kotlin
data class SpikeConfig(
    val enabled: Boolean = false,
    val threshold: Int = 3,
    val timeWindowMs: Long = 10_000,
    val stabilizationDelayMs: Long = 15_000,
    val stabilizationWindowMs: Long = 10_000,
    val stabilizationTolerancePercent: BigDecimal = BigDecimal("0.3"),
    val minPricePoints: Int = 5,
    val maxWaitTimeMs: Long = 120_000,
    val maxPriceDriftPercent: BigDecimal = BigDecimal("3.0"),
    val aggregateOrderType: String = "LIMIT",
    val limitPriceOffsetPercent: BigDecimal = BigDecimal("0.1")
) {
    companion object {
        fun disabled() = SpikeConfig(enabled = false)
        fun fromConfig(config: Config): SpikeConfig = ... // loads from spike_aggregation block
    }
}
```

Загрузка в `AlgorithmGrid`:
```kotlin
private fun loadSpikeConfig(): SpikeConfig = try {
    val config = readConf("$path/spike_aggregation.conf")
    if (config != null) SpikeConfig.fromConfig(config) else SpikeConfig.disabled()
} catch (e: Exception) {
    SpikeConfig.disabled()
}
```

### 4.5 Изменения в `AlgorithmGrid`

#### 4.5.1 Новые поля класса

```kotlin
class AlgorithmGrid(...) : Algorithm(...) {
    // ... существующие поля ...

    // Spike Aggregation — loaded from $path/spike_aggregation.conf
    private val spikeConfig: SpikeConfig = loadSpikeConfig()
    private val spikeDetector = SpikeDetector(spikeConfig)
    private val priceStabilizer = PriceStabilizer(spikeConfig)
    private val pendingSpikeOrders = mutableListOf<ActiveOrder>()
    private var spikeSide: SIDE? = null  // сторона спайка (SELL при спайке вверх)
}
```

#### 4.5.2 Модификация Trade trigger (`handle()`, строки 101-159)

**Было:**
```kotlin
TYPE.LIMIT -> {
    val orders = getInRangeOrders(...)
    if (orders.isNotEmpty()) {
        val verifiedOrders = orders.filter { /* verify on exchange */ }
        val newOrders = verifiedOrders.map { createNextOrder(it, it.orderSide!!.reverse()) }
        val exchangeOrders = sendOrders(newOrders)
        activeOrdersService.updateOrdersById(exchangeOrders)
    }
}
```

**Станет:**
```kotlin
TYPE.LIMIT -> {
    val orders = getInRangeOrders(...)
    if (orders.isNotEmpty()) {
        val verifiedOrders = orders.filter { /* verify on exchange */ }
        if (verifiedOrders.isNotEmpty()) {
            if (spikeConfig.enabled) {
                handleWithSpikeDetection(verifiedOrders)
            } else {
                // Текущая логика без изменений
                val newOrders = verifiedOrders.map { createNextOrder(it, it.orderSide!!.reverse()) }
                val exchangeOrders = sendOrders(newOrders)
                activeOrdersService.updateOrdersById(exchangeOrders)
            }
        }
    }

    // Проверка стабилизации цены (при активном спайке)
    if (spikeDetector.isActive()) {
        priceStabilizer.recordPrice(currentPrice)
        checkSpikeResolution()
    }
}
```

#### 4.5.3 Новый метод: `handleWithSpikeDetection()`

```kotlin
private fun handleWithSpikeDetection(verifiedOrders: List<ActiveOrder>) {
    val threadId = Thread.currentThread().name

    for (order in verifiedOrders) {
        val isSpike = spikeDetector.registerFill(order, currentPrice)

        if (isSpike && !spikeDetector.isActive()) {
            // Спайк только что обнаружен — буферизируем ВСЕ предыдущие fills
            log("[$threadId] SPIKE DETECTED: ${spikeDetector.getBufferedFills().size} orders filled within ${spikeConfig.timeWindowMs}ms")
            sendMessage("Spike detected: ${spikeDetector.getBufferedFills().size} orders. Waiting for price stabilization...", false)
            spikeSide = order.orderSide
        }
    }

    if (spikeDetector.isActive()) {
        // Режим спайка: буферизируем ордера, НЕ отправляем обратные
        pendingSpikeOrders.addAll(verifiedOrders)
        log("[$threadId] Spike buffer: ${pendingSpikeOrders.size} orders pending, total amount: ${spikeDetector.getTotalAmount()}")
    } else {
        // Обычный режим: текущая логика
        val newOrders = verifiedOrders.map { createNextOrder(it, it.orderSide!!.reverse()) }
        val exchangeOrders = sendOrders(newOrders)
        activeOrdersService.updateOrdersById(exchangeOrders)
    }
}
```

#### 4.5.4 Новый метод: `checkSpikeResolution()`

```kotlin
private fun checkSpikeResolution() {
    val threadId = Thread.currentThread().name
    val spikeStart = spikeDetector.getSpikeStartTime() ?: return

    // 1. Safety cutoff: цена ушла дальше в сторону спайка
    val avgPrice = spikeDetector.getWeightedAvgPrice()
    val driftPercent = ((currentPrice - avgPrice).abs() / avgPrice) * BigDecimal(100)
    val isDriftExceeded = when (spikeSide) {
        // Спайк вверх (sells filled): если цена ПРОДОЛЖАЕТ расти — cutoff
        SIDE.SELL -> currentPrice > avgPrice &&
            driftPercent > spikeConfig.maxPriceDriftPercent
        // Спайк вниз (buys filled): если цена ПРОДОЛЖАЕТ падать — cutoff
        SIDE.BUY -> currentPrice < avgPrice &&
            driftPercent > spikeConfig.maxPriceDriftPercent
        else -> false
    }

    if (isDriftExceeded) {
        log("[$threadId] Spike SAFETY CUTOFF: price drifted ${driftPercent}% from avg ${avgPrice}")
        sendMessage("Spike safety cutoff! Price drift exceeded ${spikeConfig.maxPriceDriftPercent}%. Executing immediate counter-orders.", false)
        executeSpikeCounterOrders(useMarketPrice = true)
        return
    }

    // 2. Таймаут: слишком долго ждём
    if (priceStabilizer.isTimedOut(spikeStart)) {
        log("[$threadId] Spike TIMEOUT: waited ${spikeConfig.maxWaitTimeMs}ms")
        sendMessage("Spike timeout reached. Executing counter-orders at current price.", false)
        executeSpikeCounterOrders(useMarketPrice = true)
        return
    }

    // 3. Цена стабилизировалась — исполняем агрегированный ордер
    if (priceStabilizer.isStable(spikeStart)) {
        val medianPrice = priceStabilizer.getMedianPrice()
        log("[$threadId] Spike STABILIZED: median price ${medianPrice}")
        sendMessage("Price stabilized at ${medianPrice}. Executing aggregated counter-order.", false)
        executeSpikeCounterOrders(useMarketPrice = false)
        return
    }
}
```

#### 4.5.5 Новый метод: `executeSpikeCounterOrders()`

Это ключевой метод, выполняющий агрегированный откуп и перестройку сетки:

```kotlin
private fun executeSpikeCounterOrders(useMarketPrice: Boolean) {
    val threadId = Thread.currentThread().name
    val reversedSide = spikeSide!!.reverse()

    // 1. Рассчитать агрегированный объём
    val totalAmount = pendingSpikeOrders
        .mapNotNull { it.amount }
        .fold(BigDecimal.ZERO, BigDecimal::add)

    // 2. Определить цену
    val orderPrice = if (useMarketPrice || spikeConfig.aggregateOrderType == "MARKET") {
        currentPrice
    } else {
        val median = priceStabilizer.getMedianPrice()
        val offset = median.percent(spikeConfig.limitPriceOffsetPercent)
        when (reversedSide) {
            SIDE.BUY -> median - offset   // покупаем чуть ниже медианы
            SIDE.SELL -> median + offset   // продаём чуть выше медианы
            else -> median
        }
    }

    log("[$threadId] Spike aggregated order: $reversedSide $totalAmount @ $orderPrice")

    // 3. Отправить агрегированный ордер
    val orderType = if (useMarketPrice) TYPE.MARKET else TYPE.LIMIT
    try {
        val result = sendOrder(
            price = orderPrice,
            amount = totalAmount,
            orderSide = reversedSide,
            orderType = orderType
        )
        log("[$threadId] Spike aggregated order sent: orderId=${result.orderId}, status=${result.status}")

        // 4. Перестроить grid state в БД
        redistributeGridAfterSpike(pendingSpikeOrders, reversedSide)

        val avgSellPrice = spikeDetector.getWeightedAvgPrice()
        val profit = when (reversedSide) {
            SIDE.BUY -> (avgSellPrice - orderPrice) * totalAmount
            SIDE.SELL -> (orderPrice - avgSellPrice) * totalAmount
            else -> BigDecimal.ZERO
        }
        sendMessage(
            "Spike resolved: ${reversedSide} ${totalAmount} @ ${orderPrice} " +
            "(avg spike price: ${avgSellPrice}, est. profit: ${profit.setScale(4)} USDT)", false
        )

    } catch (e: Exception) {
        log("[$threadId] Spike aggregated order FAILED: ${e.message}")
        log("[$threadId] Falling back to standard grid counter-orders")
        sendMessage("Spike aggregated order failed: ${e.message}. Falling back to grid orders.", false)

        // Fallback: стандартные обратные ордера по уровням сетки
        fallbackToGridCounterOrders()
    } finally {
        // 5. Сброс состояния
        spikeDetector.reset()
        priceStabilizer.reset()
        pendingSpikeOrders.clear()
        spikeSide = null
    }
}
```

#### 4.5.6 Новый метод: `redistributeGridAfterSpike()`

После агрегированного откупа нужно корректно восстановить состояние сетки:

```kotlin
private fun redistributeGridAfterSpike(
    filledOrders: List<ActiveOrder>,
    newSide: SIDE
) {
    val threadId = Thread.currentThread().name

    // Для каждого уровня, который был исполнен в спайке,
    // создаём ордер с обратной стороной.
    // Токены уже куплены/проданы агрегированно, поэтому
    // выставляем ТОЛЬКО limit-ордера для будущих циклов.

    val newOrders = filledOrders.map { order ->
        createNextOrder(order, newSide)
    }

    log("[$threadId] Redistributing grid: ${newOrders.size} orders, side=$newSide")

    val exchangeOrders = sendOrders(newOrders)
    activeOrdersService.updateOrdersById(exchangeOrders)

    log("[$threadId] Grid redistributed: ${exchangeOrders.size} orders placed")
}
```

#### 4.5.7 Fallback метод

```kotlin
private fun fallbackToGridCounterOrders() {
    val newOrders = pendingSpikeOrders.map {
        createNextOrder(it, it.orderSide!!.reverse())
    }
    val exchangeOrders = sendOrders(newOrders)
    activeOrdersService.updateOrdersById(exchangeOrders)
}
```

#### 4.5.8 Модификация WebSocket FILLED trigger

В обработчике `is Order -> STATUS.FILLED` (строки 172-239) добавить проверку:

```kotlin
STATUS.FILLED -> {
    if (msg.type == TYPE.LIMIT) {
        // ... существующая верификация ...

        // Если спайк активен — пропустить, Trade trigger уже буферизирует
        if (spikeDetector.isActive()) {
            log("[$threadId] WebSocket FILLED: spike active, skipping (handled by Trade trigger)")
            return
        }

        // ... остальная существующая логика ...
    }
}
```

---

## 5. Формат конфигурации (.conf / HOCON)

Конфигурация Spike Aggregation хранится в **отдельном файле** `exchangeBots/{botName}/spike_aggregation.conf`.

Пример `exchangeBots/REACT_USDT_GRID/spike_aggregation.conf`:

```hocon
# Spike Aggregation Mode
# Detects rapid multi-fill events during price spikes and aggregates
# counter-orders at the stabilized price instead of individual grid-level orders.

spike_aggregation {
    enabled = true
    threshold = 3               # min orders filled to trigger
    time_window = 10s           # time window for detection
    stabilization_delay = 15s   # min wait before checking stability
    stabilization_window = 10s  # price observation window
    stabilization_tolerance_percent = 0.3
    min_price_points = 5
    max_wait_time = 120s        # max wait, then force execute
    max_price_drift_percent = 3.0
    aggregate_order_type = "LIMIT"
    limit_price_offset_percent = 0.1
}
```

**Преимущества .conf формата:**
- Комментарии для описания каждого параметра
- Duration-литералы (`10s`, `120s` вместо `10000`, `120000`)
- Не затрагивает существующий `settings.json`
- Если файл отсутствует — фича отключена автоматически

---

## 6. Блок-схема обработки

```
                Trade/Order msg приходит
                         │
                         ▼
              ┌─ spike_aggregation.enabled? ─┐
              │                              │
              no                            yes
              │                              │
              ▼                              ▼
     ┌─────────────────┐        ┌─────────────────────┐
     │ Текущая логика   │        │ registerFill()      │
     │ createNextOrder  │        │ в SpikeDetector     │
     │ sendOrders       │        └──────────┬──────────┘
     └─────────────────┘                    │
                                   ┌── isSpike? ──┐
                                   │              │
                                   no            yes
                                   │              │
                                   ▼              ▼
                          ┌──────────────┐ ┌──────────────────┐
                          │ Текущая      │ │ Буферизация      │
                          │ логика       │ │ pendingSpikeOrders│
                          └──────────────┘ └────────┬─────────┘
                                                    │
                                         Каждый следующий Trade:
                                                    │
                                                    ▼
                                          ┌──────────────────┐
                                          │checkSpikeResoluti│
                                          └────────┬─────────┘
                                                   │
                                    ┌──────────────┼──────────────┐
                                    │              │              │
                              drift > max    timeout?       isStable?
                                    │              │              │
                                    ▼              ▼              ▼
                              ┌────────────────────────────────────┐
                              │     executeSpikeCounterOrders()     │
                              │                                    │
                              │  1. Агрегированный ордер           │
                              │  2. redistributeGridAfterSpike()   │
                              │  3. reset() всех компонентов       │
                              └────────────────────────────────────┘
                                              │
                                         on failure
                                              │
                                              ▼
                                    ┌──────────────────┐
                                    │ fallback:        │
                                    │ стандартные grid  │
                                    │ counter-orders   │
                                    └──────────────────┘
```

---

## 7. Затрагиваемые файлы

| Файл | Изменение |
|------|-----------|
| `spike/SpikeConfig.kt` | **НОВЫЙ** — конфигурация (загрузка из .conf через Typesafe Config) |
| `spike/SpikeDetector.kt` | **НОВЫЙ** — детектор спайков |
| `spike/PriceStabilizer.kt` | **НОВЫЙ** — монитор стабилизации цены |
| `spike/SpikeDetectorTest.kt` | **НОВЫЙ** — unit-тесты детектора |
| `spike/PriceStabilizerTest.kt` | **НОВЫЙ** — unit-тесты стабилизатора |
| `exchangeBots/{botName}/spike_aggregation.conf` | **НОВЫЙ** — конфигурация (опционально, если отсутствует — фича отключена) |
| `AlgorithmGrid.kt` | **ИЗМЕНЕНИЕ** — интеграция spike detection в handle() |

---

## 8. Рекомендуемые значения параметров

### Для низколиквидных пар (REACT/USDT, SUPRA/USDT):
```json
{
  "threshold": 3,
  "time_window_ms": 10000,
  "stabilization_delay_ms": 20000,
  "max_wait_time_ms": 120000,
  "stabilization_tolerance_percent": 0.5,
  "aggregate_order_type": "LIMIT"
}
```

### Для среднеликвидных пар (ETH/USDT, BTC/USDT):
```json
{
  "threshold": 5,
  "time_window_ms": 5000,
  "stabilization_delay_ms": 10000,
  "max_wait_time_ms": 60000,
  "stabilization_tolerance_percent": 0.2,
  "aggregate_order_type": "LIMIT"
}
```

---

## 9. Риски и митигации

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|-----------|
| Спайк = начало тренда, цена не вернётся | Средняя | Высокое | `max_price_drift_percent` — safety cutoff при дрифте. `max_wait_time_ms` — таймаут |
| Проскальзывание большого ордера | Средняя | Среднее | Разбивка на несколько ордеров (будущая оптимизация). Использование LIMIT вместо MARKET |
| Race condition: Trade trigger vs WebSocket | Низкая | Среднее | При активном спайке WebSocket handler пропускает обработку |
| Неконсистентное состояние БД при сбое | Низкая | Высокое | Fallback на стандартные grid counter-orders. `synchronizeOrders()` восстановит при restart |
| Спайки в обе стороны одновременно | Очень низкая | Среднее | SpikeDetector фильтрует по `orderSide` — учитывает только fills одного направления |

---

## 10. Тестирование

### 10.1 Unit-тесты

```
SpikeDetectorTest:
  - testNoSpikeWithFewOrders()
  - testSpikeDetectedAtThreshold()
  - testOldFillsExpire()
  - testResetClearsState()
  - testWeightedAvgPrice()

PriceStabilizerTest:
  - testNotStableWithHighVolatility()
  - testStableWithLowVolatility()
  - testMinDelayRespected()
  - testTimeout()
  - testMedianPrice()
```

### 10.2 Интеграционные тесты (через эмулятор)

Использовать `TestClientFileData` с историческими данными, содержащими спайки:
1. Запустить эмуляцию с `spike_aggregation.enabled = false` — замерить профит
2. Запустить эмуляцию с `spike_aggregation.enabled = true` — замерить профит
3. Сравнить результаты

### 10.3 Ручное тестирование

1. Запустить бота на тестовой паре с малыми объёмами
2. Дождаться спайка или спровоцировать рыночным ордером
3. Проверить Telegram-уведомления о spike detection
4. Проверить корректность агрегированного ордера в истории Gate.io
5. Проверить состояние grid после spike resolution

---

## 11. Порядок реализации

### Фаза 1: Основа
1. Создать `spike/SpikeConfig.kt`
2. Создать `spike/SpikeDetector.kt` + тесты
3. Создать `spike/PriceStabilizer.kt` + тесты
4. Добавить `spike_aggregation` в `BotSettingsGrid.Parameters`

### Фаза 2: Интеграция
5. Модифицировать Trade trigger в `AlgorithmGrid.handle()`
6. Добавить `handleWithSpikeDetection()`
7. Добавить `checkSpikeResolution()`
8. Добавить `executeSpikeCounterOrders()`
9. Добавить `redistributeGridAfterSpike()` и `fallbackToGridCounterOrders()`
10. Модифицировать WebSocket FILLED handler

### Фаза 3: Верификация
11. Тестирование через эмулятор с историческими данными
12. A/B сравнение профита
13. Деплой на тестовую пару

---

## 12. Ожидаемый эффект

На основе анализа кейса REACT/USDT (30.01.2026):

| Метрика | Текущая логика | Spike Aggregation | Разница |
|---------|---------------|-------------------|---------|
| Профит за спайк | ~1.02 USDT | ~1.91 USDT | **+87%** |
| Комиссии (тейкер vs мейкер) | ~0.06 USDT | ~0.03 USDT | **-50%** |
| Время завершения цикла | Мгновенно + ожидание | 30-120 сек + мгновенно | Сопоставимо |

При 1-2 спайках в день на одной паре — дополнительные **~1-2 USDT/день**, что на горизонте месяца составляет **~30-60 USDT** с одной пары.