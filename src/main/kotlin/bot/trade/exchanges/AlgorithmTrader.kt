package bot.trade.exchanges

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.service.ActiveOrdersService
import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.libs.TrendCalculator
import bot.trade.exchanges.libs.TrendCalculator.Trend.TREND
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.BotSettingsTrader
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.collections.HashMap


class AlgorithmTrader(
    botSettings: BotSettings,
    exchangeBotsFiles: String,
    private val activeOrdersService: ActiveOrdersService,
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
    private var trendCalculator: TrendCalculator? = null,
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
    private var futuresClient = super.client as ClientFutures
    private val ordersType = settings.ordersType
    private val strategy = settings.strategy
    private val notAutoCalcTrend = settings.trendDetector?.notAutoCalcTrend ?: true
    private val minOrderAmount = settings.minOrderAmount?.amount ?: BigDecimal.ZERO
    private val long = settings.parameters.longParameters
    private val short = settings.parameters.shortParameters
    private var trend: TrendCalculator.Trend? = null
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val ordersForExecute: MutableMap<Long, ActiveOrder> = HashMap()

    private var isInOrdersInitialized: Boolean = false

    private var maxPriceInOrderLong: BigDecimal? = null
    private var minPriceInOrderLong: BigDecimal? = null
    private var maxPriceInOrderShort: BigDecimal? = null
    private var minPriceInOrderShort: BigDecimal? = null

    private var hedgeModule: HedgeModule? = null
    private var positionLong: Position? = null
    private var positionShort: Position? = null

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {
//        futuresClient.switchMode("linear", 3, botSettings.pair, null)

        positionLong = futuresClient.getPositions(botSettings.pair).find { it.side.equals("BUY", true) }
        positionShort = futuresClient.getPositions(botSettings.pair).find { it.side.equals("SELL", true) }

        trendCalculator = trendCalculator ?: settings.trendDetector?.run {
            TrendCalculator(
                client = client,
                pair = botSettings.pair,
                hma1 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma1Period,
                hma2 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma2Period,
                hma3 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma3Period,
                rsi1 = rsi1.timeFrame.toDuration() to rsi1.rsiPeriod,
                rsi2 = rsi2.timeFrame.toDuration() to rsi2.rsiPeriod,
                endTime = endTimeForTrendCalculator,
                inputKlineInterval = inputKlineInterval?.let { it.toDuration() to it.toInterval() }
                    ?: (5.m() to INTERVAL.FIVE_MINUTES)
            ).apply { init() }
        }
    }

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Candlestick -> {

                trendCalculator?.addCandlesticks(msg)

                log(json(msg, false), File("logging/$path/kline.txt"))

                prevPrice = if (prevPrice == BigDecimal(0)) msg.close
                else currentPrice

                currentPrice = msg.close

                maxPriceInOrderLong = maxPriceInOrderLong ?: currentPrice
                minPriceInOrderLong = minPriceInOrderLong ?: currentPrice
                maxPriceInOrderShort = maxPriceInOrderShort ?: currentPrice
                minPriceInOrderShort = minPriceInOrderShort ?: currentPrice

                from = if (from > msg.closeTime) msg.closeTime else from
                to = if (to < msg.closeTime) msg.closeTime else to

                if (!notAutoCalcTrend) {
                    getTrend()?.let {
                        if (trend?.trend != it.trend) {
                            trend = it
                            send("#${botSettings.name} #Trend :\n```json\n${json(it)}\n```", true)
                        }
                    }
                }

                long?.entireTp?.let { entireTp ->
                    if (
                        entireTp.enabled &&
                        (trend?.trend in listOf(TREND.LONG, TREND.SHORT, null) || entireTp.enabledInHedge)
                    ) {

                        val triggerCount = activeOrdersService.count(settings.name, DIRECTION.LONG, SIDE.SELL)

                        if (positionLong != null && triggerCount >= entireTp.maxTriggerAmount) {

                            val withRealizedPnl = long.withRealizedPnl ?: false

                            val (profitPercent, currentTpDistance) = calcProfit(
                                positionLong!!,
                                currentPrice,
                                withRealizedPnl
                            )

                            val entireTpDistance = if (entireTp.tpDistance.usePercent)
                                positionLong!!.entryPrice.percent(entireTp.tpDistance.value)
                            else
                                entireTp.tpDistance.value

                            if (
                                currentTpDistance > entireTpDistance
                                || profitPercent > entireTp.maxProfitPercent
                                || profitPercent < (entireTp.maxLossPercent.negate())
                            ) {
                                resetLong()

                                maxPriceInOrderLong = null
                                minPriceInOrderLong = null
                            }
                        }
                    }
                }

                short?.entireTp?.let { entireTp ->
                    if (
                        entireTp.enabled &&
                        (trend?.trend in listOf(TREND.LONG, TREND.SHORT, null) || entireTp.enabledInHedge)
                    ) {

                        val triggerCount = activeOrdersService.count(settings.name, DIRECTION.SHORT, SIDE.BUY)

                        if (positionShort != null && triggerCount >= entireTp.maxTriggerAmount) {

                            val withRealizedPnl = short.withRealizedPnl ?: false

                            val (profitPercent, currentTpDistance) = calcProfit(
                                positionShort!!,
                                currentPrice,
                                withRealizedPnl
                            )

                            val entireTpDistance = if (entireTp.tpDistance.usePercent)
                                positionShort!!.entryPrice.percent(entireTp.tpDistance.value)
                            else
                                entireTp.tpDistance.value

                            if (
                                currentTpDistance > entireTpDistance
                                || profitPercent > entireTp.maxProfitPercent
                                || profitPercent < (entireTp.maxLossPercent.negate())
                            ) {
                                resetShort()

                                maxPriceInOrderShort = null
                                minPriceInOrderShort = null
                            }
                        }
                    }
                }

                when (trend?.trend) {
                    TREND.LONG -> {
                        resetShort()

                        maxPriceInOrderShort = null
                        minPriceInOrderShort = null

                        long?.let { params -> createOrdersForExecute(DIRECTION.LONG, params) }
                    }

                    TREND.SHORT -> {
                        resetLong()

                        maxPriceInOrderLong = null
                        minPriceInOrderLong = null

                        short?.let { params -> createOrdersForExecute(DIRECTION.SHORT, params) }
                    }

                    else -> {
                        val newHedgeModule = calcHedgeModule()

                        if (hedgeModule != newHedgeModule) {
                            log("Changed hedge module: oldModule = $hedgeModule, newModule = $newHedgeModule")

                            activeOrdersService.deleteByDirectionAndSide(settings.name, DIRECTION.LONG, SIDE.BUY)
                            activeOrdersService.deleteByDirectionAndSide(settings.name, DIRECTION.SHORT, SIDE.SELL)

                            minPriceInOrderLong = ordersLong()
                                .filter { it.orderSide == SIDE.BUY }
                                .mapNotNull { it.price?.toDouble() }
                                .minOrNull()
                                ?.toBigDecimal()
                                ?: currentPrice

                            maxPriceInOrderShort = ordersShort()
                                .filter { it.orderSide == SIDE.SELL }
                                .mapNotNull { it.price?.toDouble() }
                                .maxOrNull()
                                ?.toBigDecimal()
                                ?: currentPrice

                            hedgeModule = newHedgeModule
                        }

                        long?.let { params -> createOrdersForExecute(DIRECTION.LONG, params) }
                        short?.let { params -> createOrdersForExecute(DIRECTION.SHORT, params) }
                    }
                }

                executeOrders()


                //todo:: COMPARE POSITION WITH OPEN ORDERS (DEBUGGING)
                /*val pLong = positionLong
                val openLong = activeOrdersService
                    .getOrdersBySide(settings.name, DIRECTION.LONG, SIDE.SELL)


                val pShort = positionShort
                val openShort = activeOrdersService
                    .getOrdersBySide(settings.name, DIRECTION.SHORT, SIDE.BUY)

                if (
                    positionLong
                        ?.size
                        ?.run { compareBigDecimal(this, openLong.mapNotNull { it.amount }.sumOf { it }) } == false
                ) {
                    println(pLong)
                    println(openLong)
                }

                if (
                    positionShort
                        ?.size
                        ?.run { compareBigDecimal(this, openShort.mapNotNull { it.amount }.sumOf { it }) } == false
                ) {
                    println(pShort)
                    println(openShort)
                }*/
            }

            is Position -> {
                log("Current position:\n${json(msg, false)}")

                if (msg.side.equals("BUY", true))
                    positionLong = msg
                else if (msg.side.equals("SELL", true))
                    positionShort = msg
            }

            is Order -> {
                log?.info("{} Order update: {}", botSettings.name, msg)
                if (msg.pair == botSettings.pair) {
                    if (msg.status == STATUS.FILLED) {
                        if (msg.type == TYPE.LIMIT) {
                            activeOrdersService.getOrderByOrderId(settings.name, UUID.fromString(msg.orderId))
                                ?.let { orderForUpdate ->
                                    activeOrdersService.saveOrder(orderForUpdate.also { it.lastBorderPrice = null })
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

                        resetLong()
                        resetShort()

                        executeOrders()

                        stopThis()

                        return
                    }

                    BotEvent.Type.PAUSE -> {

                        executeOrders()

                        stopThis()

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
        params: BotSettingsTrader.TradeParameters.Parameters
    ) {
        if (currentPrice > params.minRange() && currentPrice < params.maxRange()) {

            if (isInOrdersInitialized.not()) {
                activeOrdersService.deleteByDirectionAndSide(settings.name, DIRECTION.LONG, SIDE.BUY)
                activeOrdersService.deleteByDirectionAndSide(settings.name, DIRECTION.SHORT, SIDE.SELL)

                isInOrdersInitialized = true
            }

            when (currentDirection) {
                DIRECTION.LONG -> {

                    while (currentPrice <= minPriceInOrderLong!!) {

                        val step = calcInPriceStep(minPriceInOrderLong!!, params, hedgeModule, currentDirection, true)
                        val price = (minPriceInOrderLong!! - step).round()

                        val order = activeOrdersService.getOrderByPrice(settings.name, currentDirection, price)

                        if (order != null)
                            log?.trace("{} Order already exist: {}", botSettings.name, order)
                        else
                            activeOrdersService.saveOrder(order(price, currentDirection, params))

                        minPriceInOrderLong =
                            activeOrdersService.getOrderWithMinPrice(settings.name, DIRECTION.LONG, currentPrice - step)
                                ?.price ?: maxPriceInOrderLong
                    }

                    maxPriceInOrderLong =
                        activeOrdersService.getOrderWithMaxPrice(settings.name, DIRECTION.LONG, currentPrice)
                            ?.price ?: minPriceInOrderLong

                    var step = calcInPriceStep(maxPriceInOrderLong!!, params, hedgeModule, currentDirection, false)

                    while (currentPrice > maxPriceInOrderLong!! + step) {

                        val ordersInGap = activeOrdersService.getOrderByPriceBetween(
                            settings.name,
                            DIRECTION.LONG,
                            minPrice = maxPriceInOrderLong!!,
                            maxPrice = maxPriceInOrderLong!! + (step * BigDecimal(2))
                        ).toList()

                        maxPriceInOrderLong = maxPriceInOrderLong!! + step

                        if (ordersInGap.isEmpty()) {

                            val order = activeOrdersService.getOrderByPrice(
                                settings.name,
                                currentDirection,
                                maxPriceInOrderLong!!
                            )

                            if (order != null)
                                log?.trace("{} Order already exist: {}", botSettings.name, order)
                            else
                                activeOrdersService.saveOrder(order(maxPriceInOrderLong!!, currentDirection, params))
                        }

                        step = calcInPriceStep(maxPriceInOrderLong!!, params, hedgeModule, currentDirection, false)
                    }
                }

                DIRECTION.SHORT -> {

                    while (currentPrice >= maxPriceInOrderShort!!) {

                        val step = calcInPriceStep(maxPriceInOrderShort!!, params, hedgeModule, currentDirection, false)
                        val price = (maxPriceInOrderShort!! + step).round()

                        val order = activeOrdersService.getOrderByPrice(settings.name, currentDirection, price)

                        if (order != null)
                            log?.trace("{} Order already exist: {}", botSettings.name, order)
                        else
                            activeOrdersService.saveOrder(order(price, currentDirection, params))

                        maxPriceInOrderShort =
                            activeOrdersService.getOrderWithMaxPrice(
                                settings.name,
                                DIRECTION.SHORT,
                                currentPrice + step
                            )
                                ?.price ?: minPriceInOrderShort
                    }

                    minPriceInOrderShort =
                        activeOrdersService.getOrderWithMinPrice(settings.name, DIRECTION.SHORT, BigDecimal(0))
                            ?.price ?: maxPriceInOrderShort

                    var step = calcInPriceStep(minPriceInOrderShort!!, params, hedgeModule, currentDirection, true)

                    while (currentPrice < minPriceInOrderShort!! - step) {

                        val ordersInGap = activeOrdersService.getOrderByPriceBetween(
                            settings.name,
                            DIRECTION.SHORT,
                            minPrice = minPriceInOrderShort!! - (step * BigDecimal(2)),
                            maxPrice = minPriceInOrderShort!!
                        ).toList()

                        minPriceInOrderShort = minPriceInOrderShort!! - step

                        if (ordersInGap.isEmpty()) {

                            val order = activeOrdersService.getOrderByPrice(
                                settings.name,
                                currentDirection,
                                minPriceInOrderShort!!
                            )

                            if (order != null)
                                log?.trace("{} Order already exist: {}", botSettings.name, order)
                            else
                                activeOrdersService.saveOrder(order(minPriceInOrderShort!!, currentDirection, params))
                        }

                        step = calcInPriceStep(minPriceInOrderShort!!, params, hedgeModule, currentDirection, true)
                    }
                }
            }
        } else
            log?.trace(
                "{} Price {}, not in range: {}",
                botSettings.name,
                format(currentPrice),
                params.minRange() to params.maxRange()
            )

        val triggerDistance =
            if (currentDirection == hedgeModule?.direction)
                (params.triggerDistance() * hedgeModule!!.module).round()
            else
                params.triggerDistance().round()

        when (currentDirection) {
            DIRECTION.LONG -> {
                ordersLong().forEach {
                    if (it.orderSide == SIDE.SELL) {
                        if (it.lastBorderPrice == null || it.lastBorderPrice!! < currentPrice) {
                            it.lastBorderPrice = currentPrice

                            it.stopPrice?.let { stopPrice ->
                                if (stopPrice < currentPrice - params.maxTpDistance())
                                    it.stopPrice = currentPrice - params.maxTpDistance()
                            } ?: run {
                                if (it.price!! <= (currentPrice - triggerDistance))
                                    it.stopPrice = currentPrice - params.minTpDistance()
                            }

                            activeOrdersService.saveOrder(it)
                        }
                        if (it.stopPrice?.run { this >= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, it)
                            ordersForExecute[it.id!!] = (it)
                        }
                    } else if (params.trailingInOrderDistance() != null && params.triggerInOrderDistance() != null) {
                        if (it.lastBorderPrice == null || it.lastBorderPrice!! > currentPrice) {
                            it.lastBorderPrice = currentPrice

                            if (
                                it.stopPrice?.run { this > currentPrice + params.trailingInOrderDistance()!! } == true ||
                                (it.stopPrice == null && it.price!! >= (currentPrice + params.triggerInOrderDistance()!!))
                            ) {
                                it.stopPrice = currentPrice + params.trailingInOrderDistance()!!
                            }

                            activeOrdersService.saveOrder(it)
                        }
                        if (it.stopPrice?.run { this <= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, it)
                            ordersForExecute[it.id!!] = (it)
                        }
                    } else {
                        if (it.orderSide == SIDE.BUY && currentPrice < it.price!!) {
                            ordersForExecute[it.id!!] = (it)
                        }
                    }
                }
            }

            DIRECTION.SHORT -> {
                ordersShort().forEach {
                    if (it.orderSide == SIDE.BUY) {
                        if (it.lastBorderPrice == null || it.lastBorderPrice!! > currentPrice) {
                            it.lastBorderPrice = currentPrice

                            it.stopPrice?.let { stopPrice ->
                                if (stopPrice > currentPrice + params.maxTpDistance())
                                    it.stopPrice = currentPrice + params.maxTpDistance()
                            } ?: run {
                                if (it.price!! >= (currentPrice + triggerDistance))
                                    it.stopPrice = currentPrice + params.minTpDistance()
                            }

                            activeOrdersService.saveOrder(it)
                        }
                        if (it.stopPrice?.run { this <= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, it)
                            ordersForExecute[it.id!!] = (it)
                        }
                    } else if (params.trailingInOrderDistance() != null && params.triggerInOrderDistance() != null) {
                        if (it.lastBorderPrice == null || it.lastBorderPrice!! < currentPrice) {
                            it.lastBorderPrice = currentPrice

                            if (
                                it.stopPrice?.run { this < currentPrice - params.trailingInOrderDistance()!! } == true ||
                                (it.stopPrice == null && it.price!! <= (currentPrice - params.triggerInOrderDistance()!!))
                            ) {
                                it.stopPrice = currentPrice - params.trailingInOrderDistance()!!
                            }

                            activeOrdersService.saveOrder(it)
                        }
                        if (it.stopPrice?.run { this >= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, it)
                            ordersForExecute[it.id!!] = (it)
                        }
                    } else {
                        if (it.orderSide == SIDE.SELL && currentPrice > it.price!!) {
                            ordersForExecute[it.id!!] = (it)
                        }
                    }
                }
            }
        }
    }

    private fun checkOrders() {
        ordersForExecute.forEach { (_, v) ->
            when (v.direction!!) {
                DIRECTION.LONG -> {
                    when (v.orderSide) {
                        SIDE.BUY -> {
                            activeOrdersService.saveOrder(
                                ActiveOrder(
                                    id = v.id,
                                    botName = settings.name,
                                    orderId = v.orderId,
                                    tradePair = botSettings.pair.toString(),
                                    price = v.price,
                                    amount = v.amount,
                                    orderSide = SIDE.SELL,
                                    direction = DIRECTION.LONG,
                                    lastBorderPrice = null,
                                    stopPrice = null
                                )
                            )
                        }

                        SIDE.SELL -> activeOrdersService.deleteByOrderId(v.orderId!!)
                        else -> log?.error("${botSettings.name} Unknown side: ${v.orderSide}")
                    }
                }

                DIRECTION.SHORT -> {
                    when (v.orderSide) {
                        SIDE.SELL -> {
                            activeOrdersService.saveOrder(
                                ActiveOrder(
                                    id = v.id,
                                    botName = settings.name,
                                    orderId = v.orderId,
                                    tradePair = botSettings.pair.toString(),
                                    price = v.price,
                                    amount = v.amount,
                                    orderSide = SIDE.BUY,
                                    direction = DIRECTION.SHORT,
                                    lastBorderPrice = null,
                                    stopPrice = null
                                )
                            )
                        }

                        SIDE.BUY -> activeOrdersService.deleteByOrderId(v.orderId!!)
                        else -> log?.error("${botSettings.name} Unknown side: ${v.orderSide}")
                    }
                }
            }
        }

        log("Price = '${currentPrice.toPrice()}' Orders for execute:\n${json(ordersForExecute)}")

        ordersForExecute.clear()
    }

    override fun synchronizeOrders() {}

    private fun syncOrders(
        params: BotSettingsTrader.TradeParameters.Parameters,
        orders: ObservableHashMap,
        currentDirection: DIRECTION
    ) {
        orders.readFromFile()

        ordersForExecute.clear()

        if (params.setCloseOrders && params.inOrderDistance.usePercent.not()) {
            var price = params.minRange()
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
                        origQty = calcAmount(params.orderQuantity(), price, currentDirection, hedgeModule),
                        executedQty = BigDecimal(0),
                        side = if (currentDirection == DIRECTION.LONG) SIDE.SELL else SIDE.BUY,
                        type = TYPE.MARKET,
                        status = STATUS.FILLED,
                        lastBorderPrice = null
                    )
                }

                price += params.orderDistance().value
            }
        }
    }

    private fun calcInPriceStep(
        prevPrice: BigDecimal,
        params: BotSettingsTrader.TradeParameters.Parameters,
        hedgeModule: HedgeModule?,
        currentDirection: DIRECTION,
        isStepDown: Boolean = false
    ): BigDecimal {
        val distance = if (params.orderDistance().usePercent) {
            if (isStepDown)
                params.orderDistance().value.let {
                    prevPrice.round() / (BigDecimal(100).round() + it.round()) * it.round()
                }
            else
                prevPrice.round() / BigDecimal(100).round() * params.orderDistance().value.round()

        } else params.orderDistance().value

        val step = if (hedgeModule == null || hedgeModule.direction == currentDirection)
            distance
        else
            (distance * hedgeModule.module)

        return params.counterDistance?.let { counterDistance ->
            when (currentDirection) {
                DIRECTION.LONG -> {
                    if (positionLong != null && calcProfit(
                            positionLong!!,
                            prevPrice,
                            params.withRealizedPnl
                        ).first < BigDecimal(0)
                    )
                        (step * counterDistance).round()
                    else
                        step.round()
                }

                DIRECTION.SHORT -> {
                    if (positionShort != null && calcProfit(
                            positionShort!!,
                            prevPrice,
                            params.withRealizedPnl
                        ).first < BigDecimal(0)
                    )
                        (step * counterDistance).round()
                    else
                        step.round()
                }
            }
        } ?: step.round()
    }

    private fun calcAmount(
        amount: BigDecimal,
        price: BigDecimal,
        direction: DIRECTION,
        hedgeModule: HedgeModule?
    ): BigDecimal {

        val orderAmount =
            if (direction == hedgeModule?.direction)
                amount * hedgeModule.module
            else amount

        return if (firstBalanceForOrderAmount) settings.minOrderAmount?.countOfDigitsAfterDotForAmount
            ?.let { orderAmount.round(it) }
            ?: orderAmount.round(botSettings.countOfDigitsAfterDotForAmount)
        else settings.minOrderAmount?.countOfDigitsAfterDotForAmount?.let { orderAmount.div8(price).round(it) }
            ?: orderAmount.div8(price).round(botSettings.countOfDigitsAfterDotForAmount)
    }

    fun calcHedgeModule(): HedgeModule? =
        if (settings.autoBalance.not() || trend?.trend !in listOf(TREND.HEDGE, TREND.FLAT)) null
        else {
            val openLongPosition: BigDecimal = activeOrdersService
                .getOrdersBySide(settings.name, DIRECTION.LONG, SIDE.SELL)
                .mapNotNull { it.amount }
                .sumOf { it }

            val openShortPosition: BigDecimal = activeOrdersService
                .getOrdersBySide(settings.name, DIRECTION.SHORT, SIDE.BUY)
                .mapNotNull { it.amount }
                .sumOf { it }

            if (openLongPosition == openShortPosition)
                null
            else if (openLongPosition > openShortPosition) {
                if (!compareBigDecimal(openLongPosition, BigDecimal(0)) && !compareBigDecimal(
                        openShortPosition,
                        BigDecimal(0)
                    )
                )
                    HedgeModule((BigDecimal(2) - (openShortPosition / openLongPosition)).round(), DIRECTION.SHORT)
                else
                    HedgeModule(BigDecimal(2), DIRECTION.SHORT)
            } else {
                if (!compareBigDecimal(openLongPosition, BigDecimal(0)) && !compareBigDecimal(
                        openShortPosition,
                        BigDecimal(0)
                    )
                )
                    HedgeModule((BigDecimal(2) - (openLongPosition / openShortPosition)).round(), DIRECTION.LONG)
                else
                    HedgeModule(BigDecimal(2), DIRECTION.LONG)
            }
        }

    private fun resetLong() {
        ordersForExecute.putAll(
            activeOrdersService.getOrdersBySide(settings.name, DIRECTION.LONG, SIDE.SELL)
                .map { it.id!! to it }
        )

        activeOrdersService.deleteByDirection(settings.name, DIRECTION.LONG)
    }

    private fun resetShort() {
        ordersForExecute.putAll(
            activeOrdersService.getOrdersBySide(settings.name, DIRECTION.SHORT, SIDE.BUY)
                .map { it.id!! to it }
        )

        activeOrdersService.deleteByDirection(settings.name, DIRECTION.SHORT)
    }

    private fun order(
        price: BigDecimal,
        direction: DIRECTION,
        params: BotSettingsTrader.TradeParameters.Parameters
    ) = ActiveOrder(
        orderId = UUID.randomUUID(),
        botName = settings.name,
        tradePair = botSettings.pair.toString(),
        price = price,
        amount = calcAmount(params.orderQuantity(), price, direction, hedgeModule),
        orderSide = if (direction == DIRECTION.LONG) SIDE.BUY
        else SIDE.SELL,
        direction = direction,
        lastBorderPrice = price,
        stopPrice = null
    )

    private fun calcProfit(
        position: Position,
        currPrice: BigDecimal,
        withRealizedPnl: Boolean?
    ): Pair<BigDecimal, BigDecimal> {

        val inPrice = if (withRealizedPnl == true) position.breakEvenPrice
        else position.entryPrice

        val profitAbsolute = if (position.side.equals("sell", true))
            inPrice - currPrice
        else
            currPrice - inPrice

        val profitPercent = if (!compareBigDecimal(inPrice, BigDecimal(0)))
            profitAbsolute.div8(inPrice.div8(BigDecimal(100)))
        else
            BigDecimal(0)

        return profitPercent to profitAbsolute.abs()
    }

    private fun executeOrders() {

        var longAmount = BigDecimal.ZERO
        var shortAmount = BigDecimal.ZERO

        ordersForExecute.forEach { (_, v) ->
            when (v.direction!!) {
                DIRECTION.LONG -> when (v.orderSide) {
                    SIDE.BUY -> longAmount += v.amount!!
                    SIDE.SELL -> longAmount -= v.amount!!
                    else -> {}
                }

                DIRECTION.SHORT -> when (v.orderSide) {
                    SIDE.BUY -> shortAmount -= v.amount!!
                    SIDE.SELL -> shortAmount += v.amount!!
                    else -> {}
                }
            }
        }

        if (longAmount.abs() > calcAmount(minOrderAmount, currentPrice, DIRECTION.LONG, hedgeModule)) {
            log(
                "LONG Orders before execute:\n${json(ordersLong(), false)}",
                File("logging/$path/long_orders.txt")
            )
            checkOrders()
            sentOrder(
                amount = longAmount.abs(),
                orderSide = if (longAmount > BigDecimal.ZERO) SIDE.BUY else SIDE.SELL,
                price = currentPrice,
                orderType = TYPE.MARKET,
                positionSide = DIRECTION.LONG,
                isReduceOnly = longAmount < BigDecimal.ZERO
            )
            log("LONG Orders after execute:\n${json(ordersLong(), false)}", File("logging/$path/long_orders.txt"))
        }

        if (shortAmount.abs() > calcAmount(minOrderAmount, currentPrice, DIRECTION.SHORT, hedgeModule)) {
            log(
                "SHORT Orders before execute:\n${json(ordersShort(), false)}",
                File("logging/$path/short_orders.txt")
            )
            checkOrders()
            sentOrder(
                amount = shortAmount.abs(),
                orderSide = if (shortAmount > BigDecimal.ZERO) SIDE.SELL else SIDE.BUY,
                price = currentPrice,
                orderType = TYPE.MARKET,
                positionSide = DIRECTION.SHORT,
                isReduceOnly = shortAmount < BigDecimal.ZERO
            )
            log(
                "SHORT Orders after execute:\n${json(ordersShort(), false)}",
                File("logging/$path/short_orders.txt")
            )
        }
    }

    fun orderBorders() =
        listOf(maxPriceInOrderLong, minPriceInOrderLong, maxPriceInOrderShort, minPriceInOrderShort, currentPrice)

    fun getTrend(): TrendCalculator.Trend? = trendCalculator?.getTrend()

    fun orders() = Triple(botSettings, ordersLong(), ordersShort())

    fun positions() = positionLong to positionShort

    data class HedgeModule(val module: BigDecimal, val direction: DIRECTION)

    override fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal = TODO("Not yet implemented")

    private fun ordersLong() = activeOrdersService.getOrders(settings.name, DIRECTION.LONG)
    private fun ordersShort() = activeOrdersService.getOrders(settings.name, DIRECTION.SHORT)
}