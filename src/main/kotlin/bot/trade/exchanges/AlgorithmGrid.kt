package bot.trade.exchanges

import bot.trade.database.service.ActiveOrdersService
import bot.trade.exchanges.*
import bot.trade.exchanges.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.CommonExchangeData
import bot.trade.exchanges.params.BotSettingsGrid
import bot.trade.libs.*
import com.typesafe.config.Config
import mu.KotlinLogging
import java.math.BigDecimal
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.collections.HashMap


class AlgorithmGrid(
    botSettings: BotSettingsGrid,
    exchangeBotsFiles: String,
    private val activeOrdersService: ActiveOrdersService,
    queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    exchangeEnum: ExchangeEnum = ExchangeEnum.valueOf(botSettings.exchange.uppercase(Locale.getDefault())),
    conf: Config = getConfigByExchange(exchangeEnum)!!,
    api: String = conf.getString("api"),
    sec: String = conf.getString("sec"),
    client: Client = newClient(exchangeEnum, api, sec),
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
    private val settings: BotSettingsGrid = super.botSettings as BotSettingsGrid
    private val minRange = botSettings.parameters.tradingRange.lowerBound
    private val maxRange = botSettings.parameters.tradingRange.upperBound
    //private val orders: HashMap<String, Order> = HashMap()
    private val ordersListForRemove: MutableList<String> = mutableListOf()
    private val tradePair = botSettings.pair
    private val log = if (isLog) KotlinLogging.logger {} else null
    private val mainBalance = 0.0.toBigDecimal()

    var from: Long = Long.MAX_VALUE
    var to: Long = Long.MIN_VALUE

    override fun setup() {
        TODO("setup() not implemented")
    }

    override fun synchronizeOrders() {
        TODO("synchronizeOrders() not implemented")
    }

    override fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal = TODO("Not yet implemented")

    override fun handle(msg: CommonExchangeData?) {
        when (msg) {
            is Trade -> {
                prevPrice = currentPrice
                currentPrice = msg.price

                from = if (from > msg.time) msg.time else from
                to = if (to < msg.time) msg.time else to

                if (currentPrice > minRange && currentPrice < maxRange) {

                    val price = (currentPrice - (currentPrice % settings.parameters.orderDistance.value)).toPrice()

                    when (settings.ordersType) {
                        TYPE.MARKET -> {
                            orders[price] ?: run {
                                if (orders.size < settings.parameters.orderMaxQuantity) {
                                    orders[price] =
                                        sentOrder(
                                            amount = settings.parameters.orderQuantity.value,
                                            orderSide = if (settings.direction == DIRECTION.LONG) SIDE.BUY
                                            else SIDE.SELL,
                                            orderType = settings.ordersType,
                                            price = BigDecimal(0.0)
                                        ).also {
                                            if (settings.direction == DIRECTION.LONG)
                                                it.lastBorderPrice = BigDecimal.ZERO
                                            else
                                                it.lastBorderPrice = BigDecimal(99999999999999L)
                                        }
                                }
                            }
                        }

                        TYPE.LIMIT -> {
                            when (settings.direction) {
                                DIRECTION.LONG -> {
                                    var keyPrice = price.toBigDecimal()
                                    while (keyPrice > minRange) {
                                        keyPrice.toPrice().let {
                                            orders[it] ?: run {
                                                orders[it] =
                                                    sentOrder(
                                                        price = keyPrice,
                                                        amount = settings.parameters.orderQuantity.value,
                                                        orderSide = SIDE.BUY,
                                                        orderType = settings.ordersType
                                                    )
                                            }
                                        }
                                        keyPrice -= settings.parameters.orderDistance.value
                                    }


                                    val prev =
                                        (prevPrice - (prevPrice % settings.parameters.orderDistance.value)).toPrice()

                                    if (price.toBigDecimal() != prev.toBigDecimal()) {
                                        orders[prev]?.let { order ->
                                            getOrder(tradePair, order.orderId)?.let { o ->
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
                                    TODO("Not Implemented DIRECTION.SHORT")
                                }
                            }
                        }

                        else -> throw UnsupportedOrderTypeException("Unsupported order type: ${settings.ordersType}")
                    }
                } else
                    log("Price ${currentPrice.toPrice()}, not in range: ${settings.parameters.tradingRange}")

                when (settings.direction) {
                    DIRECTION.LONG -> {
                        orders.filter { it.value.type == TYPE.MARKET }.forEach {
                            if (it.value.lastBorderPrice!! < currentPrice) {
                                it.value.lastBorderPrice = currentPrice

                                if (
                                    it.value.stopPrice?.run { this < currentPrice - settings.parameters.triggerDistance.value } == true
                                    || it.value.stopPrice == null && it.key.toBigDecimal() < (currentPrice - settings.parameters.triggerDistance.value)
                                ) {
                                    it.value.stopPrice = currentPrice - settings.parameters.triggerDistance.value
                                }
                            }
                            if (it.value.stopPrice?.run { this >= currentPrice } == true) {
                                log("Order close: ${it.value}")
                                sentOrder(BigDecimal(0.0), it.value.origQty, SIDE.SELL, TYPE.MARKET)
                                ordersListForRemove.add(it.key)
                            }
                        }
                    }

                    DIRECTION.SHORT -> {
                        orders.filter { it.value.type == TYPE.MARKET }.forEach {
                            if (it.value.lastBorderPrice!! > currentPrice) {
                                it.value.lastBorderPrice = currentPrice

                                if (
                                    it.value.stopPrice?.run { this > currentPrice - settings.parameters.triggerDistance.value } == true
                                    || it.value.stopPrice == null && it.key.toBigDecimal() < (currentPrice - settings.parameters.triggerDistance.value)
                                ) {
                                    it.value.stopPrice = currentPrice - settings.parameters.triggerDistance.value
                                }
                            }
                            if (it.value.stopPrice?.run { this <= currentPrice } == true) {
                                log("Order close: ${it.value}")
                                sentOrder(BigDecimal(0.0), it.value.origQty, SIDE.BUY, TYPE.MARKET)
                                orders.remove(it.key)
                            }
                        }
                    }
                }
                ordersListForRemove.forEach { orders.remove(it) }
                ordersListForRemove.clear()
            }

            else -> log?.warn("Unsupported message: $msg")
        }
    }
}