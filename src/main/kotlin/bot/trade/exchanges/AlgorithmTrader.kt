package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.libs.TrendCalculator
import bot.trade.exchanges.libs.TrendCalculator.Trend.TREND
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
//    activeOrdersService: ActiveOrdersService,
    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = newClient(exchangeEnum, api, sec),
    private val tempUrlCalcHma: String,
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
    private var futuresClient = super.client as ClientFutures
    private val ordersType = settings.ordersType
    private val strategy = settings.strategy
    private val notAutoCalcTrend = settings.trendDetector?.notAutoCalcTrend ?: true
    private val minOrderAmount = settings.minOrderAmount?.amount ?: BigDecimal.ZERO
    private val long = settings.parameters.longParameters
    private val short = settings.parameters.shortParameters
    private var trendCalculator: TrendCalculator? = null
    private var trend: TrendCalculator.Trend? = null
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val ordersForExecute: MutableMap<Pair<DIRECTION, String>, Order> = mutableMapOf()

    private var isInOrdersInitialized: Boolean = false

    private var maxPriceInOrderLong: BigDecimal? = null
    private var minPriceInOrderLong: BigDecimal? = null
    private var maxPriceInOrderShort: BigDecimal? = null
    private var minPriceInOrderShort: BigDecimal? = null

    private var hedgeModule: HedgeModule? = null
    private var positionLong: Position? = null // todo:: Add more variables (size, liqPrice etc)
    private var positionShort: Position? = null // todo:: Add more variables (size, liqPrice etc)

    private val ordersLong: MutableMap<String, Order> = if (isEmulate.not()) ObservableHashMap(
        filePath = "$path/orders_long".also {
            if (isEmulate.not() && File(it).isDirectory.not()) Files.createDirectories(Paths.get(it))
        },
        keyToFileName = { key -> key.replace('.', '_') + ".json" },
        fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
    )
    else mutableMapOf()

    private val ordersShort: MutableMap<String, Order> = if (isEmulate.not()) ObservableHashMap(
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
//        futuresClient.switchMode("linear", 3, botSettings.pair, null)

        positionLong = futuresClient.getPositions(botSettings.pair).find { it.side.equals("BUY", true) }
        positionShort = futuresClient.getPositions(botSettings.pair).find { it.side.equals("SELL", true) }

        trendCalculator = settings.trendDetector?.run {
            TrendCalculator(
                client = client,
                pair = botSettings.pair,
                hma1 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma1Period,
                hma2 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma2Period,
                hma3 = hmaParameters.timeFrame.toDuration() to hmaParameters.hma3Period,
                rsi1 = rsi1.timeFrame.toDuration() to rsi1.rsiPeriod,
                rsi2 = rsi2.timeFrame.toDuration() to rsi2.rsiPeriod,
                tempUrlCalcHma = tempUrlCalcHma,
                endTime = endTimeForTrendCalculator
            )
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

                settings.parameters.longParameters?.entireTp?.let { entireTp ->
                    if (
                        entireTp.enabled &&
                        (trend?.trend in listOf(TREND.LONG, TREND.SHORT, null) || entireTp.enabledInHedge)
                    ) {

                        val triggerCount = ordersLong.filter { it.value.side == SIDE.SELL }.count()

                        if (positionLong != null && triggerCount >= entireTp.maxTriggerAmount) {

                            val (profitPercent, currentTpDistance) = calcProfit(positionLong!!, currentPrice)

                            val entireTpDistance = if (entireTp.tpDistance.usePercent)
                                positionLong!!.entryPrice.percent(entireTp.tpDistance.distance)
                            else
                                entireTp.tpDistance.distance

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

                settings.parameters.shortParameters?.entireTp?.let { entireTp ->
                    if (
                        entireTp.enabled &&
                        (trend?.trend in listOf(TREND.LONG, TREND.SHORT, null) || entireTp.enabledInHedge)
                    ) {

                        val triggerCount = ordersShort.filter { it.value.side == SIDE.SELL }.count()

                        if (positionShort != null && triggerCount >= entireTp.maxTriggerAmount) {

                            val (profitPercent, currentTpDistance) = calcProfit(positionShort!!, currentPrice)

                            val entireTpDistance = if (entireTp.tpDistance.usePercent)
                                positionShort!!.entryPrice.percent(entireTp.tpDistance.distance)
                            else
                                entireTp.tpDistance.distance

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

                            ordersShort.entries.removeIf { (_, v) -> v.side == SIDE.SELL }
                            ordersLong.entries.removeIf { (_, v) -> v.side == SIDE.BUY }

                            minPriceInOrderLong = ordersLong.values
                                .filter { it.side == SIDE.BUY }
                                .mapNotNull { it.price?.toDouble() }
                                .minOrNull()
                                ?.toBigDecimal()
                                ?: currentPrice

                            maxPriceInOrderShort = ordersShort.values
                                .filter { it.side == SIDE.SELL }
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

                        resetLong()
                        resetShort()

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
    ) = when (currentDirection) {
        DIRECTION.LONG -> ordersLong
        DIRECTION.SHORT -> ordersShort
    }.let { orders ->
        if (currentPrice > params.minRange() && currentPrice < params.maxRange()) {

            if (isInOrdersInitialized.not()) {
                orders.entries.removeIf { (_, v) ->
                    currentDirection == DIRECTION.LONG && v.side == SIDE.BUY ||
                            currentDirection == DIRECTION.SHORT && v.side == SIDE.SELL
                }
                isInOrdersInitialized = true
            }

            when (currentDirection) {
                DIRECTION.LONG -> {

                    while (currentPrice <= minPriceInOrderLong!!) {

                        val step = calcInPriceStep(minPriceInOrderLong!!, params, hedgeModule, currentDirection, true)
                        val price = (minPriceInOrderLong!! - step).round()

                        if (orders[price.toPrice()] != null)
                            log?.trace("{} Order already exist: {}", botSettings.name, orders[price.toPrice()])
                        else
                            orders[price.toPrice()] = order(price, currentDirection, params)

                        minPriceInOrderLong = orders.entries
                            .filter { it.value.side == SIDE.BUY }
                            .mapNotNull { it.value.price }
                            .minByOrNull { it.toDouble() }
                            ?: maxPriceInOrderLong
                    }

                    minPriceInOrderLong = orders.entries
                        .filter { it.value.side == SIDE.BUY }
                        .mapNotNull { it.value.price }
                        .minByOrNull { it.toDouble() }
                        ?: maxPriceInOrderLong

                    maxPriceInOrderLong = orders.entries
                        .filter { it.value.side == SIDE.BUY }
                        .mapNotNull { it.value.price }
                        .maxByOrNull { it.toDouble() }
                        ?: minPriceInOrderLong

                    var step = calcInPriceStep(maxPriceInOrderLong!!, params, hedgeModule, currentDirection, false)

                    while (currentPrice > maxPriceInOrderLong!! + step) {

                        val ordersInGap = getOrdersBetween(
                            orders = orders,
                            minPrice = maxPriceInOrderLong!!,
                            maxPrice = maxPriceInOrderLong!! + (step * BigDecimal(2))
                        )

                        maxPriceInOrderLong = maxPriceInOrderLong!! + step

                        if (ordersInGap.isEmpty()) {
                            if (orders[maxPriceInOrderLong!!.toPrice()] != null)
                                log?.trace(
                                    "{} Order already exist: {}",
                                    botSettings.name,
                                    orders[maxPriceInOrderLong!!.toPrice()]
                                )
                            else
                                orders[maxPriceInOrderLong!!.toPrice()] =
                                    order(maxPriceInOrderLong!!, currentDirection, params)
                        }

                        step = calcInPriceStep(maxPriceInOrderLong!!, params, hedgeModule, currentDirection, false)
                    }
                }

                DIRECTION.SHORT -> {

                    while (currentPrice >= maxPriceInOrderShort!!) {

                        val step = calcInPriceStep(maxPriceInOrderShort!!, params, hedgeModule, currentDirection, false)
                        val price = (maxPriceInOrderShort!! + step).round()

                        if (orders[price.toPrice()] != null)
                            log?.trace("{} Order already exist: {}", botSettings.name, orders[price.toPrice()])
                        else
                            orders[price.toPrice()] = order(price, currentDirection, params)

                        maxPriceInOrderShort = orders.entries
                            .filter { it.value.side == SIDE.SELL }
                            .mapNotNull { it.value.price }
                            .maxByOrNull { it.toDouble() }
                            ?: minPriceInOrderShort
                    }

                    maxPriceInOrderShort = orders.entries
                        .filter { it.value.side == SIDE.SELL }
                        .mapNotNull { it.value.price }
                        .maxByOrNull { it.toDouble() }
                        ?: minPriceInOrderShort

                    minPriceInOrderShort = orders.entries
                        .filter { it.value.side == SIDE.SELL }
                        .mapNotNull { it.value.price }
                        .minByOrNull { it.toDouble() }
                        ?: maxPriceInOrderShort

                    var step = calcInPriceStep(minPriceInOrderShort!!, params, hedgeModule, currentDirection, true)

                    while (currentPrice < minPriceInOrderShort!! - step) {

                        val ordersInGap = getOrdersBetween(
                            orders = orders,
                            minPrice = minPriceInOrderShort!! - (step * BigDecimal(2)),
                            maxPrice = minPriceInOrderShort!!
                        )

                        minPriceInOrderShort = minPriceInOrderShort!! - step

                        if (ordersInGap.isEmpty()) {
                            if (orders[minPriceInOrderShort!!.toPrice()] != null)
                                log?.trace(
                                    "{} Order already exist: {}",
                                    botSettings.name,
                                    orders[minPriceInOrderShort!!.toPrice()]
                                )
                            else
                                orders[minPriceInOrderShort!!.toPrice()] =
                                    order(minPriceInOrderShort!!, currentDirection, params)
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
                val ordersForUpdate = orders.filter { (_, v) -> v.type == TYPE.MARKET }

                ordersForUpdate.forEach { (k, v) ->
                    if (v.side == SIDE.SELL) {
                        if (v.lastBorderPrice == null || v.lastBorderPrice!! < currentPrice) {
                            v.lastBorderPrice = currentPrice

                            v.stopPrice?.let {
                                if (it < currentPrice - params.maxTpDistance())
                                    v.stopPrice = currentPrice - params.maxTpDistance()
                            } ?: run {
                                if (k.toBigDecimal() <= (currentPrice - triggerDistance))
                                    v.stopPrice = currentPrice - params.minTpDistance()
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this >= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersForExecute[currentDirection to k] = v
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
                            ordersForExecute[currentDirection to k] = v
                        }
                    } else {
                        if (v.side == SIDE.BUY && currentPrice < k.toBigDecimal()) {
                            ordersForExecute[currentDirection to k] = v
                        }
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
                                if (k.toBigDecimal() >= (currentPrice + triggerDistance))
                                    v.stopPrice = currentPrice + params.minTpDistance()
                            }

                            orders[k] = v
                        }
                        if (v.stopPrice?.run { this <= currentPrice } == true) {
                            log?.debug("{} Order close: {}", botSettings.name, v)
                            ordersForExecute[currentDirection to k] = v
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
                            ordersForExecute[currentDirection to k] = v
                        }
                    } else {
                        if (v.side == SIDE.SELL && currentPrice > k.toBigDecimal()) {
                            ordersForExecute[currentDirection to k] = v
                        }
                    }
                }
            }
        }
    }

    private fun checkOrders() {
        ordersForExecute.forEach { (k, v) ->
            when (k.first) {
                DIRECTION.LONG -> {
                    when (v.side) {
                        SIDE.BUY -> {
                            ordersLong[k.second] = Order(
                                orderId = "",
                                pair = botSettings.pair,
                                price = BigDecimal(k.second),
                                origQty = v.origQty,
                                executedQty = BigDecimal(0),
                                side = SIDE.SELL,
                                type = TYPE.MARKET,
                                status = STATUS.NEW,
                                lastBorderPrice = null,
                                stopPrice = null
                            )
                        }

                        SIDE.SELL -> ordersLong.remove(k.second)
                        else -> log?.error("${botSettings.name} Unknown side: ${v.side}")
                    }
                }

                DIRECTION.SHORT -> {
                    when (v.side) {
                        SIDE.SELL -> {
                            ordersShort[k.second] = Order(
                                orderId = "",
                                pair = botSettings.pair,
                                price = BigDecimal(k.second),
                                origQty = v.origQty,
                                executedQty = BigDecimal(0),
                                side = SIDE.BUY,
                                type = TYPE.MARKET,
                                status = STATUS.NEW,
                                lastBorderPrice = null,
                                stopPrice = null
                            )
                        }

                        SIDE.BUY -> ordersShort.remove(k.second)
                        else -> log?.error("${botSettings.name} Unknown side: ${v.side}")
                    }
                }
            }
        }

        log("Price = '${currentPrice.toPrice()}' Orders for execute:\n${json(ordersForExecute)}")

        ordersForExecute.clear()
    }

    override fun synchronizeOrders() {
        if (ordersLong is ObservableHashMap) long?.let {
            syncOrders(it, ordersLong, DIRECTION.LONG)

            maxPriceInOrderLong = ordersLong.values
                .mapNotNull { o -> o.price?.toDouble() }
                .maxOrNull()
                ?.toBigDecimal()

            minPriceInOrderLong = ordersLong.values
                .mapNotNull { o -> o.price?.toDouble() }
                .minOrNull()
                ?.toBigDecimal()
        }
        if (ordersShort is ObservableHashMap) short?.let {
            syncOrders(it, ordersShort, DIRECTION.SHORT)

            maxPriceInOrderShort = ordersShort.values
                .mapNotNull { o -> o.price?.toDouble() }
                .maxOrNull()
                ?.toBigDecimal()

            minPriceInOrderShort = ordersShort.values
                .mapNotNull { o -> o.price?.toDouble() }
                .minOrNull()
                ?.toBigDecimal()
        }
    }

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

                price += params.orderDistance().distance
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
                params.orderDistance().distance.let {
                    prevPrice.round() / (BigDecimal(100).round() + it.round()) * it.round()
                }
            else
                prevPrice.round() / BigDecimal(100).round() * params.orderDistance().distance.round()

        } else params.orderDistance().distance

        val step = if (hedgeModule == null || hedgeModule.direction == currentDirection)
            distance
        else
            (distance * hedgeModule.module)

        return params.counterDistance?.let { counterDistance ->
            when (currentDirection) {
                DIRECTION.LONG -> {
                    if (positionLong != null && calcProfit(positionLong!!, prevPrice).first < BigDecimal(0))
                        (step * counterDistance).round()
                    else
                        step.round()
                }

                DIRECTION.SHORT -> {
                    if (positionShort != null && calcProfit(positionShort!!, prevPrice).first < BigDecimal(0))
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

    fun getTrend(): TrendCalculator.Trend? = trendCalculator?.getTrend()

    fun orders() = Triple(botSettings, ordersLong, ordersShort)

    fun calcHedgeModule(): HedgeModule? =
        if (settings.autoBalance.not() || trend?.trend !in listOf(TREND.HEDGE, TREND.FLAT)) null
        else {
            val openLongPosition: BigDecimal = ordersLong
                .filter { it.value.side == SIDE.SELL }
                .map { it.value }
                .sumOf { it.origQty }

            val openShortPosition: BigDecimal = ordersShort
                .filter { it.value.side == SIDE.BUY }
                .map { it.value }
                .sumOf { it.origQty }

            if (openLongPosition == openShortPosition)
                null
            else if (openLongPosition > openShortPosition) {
                if (openLongPosition != BigDecimal(0) && openShortPosition != BigDecimal(0))
                    HedgeModule((BigDecimal(2) - (openShortPosition / openLongPosition)).round(), DIRECTION.SHORT)
                else
                    HedgeModule(BigDecimal(2), DIRECTION.SHORT)
            } else {
                if (openLongPosition != BigDecimal(0) && openShortPosition != BigDecimal(0))
                    HedgeModule((BigDecimal(2) - (openLongPosition / openShortPosition)).round(), DIRECTION.LONG)
                else
                    HedgeModule(BigDecimal(2), DIRECTION.LONG)
            }
        }

    private fun resetLong() {
        ordersLong
            .filter { (_, v) -> v.side == SIDE.SELL }
            .forEach { (k, v) -> ordersForExecute[DIRECTION.LONG to k] = v }

        ordersLong.clear()
    }

    private fun resetShort() {
        ordersShort
            .filter { (_, v) -> v.side == SIDE.BUY }
            .forEach { (k, v) -> ordersForExecute[DIRECTION.SHORT to k] = v }

        ordersShort.clear()
    }

    private fun order(
        price: BigDecimal,
        direction: DIRECTION,
        params: BotSettingsTrader.TradeParameters.Parameters
    ) = Order(
        orderId = "",
        pair = botSettings.pair,
        price = price,
        origQty = calcAmount(params.orderQuantity(), price, direction, hedgeModule),
        executedQty = BigDecimal(0),
        side = if (direction == DIRECTION.LONG) SIDE.BUY
        else SIDE.SELL,
        type = TYPE.MARKET,
        status = STATUS.NEW,
        lastBorderPrice = price,
        stopPrice = null
    )

    private fun calcProfit(position: Position, currPrice: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val profitAbsolute = if (position.side.equals("sell", true))
            position.entryPrice - currPrice
        else
            currPrice - position.entryPrice

        val profitPercent = if (position.entryPrice != BigDecimal(0))
            profitAbsolute.div8(position.entryPrice.div8(BigDecimal(100)))
        else
            BigDecimal(0)

        return profitPercent to profitAbsolute.abs()
    }

    private fun executeOrders() {

        var longAmount = BigDecimal.ZERO
        var shortAmount = BigDecimal.ZERO

        ordersForExecute.forEach { (k, v) ->
            when (k.first) {
                DIRECTION.LONG -> when (v.side) {
                    SIDE.BUY -> longAmount += v.origQty
                    SIDE.SELL -> longAmount -= v.origQty
                    else -> {}
                }

                DIRECTION.SHORT -> when (v.side) {
                    SIDE.BUY -> shortAmount -= v.origQty
                    SIDE.SELL -> shortAmount += v.origQty
                    else -> {}
                }
            }
        }

        if (longAmount.abs() > calcAmount(minOrderAmount, currentPrice, DIRECTION.LONG, hedgeModule)) {
            log(
                "LONG Orders before execute:\n${json(ordersLong, false)}",
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
            log("LONG Orders after execute:\n${json(ordersLong, false)}", File("logging/$path/long_orders.txt"))
        }

        if (shortAmount.abs() > calcAmount(minOrderAmount, currentPrice, DIRECTION.SHORT, hedgeModule)) {
            log(
                "SHORT Orders before execute:\n${json(ordersShort, false)}",
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
                "SHORT Orders after execute:\n${json(ordersShort, false)}",
                File("logging/$path/short_orders.txt")
            )
        }
    }

    fun orderBorders() =
        listOf(maxPriceInOrderLong, minPriceInOrderLong, maxPriceInOrderShort, minPriceInOrderShort, currentPrice)

    data class HedgeModule(val module: BigDecimal, val direction: DIRECTION)

    private fun getOrdersBetween(orders: MutableMap<String, Order>, minPrice: BigDecimal, maxPrice: BigDecimal) =
        orders.entries.filter { minPrice < it.value.price!! && it.value.price!! < maxPrice }

    override fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal = TODO("Not yet implemented")
}