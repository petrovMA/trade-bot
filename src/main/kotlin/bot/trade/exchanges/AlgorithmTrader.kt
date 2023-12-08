package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.libs.TrendCalculator
import com.typesafe.config.Config
import mu.KotlinLogging
import java.math.BigDecimal
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
    sendMessage = sendMessage
) {
    private val settings: BotSettingsTrader = super.botSettings as BotSettingsTrader
    private val minRange = settings.parameters.tradingRange.lowerBound
    private val maxRange = settings.parameters.tradingRange.upperBound
    private val orderDistance = settings.parameters.inOrderDistance.distance
    private val orderQuantity = settings.parameters.inOrderQuantity.value
    private val triggerDistance = settings.parameters.triggerDistance.distance
    private val orderMaxQuantity = settings.parameters.orderMaxQuantity
    private val triggerInOrderDistance = settings.parameters.triggerInOrderDistance?.distance
    private val minTpDistance = settings.parameters.minTpDistance.distance
    private val maxTpDistance = settings.parameters.maxTpDistance.distance
    private val trailingInOrderDistance = settings.parameters.trailingInOrderDistance?.distance
    private val setCloseOrders = settings.parameters.setCloseOrders
    private val ordersType = settings.ordersType
    private val minOrderAmount = settings.parameters.minOrderAmount?.amount ?: BigDecimal.ZERO
    private var currentDirection: DIRECTION = when (settings.direction) {
        BotSettingsTrader.Direction.LONG -> DIRECTION.LONG
        BotSettingsTrader.Direction.SHORT -> DIRECTION.SHORT
        else -> DIRECTION.LONG
    }
    private var trendCalculator: TrendCalculator? = null

    private val log = if (isLog) KotlinLogging.logger {} else null
    private val ordersListForExecute: MutableMap<String, Order> = mutableMapOf()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {
        trendCalculator = TrendCalculator(
            client,
            botSettings.pair,
            settings.trendDetector!!.hmaParameters.timeFrame.toDuration() to settings.trendDetector.hmaParameters.hma1Period,
            settings.trendDetector.hmaParameters.timeFrame.toDuration() to settings.trendDetector.hmaParameters.hma2Period,
            settings.trendDetector.hmaParameters.timeFrame.toDuration() to settings.trendDetector.hmaParameters.hma3Period,
            settings.trendDetector.rsi1.timeFrame.toDuration() to settings.trendDetector.rsi1.rsiPeriod,
            settings.trendDetector.rsi2.timeFrame.toDuration() to settings.trendDetector.rsi2.rsiPeriod
        )
    }

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Trade -> {
                prevPrice = if (prevPrice == BigDecimal(0)) msg.price
                else currentPrice
                currentPrice = msg.price
                log?.debug("{} TradeEvent: {}", botSettings.name, msg)

                from = if (from > msg.time) msg.time else from
                to = if (to < msg.time) msg.time else to

                if (currentPrice > minRange && currentPrice < maxRange) {

                    val priceIn = (currentPrice - (currentPrice % orderDistance)).toPrice()
                    val prevPriceIn = (prevPrice - (prevPrice % orderDistance)).toPrice()

                    var currPriceIn = priceIn

                    do {
                        when (ordersType) {
                            TYPE.MARKET -> {
                                if (orders[currPriceIn] != null)
                                    log?.trace("{} Order already exist: {}", botSettings.name, orders[currPriceIn])
                                else {
                                    if (orders.size < orderMaxQuantity) {
                                        if (triggerInOrderDistance == null) {
                                            orders[currPriceIn] = sentOrder(
                                                amount = calcAmount(orderQuantity, BigDecimal(currPriceIn)),
                                                orderSide = if (currentDirection == DIRECTION.LONG) SIDE.BUY
                                                else SIDE.SELL,
                                                price = BigDecimal(currPriceIn),
                                                orderType = TYPE.MARKET
                                            ).also {
                                                if (currentDirection == DIRECTION.LONG) {
                                                    it.lastBorderPrice = BigDecimal(-99999999999999L)
                                                    it.side = SIDE.SELL
                                                } else {
                                                    it.lastBorderPrice = BigDecimal(99999999999999L)
                                                    it.side = SIDE.BUY
                                                }
                                            }
                                        } else {
                                            orders[currPriceIn] = Order(
                                                orderId = "",
                                                pair = botSettings.pair,
                                                price = BigDecimal(currPriceIn),
                                                origQty = calcAmount(orderQuantity, BigDecimal(currPriceIn)),
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

                        currPriceIn = if (priceIn < prevPriceIn) (BigDecimal(currPriceIn) + orderDistance).toPrice()
                        else (BigDecimal(currPriceIn) - orderDistance).toPrice()

                    } while (
                        (BigDecimal(currPriceIn) < BigDecimal(prevPriceIn) &&
                                BigDecimal(priceIn) < BigDecimal(prevPriceIn)) ||
                        (BigDecimal(currPriceIn) > BigDecimal(prevPriceIn) &&
                                BigDecimal(priceIn) > BigDecimal(prevPriceIn))
                    )
                } else
                    log?.trace(
                        "{} Price {}, not in range: {}",
                        botSettings.name,
                        format(currentPrice),
                        minRange to maxRange
                    )

                when (currentDirection) {
                    DIRECTION.LONG -> {
                        val ordersForUpdate = orders.filter { (_, v) -> v.type == TYPE.MARKET }

                        ordersForUpdate.forEach { (k, v) ->
                            if (v.side == SIDE.SELL) {
                                if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                                    v.lastBorderPrice = currentPrice

                                    v.stopPrice?.let {
                                        if (it < currentPrice - maxTpDistance)
                                            v.stopPrice = currentPrice - maxTpDistance
                                    } ?: run {
                                        if (k.toBigDecimal() <= (currentPrice - triggerDistance))
                                            v.stopPrice = currentPrice - minTpDistance
                                    }

                                    orders[k] = v
                                }
                                if (v.stopPrice?.run { this >= currentPrice } == true) {
                                    log?.debug("{} Order close: {}", botSettings.name, v)
                                    ordersListForExecute[k] = v
                                }
                            } else if (trailingInOrderDistance != null && triggerInOrderDistance != null) {
                                if (v.lastBorderPrice == null || v.lastBorderPrice!! > currentPrice) {
                                    v.lastBorderPrice = currentPrice

                                    if (
                                        v.stopPrice?.run { this > currentPrice + trailingInOrderDistance } == true ||
                                        (v.stopPrice == null && k.toBigDecimal() >= (currentPrice + triggerInOrderDistance))
                                    ) {
                                        v.stopPrice = currentPrice + trailingInOrderDistance
                                    }

                                    orders[k] = v
                                }
                                if (v.stopPrice?.run { this <= currentPrice } == true) {
                                    log?.debug("{} Order close: {}", botSettings.name, v)
                                    ordersListForExecute[k] = v
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
                                        if (it > currentPrice + maxTpDistance)
                                            v.stopPrice = currentPrice + maxTpDistance
                                    } ?: run {
                                        if (k.toBigDecimal() >= (currentPrice + triggerDistance))
                                            v.stopPrice = currentPrice + minTpDistance
                                    }

                                    orders[k] = v
                                }
                                if (v.stopPrice?.run { this <= currentPrice } == true) {
                                    log?.debug("{} Order close: {}", botSettings.name, v)
                                    ordersListForExecute[k] = v
                                }
                            } else if (trailingInOrderDistance != null && triggerInOrderDistance != null) {
                                if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                                    v.lastBorderPrice = currentPrice

                                    if (
                                        v.stopPrice?.run { this < currentPrice - trailingInOrderDistance } == true ||
                                        (v.stopPrice == null && k.toBigDecimal() <= (currentPrice - triggerInOrderDistance))
                                    ) {
                                        v.stopPrice = currentPrice - trailingInOrderDistance
                                    }

                                    orders[k] = v
                                }
                                if (v.stopPrice?.run { this >= currentPrice } == true) {
                                    log?.debug("{} Order close: {}", botSettings.name, v)
                                    ordersListForExecute[k] = v
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
                    checkOrders()
                    sentOrder(
                        amount = buySumAmount,
                        orderSide = SIDE.BUY,
                        price = currentPrice,
                        orderType = TYPE.MARKET
                    )
                }

                if (sellSumAmount > calcAmount(minOrderAmount, currentPrice)) {
                    checkOrders()
                    sentOrder(
                        amount = sellSumAmount,
                        orderSide = SIDE.SELL,
                        price = currentPrice,
                        orderType = TYPE.MARKET
                    )
                }
            }

            is Order -> {
                log?.info("{} Order update: {}", botSettings.name, msg)
                if (msg.pair == botSettings.pair) {
                    if (msg.status == STATUS.FILLED) {
                        if (msg.type == TYPE.LIMIT) {
                            val order = msg
                            val ordersForUpdate = orders.filter { (_, v) -> v.orderId == order.orderId }

                            ordersForUpdate.forEach { (k, _) ->
                                orders[k] = order.also {
                                    it.type = TYPE.MARKET
                                    it.lastBorderPrice = BigDecimal.ZERO
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

    private fun checkOrders() {
        ordersListForExecute.forEach {
            if (trailingInOrderDistance != null) {
                when (currentDirection) {
                    DIRECTION.LONG -> {
                        when (it.value.side) {
                            SIDE.BUY -> {
                                orders[it.key] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = BigDecimal(it.key),
                                    origQty = calcAmount(orderQuantity, BigDecimal(it.key)),
                                    executedQty = BigDecimal(0),
                                    side = SIDE.SELL,
                                    type = TYPE.MARKET,
                                    status = STATUS.NEW,
                                    lastBorderPrice = BigDecimal(-99999999999999L),
                                    stopPrice = null
                                )
                            }

                            SIDE.SELL -> orders.remove(it.key)
                            else -> log?.error("${botSettings.name} Unknown side: ${it.value.side}")
                        }
                    }

                    DIRECTION.SHORT -> {
                        when (it.value.side) {
                            SIDE.SELL -> {
                                orders[it.key] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = BigDecimal(it.key),
                                    origQty = calcAmount(orderQuantity, BigDecimal(it.key)),
                                    executedQty = BigDecimal(0),
                                    side = SIDE.BUY,
                                    type = TYPE.MARKET,
                                    status = STATUS.NEW,
                                    lastBorderPrice = BigDecimal(99999999999999L),
                                    stopPrice = null
                                )
                            }

                            SIDE.BUY -> orders.remove(it.key)
                            else -> log?.error("${botSettings.name} Unknown side: ${it.value.side}")
                        }
                    }
                }
            } else orders.remove(it.key)
        }

        log?.info(
            "{} Price = '{}' Orders for execute:\n{}",
            botSettings.name,
            currentPrice.toPrice(),
            json(ordersListForExecute)
        )

        ordersListForExecute.clear()
    }

    override fun synchronizeOrders() {
        when (orders) {
            is ObservableHashMap -> {
                orders.readFromFile()

                var price = minRange
                val openOrders = client.getOpenOrders(botSettings.pair)
                while (price <= maxRange) {
                    val priceIn = price.toPrice()
                    openOrders.find { it.price == priceIn.toBigDecimal() }?.let { openOrder ->
                        orders[priceIn] ?: run {

                            val qty = calcAmount(orderQuantity, price).percent(10.toBigDecimal())

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
                    price += orderDistance
                }

                orders.forEach { (k, v) ->
                    if (v.type != TYPE.MARKET && v.price?.run { this in minRange..maxRange } == true)
                        openOrders
                            .find { v.orderId == it.orderId }
                            ?: run {
                                ordersListForExecute[k] = v
                                log?.info("${botSettings.name} File order not found in exchange, file Order removed:\n$v")
                            }
                }

                ordersListForExecute.forEach { (k, _) -> orders.remove(k) }
                ordersListForExecute.clear()

                if (setCloseOrders) {
                    price = minRange
                    while (price <= maxRange) {
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
                                origQty = calcAmount(orderQuantity, price),
                                executedQty = BigDecimal(0),
                                side = if (currentDirection == DIRECTION.LONG) SIDE.SELL else SIDE.BUY,
                                type = TYPE.MARKET,
                                status = STATUS.FILLED,
                                lastBorderPrice = BigDecimal.ZERO
                            )
                        }

                        price += orderDistance
                    }
                }
            }

            else -> {}
        }
    }

    override fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal =
        if (firstBalanceForOrderAmount) settings.parameters.minOrderAmount?.countOfDigitsAfterDotForAmount
            ?.let { amount.round(it) }
            ?: amount.round(botSettings.countOfDigitsAfterDotForAmount)
        else settings.parameters.minOrderAmount?.countOfDigitsAfterDotForAmount?.let { amount.div8(price).round(it) }
            ?: amount.div8(price).round(botSettings.countOfDigitsAfterDotForAmount)
}