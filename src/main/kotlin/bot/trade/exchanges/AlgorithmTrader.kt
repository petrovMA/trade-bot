package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.libs.TrendCalculator
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.LinkedBlockingDeque


class AlgorithmTrader(
    botSettings: BotSettings,
    exchangeBotsFiles: String,
    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = newClient(exchangeEnum, api, sec),
    isLog: Boolean = true,
    isEmulate: Boolean = false,
    logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
    private val endTimeForTrendCalculator: Long? = null,
    sendMessage: (String, Boolean) -> Unit
) : Algorithm(
    botSettings = botSettings,
    exchangeBotsFiles = exchangeBotsFiles,
    queue = queue,
    exchangeEnum = exchangeEnum,
    conf = conf,
    api = api,
    sec = sec,
    client = client,
    isLog = isLog,
    isEmulate = isEmulate,
    logMessageQueue = logMessageQueue,
    sendMessage = sendMessage
) {
    private val settings: BotSettingsTrader = super.botSettings as BotSettingsTrader
    private val ordersType = settings.ordersType
    private val strategy = settings.strategy
    private val notAutoCalcTrend = settings.trendDetector?.notAutoCalcTrend ?: true
    private val minOrderAmount = settings.minOrderAmount?.amount ?: BigDecimal.ZERO
    private val long = settings.parameters.longParameters
    private val short = settings.parameters.shortParameters
    private var trendCalculator: TrendCalculator? = null
    private var trend: TrendCalculator.Trend? = null
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val ordersListForExecute: MutableMap<Pair<DIRECTION, String>, Order> = mutableMapOf()

    private val ordersShort: MutableMap<String, Order> = if (isEmulate.not()) ObservableHashMap(
        filePath = "$path/orders_short".also {
            if (isEmulate.not() && File(it).isDirectory.not()) Files.createDirectories(Paths.get(it))
        },
        keyToFileName = { key -> key.replace('.', '_') + ".json" },
        fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
    )
    else mutableMapOf()

    private val ordersLong: MutableMap<String, Order> = if (isEmulate.not()) ObservableHashMap(
        filePath = "$path/orders_short".also {
            if (isEmulate.not() && File(it).isDirectory.not()) Files.createDirectories(Paths.get(it))
        },
        keyToFileName = { key -> key.replace('.', '_') + ".json" },
        fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
    )
    else mutableMapOf()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {
        trendCalculator = settings.trendDetector?.run {
            TrendCalculator(
                client = client,
                pair = botSettings.pair,
                hma1 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma1Period,
                hma2 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma2Period,
                hma3 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma3Period,
                rsi1 = rsi1.timeFrame.toDuration() to rsi1.rsiPeriod,
                rsi2 = rsi2.timeFrame.toDuration() to rsi2.rsiPeriod,
                endTime = endTimeForTrendCalculator
            )
        } ?: run {
            send("Not found trendDetector settings")
            throw RuntimeException("Not found trendDetector settings")
        }
    }

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Candlestick -> {

                trendCalculator?.addCandlesticks(msg)

                if (!notAutoCalcTrend) getTrend()?.let {
                    if (trend?.trend != it.trend) {
                        trend = it
                        send("#${botSettings.name} #Trend :\n```json\n${json(it)}\n```", true)

                        when (it.trend) {
                            TrendCalculator.Trend.TREND.LONG -> {

                            }

                            TrendCalculator.Trend.TREND.SHORT -> {

                            }

                            TrendCalculator.Trend.TREND.FLAT, TrendCalculator.Trend.TREND.HEDGE -> {

                            }
                        }
                    }
                }

                prevPrice = if (prevPrice == BigDecimal(0)) msg.close
                else currentPrice
                currentPrice = msg.close

                from = if (from > msg.closeTime) msg.closeTime else from
                to = if (to < msg.closeTime) msg.closeTime else to

                long?.let { createOrdersForExecute(DIRECTION.LONG, it) }
                short?.let { createOrdersForExecute(DIRECTION.SHORT, it) }

                var sellSumAmount = BigDecimal.ZERO
                var buySumAmount = BigDecimal.ZERO

                ordersListForExecute.forEach { (_, v) ->
                    when (v.side) {
                        SIDE.BUY -> buySumAmount += v.origQty
                        SIDE.SELL -> sellSumAmount += v.origQty
                        else -> {}
                    }
                }

                if (buySumAmount > calcAmount(minOrderAmount, currentPrice)) {
                    log("LONG Orders before execute:\n${json(ordersLong, false)}", File("logging/$path/orders.txt"))
                    log("SHORT Orders before execute:\n${json(ordersShort, false)}", File("logging/$path/orders.txt"))
                    checkOrders()
                    sentOrder(
                        amount = buySumAmount,
                        orderSide = SIDE.BUY,
                        price = currentPrice,
                        orderType = TYPE.MARKET
                    )
                    log("LONG Orders after execute:\n${json(ordersLong, false)}", File("logging/$path/orders.txt"))
                    log("SHORT Orders after execute:\n${json(ordersShort, false)}", File("logging/$path/orders.txt"))
                }

                if (sellSumAmount > calcAmount(minOrderAmount, currentPrice)) {
                    log("LONG Orders before execute:\n${json(ordersLong, false)}", File("logging/$path/orders.txt"))
                    log("SHORT Orders before execute:\n${json(ordersShort, false)}", File("logging/$path/orders.txt"))
                    checkOrders()
                    sentOrder(
                        amount = sellSumAmount,
                        orderSide = SIDE.SELL,
                        price = currentPrice,
                        orderType = TYPE.MARKET
                    )
                    log("LONG Orders after execute:\n${json(ordersLong, false)}", File("logging/$path/orders.txt"))
                    log("SHORT Orders after execute:\n${json(ordersShort, false)}", File("logging/$path/orders.txt"))
                }
            }

            is Order -> {
                log?.info("{} Order update: {}", botSettings.name, msg)
                if (msg.pair == botSettings.pair) {
                    if (msg.status == STATUS.FILLED) {
                        if (msg.type == TYPE.LIMIT) {
                            var ordersForUpdate = ordersLong.filter { (_, v) -> v.orderId == msg.orderId }

                            ordersForUpdate.forEach { (k, _) ->
                                ordersLong[k] = msg.also {
                                    it.type = TYPE.MARKET
                                    it.lastBorderPrice = null
                                }
                            }

                            ordersForUpdate = ordersShort.filter { (_, v) -> v.orderId == msg.orderId }

                            ordersForUpdate.forEach { (k, _) ->
                                ordersShort[k] = msg.also {
                                    it.type = TYPE.MARKET
                                    it.lastBorderPrice = null
                                }
                            }
                        }
                    }
                    send("#${botSettings.name} Order update:\n```json\n$msg\n```", true)
                }
            }

            is BotEvent -> {
                when (msg.type) {
                    BotEvent.Type.GET_PAIR_OPEN_ORDERS -> {
                        val symbols = msg.text.split("[^a-zA-Z]+".toRegex())
                            .filter { it.isNotBlank() }

                        send(
                            "#${botSettings.name} " +
                                    client.getOpenOrders(TradePair(symbols[0], symbols[1]))
                                        .joinToString("\n\n")
                        )
                    }

                    BotEvent.Type.GET_ALL_OPEN_ORDERS -> {
                        val pairs = msg.text
                            .split("\\s+".toRegex())
                            .filter { it.isNotBlank() }
                            .map { pair ->
                                val symbols = pair.split("[^a-zA-Z]+".toRegex())
                                    .filter { it.isNotBlank() }
                                TradePair(symbols[0], symbols[1])
                            }

                        client.getAllOpenOrders(pairs)
                            .forEach { send("#${botSettings.name} ${it.key}\n${it.value.joinToString("\n\n")}") }
                    }

                    BotEvent.Type.SHOW_BALANCES -> {
                        send(
                            "#${botSettings.name} #AllBalances " +
                                    client.getBalances()
                                        ?.toList()
                                        ?.joinToString(prefix = "\n", separator = "\n") {
                                            it.first + "\n" + it.second.joinToString(
                                                prefix = "\n",
                                                separator = "\n"
                                            )
                                        }
                        )
                    }

                    BotEvent.Type.INTERRUPT -> {
                        stream.interrupt()
                        return
                    }

                    else -> send("#${botSettings.name} Unsupported command: ${msg.type}")
                }
            }

            else -> log?.warn("${botSettings.name} Unsupported message: $msg")
        }
    }


    private fun createOrdersForExecute(
        currentDirection: DIRECTION,
        params: BotSettingsTrader.TradeParameters.Parameters,
    ) = when (currentDirection) {
        DIRECTION.LONG -> ordersLong
        DIRECTION.SHORT -> ordersShort
    }.let { orders ->
        if (currentPrice > params.minRange() && currentPrice < params.maxRange()) {

            val priceIn = (currentPrice - (currentPrice % params.orderDistance())).toPrice()
            val prevPriceIn = (prevPrice - (prevPrice % params.orderDistance())).toPrice()

            var currPriceIn = priceIn

            do {
                when (ordersType) {
                    TYPE.MARKET -> {
                        if (orders[currPriceIn] != null)
                            log?.trace("{} Order already exist: {}", botSettings.name, orders[currPriceIn])
                        else {
                            if (orders.size < params.orderMaxQuantity()) {
                                if (params.triggerInOrderDistance() == null) {
                                    orders[currPriceIn] = sentOrder(
                                        amount = calcAmount(params.orderQuantity(), BigDecimal(currPriceIn)),
                                        orderSide = if (currentDirection == DIRECTION.LONG) SIDE.BUY
                                        else SIDE.SELL,
                                        price = BigDecimal(currPriceIn),
                                        orderType = TYPE.MARKET
                                    ).also {
                                        if (currentDirection == DIRECTION.LONG) {
                                            it.lastBorderPrice = null
                                            it.side = SIDE.SELL
                                        } else {
                                            it.lastBorderPrice = null
                                            it.side = SIDE.BUY
                                        }
                                    }
                                } else {
                                    orders[currPriceIn] = Order(
                                        orderId = "",
                                        pair = botSettings.pair,
                                        price = BigDecimal(currPriceIn),
                                        origQty = calcAmount(params.orderQuantity(), BigDecimal(currPriceIn)),
                                        executedQty = BigDecimal(0),
                                        side = if (currentDirection == DIRECTION.LONG) SIDE.BUY
                                        else SIDE.SELL,
                                        type = TYPE.MARKET,
                                        status = STATUS.NEW,
                                        lastBorderPrice = if (currentDirection == DIRECTION.LONG)
                                            currPriceIn.toBigDecimal()
                                        else
                                            currPriceIn.toBigDecimal(),
                                        stopPrice = null
                                    )
                                }
                            }
                        }
                    }

                    TYPE.LIMIT -> {
                        when (currentDirection) {
                            DIRECTION.LONG -> {
                                TODO("LONG strategy not implemented for TYPE.LIMIT (find implementation on Git history)")
                            }

                            DIRECTION.SHORT -> {
                                TODO("SHORT strategy not implemented for TYPE.LIMIT")
                            }
                        }
                    }

                    else -> throw UnsupportedOrderTypeException("Error: Unknown order type '$ordersType'!")
                }

                currPriceIn =
                    if (priceIn < prevPriceIn) (BigDecimal(currPriceIn) + params.orderDistance()).toPrice()
                    else (BigDecimal(currPriceIn) - params.orderDistance()).toPrice()

            } while (
                (BigDecimal(currPriceIn) < BigDecimal(prevPriceIn) && BigDecimal(priceIn) < BigDecimal(prevPriceIn))
                ||
                (BigDecimal(currPriceIn) > BigDecimal(prevPriceIn) &&
                        BigDecimal(priceIn) > BigDecimal(prevPriceIn))
            )
        } else
            log?.trace(
                "{} Price {}, not in range: {}",
                botSettings.name,
                format(currentPrice),
                params.minRange() to params.maxRange()
            )

        when (currentDirection) {
            DIRECTION.LONG -> {
                val ordersForUpdate = orders.filter { (_, v) -> v.type == TYPE.MARKET }

                ordersForUpdate.forEach { (k, v) ->
                    if (v.side == SIDE.SELL) {
                        if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                            v.lastBorderPrice = currentPrice

                            v.stopPrice?.let {
                                if (it < currentPrice - params.maxTpDistance())
                                    v.stopPrice = currentPrice - params.maxTpDistance()
                            } ?: run {
                                if (k.toBigDecimal() <= (currentPrice - params.triggerDistance()))
                                    v.stopPrice = currentPrice - params.minTpDistance()
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this >= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersListForExecute[currentDirection to k] = v
                        }
                    } else if (params.trailingInOrderDistance() != null && params.triggerInOrderDistance() != null) {
                        if (v.lastBorderPrice == null || v.lastBorderPrice!! > currentPrice) {
                            v.lastBorderPrice = currentPrice

                            if (
                                v.stopPrice?.run { this > currentPrice + params.trailingInOrderDistance()!! } == true ||
                                (v.stopPrice == null && k.toBigDecimal() >= (currentPrice + params.triggerInOrderDistance()!!))
                            ) {
                                v.stopPrice = currentPrice + params.trailingInOrderDistance()!!
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this <= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersListForExecute[currentDirection to k] = v
                        }
                    } else {
                        log?.warn(
                            "{} No param 'trailingInOrderDistance' in settings for order:\n{}",
                            botSettings.name,
                            v
                        )
                    }
                }
            }

            DIRECTION.SHORT -> {
                val ordersForUpdate = orders.filter { (_, v) -> v.type == TYPE.MARKET }

                ordersForUpdate.forEach { (k, v) ->
                    if (v.side == SIDE.BUY) {
                        if (v.lastBorderPrice == null || v.lastBorderPrice!! > currentPrice) {
                            v.lastBorderPrice = currentPrice

                            v.stopPrice?.let {
                                if (it > currentPrice + params.maxTpDistance())
                                    v.stopPrice = currentPrice + params.maxTpDistance()
                            } ?: run {
                                if (k.toBigDecimal() >= (currentPrice + params.triggerDistance()))
                                    v.stopPrice = currentPrice + params.minTpDistance()
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this <= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersListForExecute[currentDirection to k] = v
                        }
                    } else if (params.trailingInOrderDistance() != null && params.triggerInOrderDistance() != null) {
                        if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                            v.lastBorderPrice = currentPrice

                            if (
                                v.stopPrice?.run { this < currentPrice - params.trailingInOrderDistance()!! } == true ||
                                (v.stopPrice == null && k.toBigDecimal() <= (currentPrice - params.triggerInOrderDistance()!!))
                            ) {
                                v.stopPrice = currentPrice - params.trailingInOrderDistance()!!
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this >= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersListForExecute[currentDirection to k] = v
                        }
                    } else {
                        log?.warn(
                            "{} No param 'trailingInOrderDistance' in settings for order:\n{}",
                            botSettings.name,
                            v
                        )
                    }
                }
            }
        }
    }

    private fun checkOrders() {
        ordersListForExecute.forEach { (k, v) ->
            if (trailingInOrderDistance != null) {
                when (k.first) {
                    DIRECTION.LONG -> {
                        when (v.side) {
                            SIDE.BUY -> {
                                orders[k.second] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = BigDecimal(k.second),
                                    origQty = calcAmount(orderQuantity, BigDecimal(k.second)),
                                    executedQty = BigDecimal(0),
                                    side = SIDE.SELL,
                                    type = TYPE.MARKET,
                                    status = STATUS.NEW,
                                    lastBorderPrice = null,
                                    stopPrice = null
                                )
                            }

                            SIDE.SELL -> orders.remove(k.second)
                            else -> log?.error("${botSettings.name} Unknown side: ${v.side}")
                        }
                    }

                    DIRECTION.SHORT -> {
                        when (v.side) {
                            SIDE.SELL -> {
                                orders[k.second] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = BigDecimal(k.second),
                                    origQty = calcAmount(orderQuantity, BigDecimal(k.second)),
                                    executedQty = BigDecimal(0),
                                    side = SIDE.BUY,
                                    type = TYPE.MARKET,
                                    status = STATUS.NEW,
                                    lastBorderPrice = null,
                                    stopPrice = null
                                )
                            }

                            SIDE.BUY -> orders.remove(k.second)
                            else -> log?.error("${botSettings.name} Unknown side: ${v.side}")
                        }
                    }
                }
            } else orders.remove(k.second)
        }

        log("Price = '${currentPrice.toPrice()}' Orders for execute:\n${json(ordersListForExecute)}")

        ordersListForExecute.clear()
    }

    override fun synchronizeOrders() {
        if (ordersLong is ObservableHashMap) long?.let { syncOrders(it, ordersLong, DIRECTION.LONG) }
        if (ordersShort is ObservableHashMap) short?.let { syncOrders(it, ordersShort, DIRECTION.SHORT) }
    }

    private fun syncOrders(
        params: BotSettingsTrader.TradeParameters.Parameters,
        orders: ObservableHashMap,
        currentDirection: DIRECTION
    ) {
        orders.readFromFile()

        var price = params.minRange()
        val openOrders = client.getOpenOrders(botSettings.pair)
        while (price <= params.maxRange()) {
            val priceIn = price.toPrice()
            openOrders.find { it.price == priceIn.toBigDecimal() }?.let { openOrder ->
                orders[priceIn] ?: run {

                    val qty = calcAmount(params.orderQuantity(), price).percent(10.toBigDecimal())

                    if (currentDirection == DIRECTION.LONG && openOrder.side == SIDE.BUY) {
                        if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                            orders[price.toPrice()] = openOrder
                            log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                        }
                    } else if (currentDirection == DIRECTION.SHORT && openOrder.side == SIDE.SELL) {
                        if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                            orders[price.toPrice()] = openOrder
                            log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                        }
                    }
                }
            }
            price += params.orderDistance()
        }

        orders.forEach { (k, v) ->
            if (v.type != TYPE.MARKET && v.price?.run { this in params.minRange()..params.maxRange() } == true)
                openOrders
                    .find { v.orderId == it.orderId }
                    ?: run {
                        ordersListForExecute[k] = v
                        log?.info("${botSettings.name} File order not found in exchange, file Order removed:\n$v")
                    }
        }

        ordersListForExecute.forEach { (k, _) -> orders.remove(k) }
        ordersListForExecute.clear()

        if (params.setCloseOrders) {
            price = params.minRange()
            while (price <= params.maxRange()) {
                val priceIn = price.toPrice()

                orders[priceIn]?.let { order ->
                    log?.trace(
                        "{} Order already exist: {}",
                        botSettings.name,
                        order
                    )
                } ?: run {
                    orders[priceIn] = Order(
                        orderId = "",
                        pair = botSettings.pair,
                        price = priceIn.toBigDecimal(),
                        origQty = calcAmount(params.orderQuantity(), price),
                        executedQty = BigDecimal(0),
                        side = if (currentDirection == DIRECTION.LONG) SIDE.SELL else SIDE.BUY,
                        type = TYPE.MARKET,
                        status = STATUS.FILLED,
                        lastBorderPrice = null
                    )
                }

                price += params.orderDistance()
            }
        }
    }

    override fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal =
        if (firstBalanceForOrderAmount) settings.minOrderAmount?.countOfDigitsAfterDotForAmount
            ?.let { amount.round(it) }
            ?: amount.round(botSettings.countOfDigitsAfterDotForAmount)
        else settings.minOrderAmount?.countOfDigitsAfterDotForAmount?.let { amount.div8(price).round(it) }
            ?: amount.div8(price).round(botSettings.countOfDigitsAfterDotForAmount)

    fun getTrend(): TrendCalculator.Trend? = trendCalculator?.getTrend()

    fun orders() = botSettings to orders
}