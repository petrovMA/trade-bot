package bot.trade.exchanges

import bot.trade.database.data.entities.ActiveOrder
import bot.trade.database.service.ActiveOrdersService
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.CommonExchangeData
import bot.trade.exchanges.clients.ExchangeEnum.Companion.newClient
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.exchanges.params.OrderQuantity
import bot.trade.libs.*
import com.typesafe.config.Config
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque


class AlgorithmGrid(
    botSettings: BotSettings,
    exchangeBotsFiles: String,

    // activeOrdersService.getOrders(settings.name, settings.direction).toList().sortedBy { it.price }
    private val activeOrdersService: ActiveOrdersService,
    private val ordersService: OrderService? = null,

    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = exchangeEnum.newClient(api, sec),
    logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
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
    logMessageQueue = logMessageQueue,
    sendMessage = sendMessage
) {
    private val settings: BotSettingsGrid = botSettings as BotSettingsGrid
    private val minRange = settings.parameters.tradingRange.lowerBound
    private val maxRange = settings.parameters.tradingRange.upperBound

    //private val orders: HashMap<String, Order> = HashMap()
    private val ordersListForRemove: MutableList<String> = mutableListOf()
    private val tradePair = botSettings.pair
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val mainBalance = 0.0.toBigDecimal()
    private val balances: MutableMap<String, Balance> = HashMap()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {}

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Trade -> {
                prevPrice = currentPrice

                if (compareBigDecimal(currentPrice, BigDecimal(0)))
                    checkOrders(msg.price)

                currentPrice = msg.price

                from = if (from > msg.time) msg.time else from
                to = if (to < msg.time) msg.time else to

                if (currentPrice > minRange && currentPrice < maxRange) {

                    when (settings.ordersType) {
                        TYPE.MARKET -> {
                            if (activeOrdersService.count(settings.name) < settings.parameters.orderMaxQuantity) {
                                activeOrdersService.saveOrder(
                                    ActiveOrder(
                                        botName = settings.name,
                                        order = sentOrder(
                                            amount = calcAmount(settings.parameters.orderQuantity, currentPrice),
                                            orderSide = if (settings.direction == DIRECTION.LONG) SIDE.BUY
                                            else SIDE.SELL,
                                            orderType = settings.ordersType,
                                            price = BigDecimal(0.0),
                                            positionSide = settings.direction
                                        ).also {
                                            if (settings.direction == DIRECTION.LONG)
                                                it.lastBorderPrice = BigDecimal.ZERO
                                            else
                                                it.lastBorderPrice = BigDecimal(99999999999999L)
                                        })
                                )
                            }
                        }

                        TYPE.LIMIT -> {
                            when (settings.direction) {
                                DIRECTION.LONG -> {
                                    val distance = orderDistance(currentPrice, settings.parameters.orderDistance)

                                    val nearOrders = activeOrdersService.getOrderByPriceBetween(
                                        botName = settings.name,
                                        direction = settings.direction,
                                        minPrice = currentPrice - distance * BigDecimal(2),
                                        maxPrice = currentPrice + distance * BigDecimal(2)
                                    )
                                        .toList()


                                    val (nearOrderSellOrder, nearOrderBuyOrder) = nearOrders.run {
                                        filter { (it.orderSide == SIDE.SELL) || (it.orderSide == SIDE.BUY && it.stopPrice != null) } to
                                                filter { (it.orderSide == SIDE.BUY) || (it.orderSide == SIDE.SELL && it.stopPrice != null) }
                                    }

                                    if (nearOrderSellOrder.isEmpty() || nearOrderBuyOrder.isEmpty())
                                        checkOrders()

                                    val (nearSellOrders, nearBuyOrders) = nearOrders
                                        .run { filter { it.orderSide == SIDE.SELL } to filter { it.orderSide == SIDE.BUY } }

                                    if (nearSellOrders.isNotEmpty() && nearBuyOrders.isNotEmpty()) {
                                        val nearSellOrder =
                                            nearSellOrders.sortedBy { it.stopPrice ?: it.price }.first()
                                        val nearBuyOrder =
                                            nearBuyOrders.sortedBy { it.stopPrice ?: it.price }.last()

                                        if (currentPrice > nearSellOrder.price) {
                                            getOrder(settings.pair, nearSellOrder.orderId!!)?.let {
                                                if (it.status == STATUS.FILLED) {
                                                    createNextOrderFor(nearSellOrder)
                                                    ordersService?.saveOrder(nearSellOrder.toDbOrder())
                                                }
                                            } ?: run {
                                                log?.warn("Order not found, delete order: $nearSellOrder")
                                                activeOrdersService.deleteByOrderId(nearSellOrder.orderId)
                                            }
                                        }
                                        if (currentPrice < nearBuyOrder.price) {
                                            getOrder(settings.pair, nearBuyOrder.orderId!!)?.let {
                                                if (it.status == STATUS.FILLED) {
                                                    createNextOrderFor(nearBuyOrder)
                                                    ordersService?.saveOrder(nearBuyOrder.toDbOrder())
                                                }
                                            } ?: run {
                                                log?.warn("Order not found, delete order: $nearBuyOrder")
                                                activeOrdersService.deleteByOrderId(nearBuyOrder.orderId)
                                            }
                                        }
                                    }
                                }

                                DIRECTION.SHORT -> {
                                    TODO("Not Implemented DIRECTION.SHORT")
                                }
                            }
                        }

                        else -> throw UnsupportedOrderTypeException("Unsupported order type: ${settings.ordersType}")
                    }
                } else
                    log("Price ${currentPrice.toPrice()}, not in range: ${settings.parameters.tradingRange}")
            }

            is Balance -> balances[msg.asset] = msg

            is Order -> {
                log("${settings.name} Order update (STATUS = ${msg.status}): $msg")
                if (msg.pair == settings.pair) {
                    when (msg.status) {
                        STATUS.FILLED -> {
                            if (msg.type == TYPE.LIMIT) {
                                activeOrdersService.getOrderByOrderId(settings.name, msg.orderId)
                                    ?.let {
                                        createNextOrderFor(it)
                                    }
                            }
                        }

                        STATUS.NEW -> log("${settings.name} NEW order: $msg")
                        STATUS.PARTIALLY_FILLED -> log("${settings.name} PARTIALLY_FILLED order: $msg")
                        STATUS.CANCELED, STATUS.REJECTED -> {
                            log("Order inactive, delete order: $msg")
                            activeOrdersService.deleteByOrderId(msg.orderId)
                        }

                        else -> log("${settings.name} Unsupported order status: ${msg.status}")
                    }
                }
            }

            else -> log("Unsupported message: $msg")
        }
    }

    private fun checkOrders(price: BigDecimal = currentPrice) {
        val distance = orderDistance(price, settings.parameters.orderDistance)

        var priceSell = price + (distance / BigDecimal(2))
        var priceBuy = price - (distance / BigDecimal(2))
        var prevPriceSell = priceBuy
        var prevPriceBuy = priceSell
        var distanceSell: BigDecimal
        var distanceBuy: BigDecimal

        while (
            activeOrdersService.count(settings.name) < settings.parameters.orderMaxQuantity &&
            ((minRange < priceBuy && priceBuy < maxRange) || (minRange < priceSell && priceSell < maxRange))
        ) {
            if (minRange < priceBuy && priceBuy < maxRange) {

                distanceBuy = orderDistance(priceBuy, settings.parameters.orderDistance)

                val minPrice = priceBuy - distanceBuy

                activeOrdersService.getTopOrderByPriceBetweenIncludeMaxPrice(
                    botName = settings.name,
                    direction = settings.direction,
                    minPrice = minPrice,
                    maxPrice = prevPriceBuy
                )?.let { priceBuy = it.price!! }
                    ?: run {

                        val orderBuy = sentOrder(
                            price = priceBuy,
                            amount = calcAmount(settings.parameters.orderQuantity, priceBuy),
                            orderSide = SIDE.BUY,
                            orderType = TYPE.LIMIT,
                            positionSide = settings.direction
                        )

                        activeOrdersService.saveOrder(ActiveOrder(orderBuy, settings.name))
                        log("Save new order: $orderBuy")
                    }

                prevPriceBuy = minPrice
                priceBuy -= distanceBuy
            }


            if (minRange < priceSell && priceSell < maxRange) {

                distanceSell = orderDistance(priceSell, settings.parameters.orderDistance)

                val maxPrice = priceSell + distanceSell

                activeOrdersService.getTopOrderByPriceBetweenIncludeMinPrice(
                    botName = settings.name,
                    direction = settings.direction,
                    minPrice = prevPriceSell,
                    maxPrice = maxPrice
                )?.let { priceSell = it.price!! }
                    ?: run {
                        val orderSell = sentOrder(
                            price = priceSell,
                            amount = calcAmount(settings.parameters.orderQuantity, priceSell),
                            orderSide = SIDE.SELL,
                            orderType = TYPE.LIMIT,
                            positionSide = settings.direction
                        )

                        activeOrdersService.saveOrder(ActiveOrder(orderSell, settings.name))
                        log("Save new order: $orderSell")

                    }

                prevPriceSell = maxPrice
                priceSell += distanceSell
            }
        }
    }

    private fun createNextOrderFor(prevOrder: ActiveOrder) {
        log("Order for update: $prevOrder")
        val order = sentOrder(
            price = if (prevOrder.stopPrice != null)
                prevOrder.price!!
            else
                when (prevOrder.orderSide) {
                    SIDE.BUY -> prevOrder.price!! + orderDistance(
                        prevOrder.price,
                        settings.parameters.profitDistance
                    )

                    SIDE.SELL -> prevOrder.price!! - orderDistance(
                        prevOrder.price,
                        settings.parameters.profitDistance
                    )

                    else -> throw UnknownOrderSide("Error, side: ${prevOrder.orderSide}")
                },
            amount = if (prevOrder.stopPrice != null)
                calcAmount(settings.parameters.orderQuantity, prevOrder.price)
            else
                prevOrder.amount!!,
            orderSide = prevOrder.orderSide!!.reverse(),
            orderType = TYPE.LIMIT,
            positionSide = settings.direction
        )

        val updatedOrder = activeOrdersService.updateOrder(
            ActiveOrder(
                id = prevOrder.id,
                botName = settings.name,
                orderId = order.orderId,
                tradePair = settings.pair.toString(),
                amount = order.origQty,
                orderSide = order.side,
                price = prevOrder.price,
                stopPrice = if (prevOrder.stopPrice != null) null else order.price,
                direction = settings.direction
            )
        )

        log("Updated order: $updatedOrder")

        checkOrders()
    }

    override fun synchronizeOrders() {
        val openOrders = client.getOpenOrders(settings.pair)

        openOrders.forEach { order ->
            val orderDB = activeOrdersService.getOrderByOrderId(settings.name, order.orderId)

            if (orderDB == null) {
                cancelOrder(settings.pair, order)
                log("Sync - cancel order: $order")
            } else
                log("Sync - order is synchronized: $order")
        }

        val openOrdersKeys = client.getOpenOrders(settings.pair).map { it.orderId }

        activeOrdersService.getOrders(settings.name, settings.direction)
            .mapNotNull { it.orderId }
            .forEach {
                if (!openOrdersKeys.contains(it)) {
                    activeOrdersService.deleteByOrderId(it)
                    log("Sync - delete order: $it")
                }
            }
    }

    private fun calcAmount(orderQuantity: OrderQuantity, price: BigDecimal): BigDecimal =
        if (orderQuantity.isCounterBalance)
            orderQuantity.value.div8(price).round(botSettings.countOfDigitsAfterDotForAmount)
        else
            orderQuantity.value
}