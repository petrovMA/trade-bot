package bot.trade.exchanges.clients

import bot.trade.exchanges.BotEvent
import bot.trade.exchanges.clients.stream.StreamThreadStub
import bot.trade.exchanges.emulate.TestBalance
import bot.trade.exchanges.libs.KlineConverter
import bot.trade.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.BlockingQueue

class TestClientFileData(
    val params: BotEmulateParams,
    private val fileData: File = File("database/${params.botParams.pair}_klines.csv"),
    private val fee: BigDecimal = BigDecimal(0.1),
    val from: ZonedDateTime = params.from?.let { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()) }!!,
    val to: ZonedDateTime = params.to?.let { LocalDateTime.parse(it).atZone(ZoneId.systemDefault()) }!!
) : ClientFutures {
    var handler: (CommonExchangeData?) -> Unit = {}
    private val log = KotlinLogging.logger {}
    private var orders: MutableMap<String, Order> = HashMap()
    var lastSellPrice: BigDecimal = BigDecimal(0)
        private set
    private var lastBuyPrice: BigDecimal = BigDecimal(0)
    private val profit = TestBalance(tradePair = params.botParams.pair)

    private var positionLong: Position = Position(
        pair = TradePair("TEST_PAIR"),
        marketPrice = 0.0.toBigDecimal(),
        unrealisedPnl = 0.0.toBigDecimal(),
        realisedPnl = 0.0.toBigDecimal(),
        entryPrice = 0.0.toBigDecimal(),
        breakEvenPrice = 0.0.toBigDecimal(),
        leverage = 0.0.toBigDecimal(),
        liqPrice = 0.0.toBigDecimal(),
        size = 0.0.toBigDecimal(),
        side = "BUY"
    )

    private var positionShort: Position = Position(
        pair = TradePair("TEST_PAIR"),
        marketPrice = 0.0.toBigDecimal(),
        unrealisedPnl = 0.0.toBigDecimal(),
        realisedPnl = 0.0.toBigDecimal(),
        entryPrice = 0.0.toBigDecimal(),
        breakEvenPrice = 0.0.toBigDecimal(),
        leverage = 0.0.toBigDecimal(),
        liqPrice = 0.0.toBigDecimal(),
        size = 0.0.toBigDecimal(),
        side = "SELL"
    )

    private var clientOrderId = 0

    private lateinit var candlestick: Candlestick

    fun emulate(): TestBalance {

        fileData.forEachLine { line ->
            if (line.isNotBlank()) {
                candlestick = Candlestick(line.split(';'), 1.m())
                if (from == null || from.isBefore(candlestick.openTime.toZonedTime())) {
                    if (to == null || to.isAfter(candlestick.openTime.toZonedTime()))
                        handler(candlestick)
                }
            }
        }

        handler(BotEvent(type = BotEvent.Type.INTERRUPT))

        return profit
    }

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<TradePair, List<Order>> = TODO("not implemented")

    override fun getOpenOrders(pair: TradePair): List<Order> = orders.map { it.value }

    override fun getBalances(): Map<String, List<Balance>> = TODO("not implemented")

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = OrderBook(
        asks = listOf(Offer(price = lastBuyPrice, qty = BigDecimal(0))),
        bids = listOf(Offer(price = lastSellPrice, qty = BigDecimal(0)))
    )

    override fun getAssetBalance(asset: String): Map<String, Balance?> =
        mapOf("Total Balance" to getBalances()["Total Balance"]?.find { it.asset == asset })

    override fun getOrder(pair: TradePair, orderId: String): Order? = getOpenOrders(pair).find { it.orderId == orderId }

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> =
        TODO("Not yet implemented")

    override fun getCandlestickBars(
        pair: TradePair,
        interval: INTERVAL,
        countCandles: Int,
        start: Long?,
        end: Long?
    ): List<Candlestick> {

        val converter = KlineConverter(
            inputKlineInterval = 1.m(),
            outputKlineInterval = interval.toDuration(),
            size = countCandles
        )

        val from = start?.toZonedTime()
        val to = end?.toZonedTime()

        fileData.forEachLine { line ->
            if (line.isNotBlank()) {
                candlestick = Candlestick(line.split(';'), 1.m())
                if (from == null || from.isBefore(candlestick.openTime.toZonedTime())) {
                    if (to == null || to.isAfter(candlestick.openTime.toZonedTime()))
                        converter.addCandlesticks(candlestick)
                }
            }
        }

        return converter.getBars().map { Candlestick(it) }
    }

    override fun newOrder(
        order: Order,
        isStaticUpdate: Boolean,
        qty: String,
        price: String,
        positionSide: DIRECTION?,
        isReduceOnly: Boolean
    ): Order {
        ++clientOrderId
        when (order.type) {
            TYPE.MARKET -> {
                updatePosition(positionSide!!, order.side, order.origQty)

                handler(positionLong)
                handler(positionShort)

                return order
            }

            TYPE.LIMIT -> TODO("not implemented")

            else -> return order
        }
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean): Boolean {
        orders[orderId]?.let { order ->
            if (order.status != STATUS.NEW) return true
            order.status = STATUS.CANCELED

            if (order.side == SIDE.SELL)
                profit.firstBalance += order.origQty
            else if (order.side == SIDE.BUY)
                profit.secondBalance += order.origQty * (order.price ?: 0.0.toBigDecimal())
        } ?: log.info("Order: id = $orderId Not found!")
        return true
    }

    override fun stream(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        StreamThreadStub()

    override fun getAllPairs(): List<TradePair> = TODO("not implemented")

    override fun close() = Unit

    override fun getPositions(pair: TradePair): List<Position> = listOf(positionLong, positionShort)

    override fun switchMode(category: String, mode: Int, pair: TradePair?, coin: String?) {}


    private fun updatePosition(direction: DIRECTION, side: SIDE, amount: BigDecimal) {
        val price = if (side == SIDE.BUY) candlestick.high else candlestick.low

        val priceWithFee = if (side == SIDE.BUY) price + price.percent(fee) else price - price.percent(fee)

        when (direction) {
            DIRECTION.LONG -> {
                if (compareBigDecimal(positionLong.size, BigDecimal(0)) && side == SIDE.BUY) {
                    positionLong = Position(
                        pair = TradePair("TEST_PAIR"),
                        marketPrice = price,
                        unrealisedPnl = 0.0.toBigDecimal(),
                        realisedPnl = 0.0.toBigDecimal(),
                        entryPrice = price,
                        breakEvenPrice = price,
                        leverage = 0.0.toBigDecimal(),
                        liqPrice = 0.0.toBigDecimal(),
                        size = amount,
                        side = "BUY"
                    )

                    profit.secondBalance -= (amount * priceWithFee)
                    profit.firstBalance += amount

                } else {
                    positionLong = if (side == SIDE.BUY) {
                        val newAmount = positionLong.size + amount
                        val newAveragePrice =
                            ((positionLong.breakEvenPrice * positionLong.size) + (price * amount)) / newAmount

                        profit.secondBalance -= (amount * priceWithFee)
                        profit.firstBalance += amount

                        Position(
                            pair = TradePair("TEST_PAIR"),
                            marketPrice = candlestick.high,
                            unrealisedPnl = 0.0.toBigDecimal(),
                            realisedPnl = 0.0.toBigDecimal(),
                            entryPrice = positionLong.entryPrice,
                            breakEvenPrice = newAveragePrice,
                            leverage = 0.0.toBigDecimal(),
                            liqPrice = 0.0.toBigDecimal(),
                            size = newAmount,
                            side = "BUY"
                        )
                    } else {
                        val newAmount = positionLong.size - amount

                        val priceChange = (price - positionLong.breakEvenPrice) / (positionLong.size / amount)

                        profit.secondBalance += (amount * priceWithFee)
                        profit.firstBalance -= amount

                        if (newAmount > BigDecimal(0.0)) {
                            Position(
                                pair = TradePair("TEST_PAIR"),
                                marketPrice = 0.0.toBigDecimal(),
                                unrealisedPnl = 0.0.toBigDecimal(),
                                realisedPnl = 0.0.toBigDecimal(),
                                entryPrice = positionLong.entryPrice,
                                breakEvenPrice = positionLong.breakEvenPrice + priceChange,
                                leverage = 0.0.toBigDecimal(),
                                liqPrice = 0.0.toBigDecimal(),
                                size = newAmount,
                                side = "BUY"
                            )
                        } else {
                            Position(
                                pair = TradePair("TEST_PAIR"),
                                marketPrice = 0.0.toBigDecimal(),
                                unrealisedPnl = 0.0.toBigDecimal(),
                                realisedPnl = 0.0.toBigDecimal(),
                                entryPrice = 0.0.toBigDecimal(),
                                breakEvenPrice = 0.0.toBigDecimal(),
                                leverage = 0.0.toBigDecimal(),
                                liqPrice = 0.0.toBigDecimal(),
                                size = 0.0.toBigDecimal(),
                                side = "BUY"
                            )
                        }
                    }
                }
            }

            DIRECTION.SHORT -> {
                if (compareBigDecimal(positionShort.size, BigDecimal(0)) && side == SIDE.SELL) {
                    positionShort = Position(
                        pair = TradePair("TEST_PAIR"),
                        marketPrice = price,
                        unrealisedPnl = 0.0.toBigDecimal(),
                        realisedPnl = 0.0.toBigDecimal(),
                        entryPrice = price,
                        breakEvenPrice = price,
                        leverage = 0.0.toBigDecimal(),
                        liqPrice = 0.0.toBigDecimal(),
                        size = amount,
                        side = "SELL"
                    )

                    profit.secondBalance += (amount * priceWithFee)
                    profit.firstBalance -= amount
                } else {
                    positionShort = if (side == SIDE.SELL) {
                        val newAmount = positionShort.size + amount

                        val priceChange = (price - positionShort.breakEvenPrice) / newAmount

                        profit.secondBalance += (amount * priceWithFee)
                        profit.firstBalance -= amount

                        Position(
                            pair = TradePair("TEST_PAIR"),
                            marketPrice = price,
                            unrealisedPnl = 0.0.toBigDecimal(),
                            realisedPnl = 0.0.toBigDecimal(),
                            entryPrice = positionShort.entryPrice,
                            breakEvenPrice = positionShort.breakEvenPrice + priceChange,
                            leverage = 0.0.toBigDecimal(),
                            liqPrice = 0.0.toBigDecimal(),
                            size = newAmount,
                            side = "SELL"
                        )
                    } else {
                        val newAmount = positionShort.size - amount

                        profit.secondBalance -= (amount * priceWithFee)
                        profit.firstBalance += amount

                        if (newAmount > BigDecimal(0.0)) {
                            Position(
                                pair = TradePair("TEST_PAIR"),
                                marketPrice = 0.0.toBigDecimal(),
                                unrealisedPnl = 0.0.toBigDecimal(),
                                realisedPnl = 0.0.toBigDecimal(),
                                entryPrice = positionShort.entryPrice,
                                breakEvenPrice = ((positionShort.breakEvenPrice * positionShort.size) + (price * amount)) / newAmount,
                                leverage = 0.0.toBigDecimal(),
                                liqPrice = 0.0.toBigDecimal(),
                                size = newAmount,
                                side = "SELL"
                            )
                        } else {
                            Position(
                                pair = TradePair("TEST_PAIR"),
                                marketPrice = 0.0.toBigDecimal(),
                                unrealisedPnl = 0.0.toBigDecimal(),
                                realisedPnl = 0.0.toBigDecimal(),
                                entryPrice = 0.0.toBigDecimal(),
                                breakEvenPrice = 0.0.toBigDecimal(),
                                leverage = 0.0.toBigDecimal(),
                                liqPrice = 0.0.toBigDecimal(),
                                size = 0.0.toBigDecimal(),
                                side = "SELL"
                            )
                        }
                    }
                }
            }
        }
    }
}