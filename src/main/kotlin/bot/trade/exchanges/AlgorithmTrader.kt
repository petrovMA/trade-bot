package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
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
    override val botSettings: BotSettingsTrader = super.botSettings as BotSettingsTrader
    private val minRange = this.botSettings.tradingRange.first
    private val maxRange = this.botSettings.tradingRange.second

    private val log = if (isLog) KotlinLogging.logger {} else null

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun run() {
        saveBotSettings(botSettings)
        stopThread = false
        try {
            if (File(ordersPath).isDirectory.not()) Files.createDirectories(Paths.get(ordersPath))

            synchronizeOrders()

            stream.run { start() }

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is Trade -> {
                            prevPrice = currentPrice
                            currentPrice = msg.price
                            log?.debug("{} TradeEvent:\n{}", botSettings.name, msg)

                            from = if (from > msg.time) msg.time else from
                            to = if (to < msg.time) msg.time else to

                            if (currentPrice > minRange && currentPrice < maxRange) {

                                val priceIn = (currentPrice - (currentPrice % botSettings.orderDistance)).toPrice()

                                when (botSettings.ordersType) {
                                    TYPE.MARKET -> {
                                        orders[priceIn]?.let { log?.trace("{} Order already exist: {}", botSettings.name, it) }
                                            ?: run {
                                                if (orders.size < botSettings.orderMaxQuantity) {
                                                    orders[priceIn] = sentOrder(
                                                        amount = botSettings.orderSize,
                                                        orderSide = if (botSettings.direction == DIRECTION.LONG) SIDE.BUY
                                                        else SIDE.SELL,
                                                        price = priceIn.toBigDecimal(),
                                                        orderType = TYPE.MARKET
                                                    ).also {
                                                        if (botSettings.direction == DIRECTION.LONG)
                                                            it.lastBorderPrice = BigDecimal.ZERO
                                                        else
                                                            it.lastBorderPrice = BigDecimal(99999999999999L)
                                                    }
                                                }
                                            }
                                    }

                                    TYPE.LIMIT -> {
                                        when (botSettings.direction) {
                                            DIRECTION.LONG -> {
                                                var keyPrice = priceIn.toBigDecimal()
                                                while (keyPrice > minRange) {
                                                    keyPrice.toPrice().let {
                                                        orders[it]?.let { order -> log?.trace("{} Order already exist: {}", botSettings.name, order) }
                                                            ?: run {
                                                                if (orders.size < botSettings.orderMaxQuantity) {
                                                                    orders[it] = sentOrder(
                                                                        price = keyPrice,
                                                                        amount = botSettings.orderSize,
                                                                        orderSide = SIDE.BUY,
                                                                        orderType = TYPE.LIMIT
                                                                    )
                                                                } else
                                                                    log?.trace("{} Orders count limit reached: price = {}; orderMaxQuantity = {}", botSettings.name, keyPrice, botSettings.orderMaxQuantity)
                                                            }
                                                    }
                                                    keyPrice -= botSettings.orderDistance
                                                }

                                                val prev =
                                                    (prevPrice - (prevPrice % botSettings.orderDistance)).toPrice()

                                                if (priceIn.toBigDecimal() != prev.toBigDecimal()) {
                                                    orders[prev]?.let { order ->
                                                        if (order.price?.let { it > currentPrice } == true && order.status != STATUS.FILLED && order.status != STATUS.REJECTED && order.type != TYPE.MARKET) {
                                                            val o = getOrder(botSettings.pair, order.orderId)
                                                                ?: order.apply { status = STATUS.FILLED }

                                                            if (o.status == STATUS.FILLED || o.status == STATUS.REJECTED) {
                                                                orders[prev] = o.also {
                                                                    it.type = TYPE.MARKET
                                                                    it.lastBorderPrice = BigDecimal.ZERO
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            DIRECTION.SHORT -> {
                                                TODO()
                                            }
                                        }
                                    }

                                    else -> throw UnsupportedOrderTypeException("Error: Unknown order type '${botSettings.ordersType}'!")
                                }
                            } else
                                log?.warn("${botSettings.name} Price ${format(currentPrice)}, not in range: ${botSettings.tradingRange}")

                            when (botSettings.direction) {
                                DIRECTION.LONG -> {
                                    val ordersForUpdate = orders.filter { it.value.type == TYPE.MARKET }

                                    ordersForUpdate.forEach { (k, v) ->
                                        if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                                            v.lastBorderPrice = currentPrice

                                            if (
                                                v.stopPrice?.run { this < currentPrice - botSettings.triggerDistance } == true
                                                || v.stopPrice == null && k.toBigDecimal() < (currentPrice - botSettings.triggerDistance - botSettings.enableStopOrderDistance)
                                            ) {
                                                v.stopPrice = currentPrice - botSettings.triggerDistance
                                            }

                                            orders[k] = v
                                        }
                                        if (v.stopPrice?.run { this >= currentPrice } == true) {
                                            log?.debug("{} Order close: {}", botSettings.name, v)
                                            ordersListForRemove.add(k to v.apply { side = SIDE.SELL })
                                        }
                                    }
                                }

                                DIRECTION.SHORT -> {
                                    val ordersForUpdate = orders.filter { it.value.type == TYPE.MARKET }

                                    ordersForUpdate.forEach { (k, v) ->
                                        if (v.lastBorderPrice == null || v.lastBorderPrice!! > currentPrice) {
                                            v.lastBorderPrice = currentPrice

                                            if (
                                                v.stopPrice?.run { this > currentPrice - botSettings.triggerDistance } == true
                                                || v.stopPrice == null && k.toBigDecimal() > (currentPrice + botSettings.triggerDistance + botSettings.enableStopOrderDistance)
                                            ) {
                                                v.stopPrice = currentPrice - botSettings.triggerDistance
                                            }

                                            orders[k] = v
                                        }
                                        if (v.stopPrice?.run { this <= currentPrice } == true) {
                                            log?.debug("{} Order close: {}", botSettings.name, v)
                                            ordersListForRemove.add(k to v.apply { side = SIDE.BUY })
                                        }
                                    }
                                }
                            }

                            var sellSumAmount = BigDecimal.ZERO
                            var buySumAmount = BigDecimal.ZERO

                            ordersListForRemove.forEach {
                                orders.remove(it.first)

                                when (it.second.side) {
                                    SIDE.BUY -> buySumAmount += it.second.origQty
                                    SIDE.SELL -> sellSumAmount += it.second.origQty
                                    else -> {}
                                }
                            }

                            if (buySumAmount > BigDecimal.ZERO)
                                sentOrder(
                                    amount = buySumAmount,
                                    orderSide = SIDE.BUY,
                                    price = currentPrice,
                                    orderType = TYPE.MARKET,
                                    isCloseOrder = true
                                )

                            if (sellSumAmount > BigDecimal.ZERO)
                                sentOrder(
                                    amount = sellSumAmount,
                                    orderSide = SIDE.SELL,
                                    price = currentPrice,
                                    orderType = TYPE.MARKET,
                                    isCloseOrder = true
                                )

                            ordersListForRemove.clear()
                        }

                        is Order -> {
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

                                            send("Executed LIMIT order:\n```json\n$order\n```", true)
                                        }
                                    } else send("Executed MARKET order:\n```json\n$msg\n```", true)
                                }
                            }
                        }

                        is BotEvent -> {
                            when (msg.type) {
                                BotEvent.Type.GET_PAIR_OPEN_ORDERS -> {
                                    val symbols = msg.text.split("[^a-zA-Z]+".toRegex())
                                        .filter { it.isNotBlank() }

                                    send(
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
                                        .forEach { send("${it.key}\n${it.value.joinToString("\n\n")}") }
                                }

                                BotEvent.Type.SHOW_BALANCES -> {
                                    send(
                                        "#AllBalances " +
                                                client.getBalances()
                                                    ?.toList()
                                                    ?.joinToString(prefix = "\n", separator = "\n") {
                                                        it.first + "\n" + it.second.joinToString(prefix = "\n", separator = "\n")
                                                    }
                                    )
                                }

                                BotEvent.Type.INTERRUPT -> {
                                    stream.interrupt()
                                    return
                                }

                                else -> send("${botSettings.name} Unsupported command: ${msg.type}")
                            }
                        }

                        else -> log?.warn("${botSettings.name} Unsupported message: $msg")
                    }

                    msg = if (isEmulate) client.nextEvent() /* only for test */
                    else queue.poll(waitTime)

                } catch (e: InterruptedException) {
                    log?.error("${botSettings.name} ${e.message}", e)
                    send("#Error_${botSettings.name}: \n${printTrace(e)}")
                    if (stopThread) return
                }
            } while (true)

        } catch (e: Exception) {
            log?.error("${botSettings.name} MAIN ERROR:\n", e)
            send("#Error_${botSettings.name}: \n${printTrace(e)}")
        } finally {
            interruptThis()
        }
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
                            if (botSettings.direction == DIRECTION.LONG && openOrder.side == SIDE.BUY) {
                                val orderSize = if (firstBalanceForOrderAmount) botSettings.orderSize
                                else botSettings.orderSize.div8(price)

                                val qty = orderSize.percent(10.toBigDecimal())

                                if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                                    orders[price.toPrice()] = openOrder
                                    log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                                }
                            } else if (botSettings.direction == DIRECTION.SHORT && openOrder.side == SIDE.SELL) {
                                val orderSize = if (firstBalanceForOrderAmount) botSettings.orderSize
                                else botSettings.orderSize.div8(price)

                                val qty = orderSize.percent(10.toBigDecimal())

                                if (openOrder.origQty in (openOrder.origQty - qty)..(openOrder.origQty + qty)) {
                                    orders[price.toPrice()] = openOrder
                                    log?.info("${botSettings.name} Synchronized Order:\n$openOrder")
                                }
                            }
                        }
                    }
                    price += botSettings.orderDistance
                }

                orders.forEach { (k, v) ->
                    openOrders
                        .find { (v.orderId == it.orderId || v.type == TYPE.MARKET) && v.price?.run { this in minRange..maxRange } ?: false }
                        ?: run {
                            ordersListForRemove.add(k to v)
                            log?.info("${botSettings.name} File order not found in exchange, file Order removed:\n$v")
                        }
                }

                ordersListForRemove.forEach { orders.remove(it.first) }
                ordersListForRemove.clear()

                if (botSettings.setCloseOrders) {
                    price = minRange
                    while (price <= maxRange) {
                        val priceIn = price.toPrice()

                        orders[priceIn]?.let { order -> log?.trace("{} Order already exist: {}", botSettings.name, order) }
                            ?: run {
                                orders[priceIn] = Order(
                                    orderId = "",
                                    pair = botSettings.pair,
                                    price = priceIn.toBigDecimal(),
                                    origQty = if (firstBalanceForOrderAmount) botSettings.orderSize
                                    else botSettings.orderSize.div8(price),
                                    executedQty = BigDecimal(0),
                                    side = if (botSettings.direction == DIRECTION.LONG) SIDE.SELL else SIDE.BUY,
                                    type = TYPE.MARKET,
                                    status = STATUS.FILLED,
                                    lastBorderPrice = BigDecimal.ZERO
                                )
                            }


                        price += botSettings.orderDistance
                    }
                }
            }

            else -> {}
        }
    }
}