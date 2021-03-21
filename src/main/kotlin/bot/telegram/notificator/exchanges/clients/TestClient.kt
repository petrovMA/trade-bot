package bot.telegram.notificator.exchanges.clients

import bot.telegram.notificator.exchanges.emulate.SocketThreadStub
import bot.telegram.notificator.exchanges.emulate.TestBalance
import bot.telegram.notificator.exchanges.emulate.libs.NoEmptyOrdersException
import bot.telegram.notificator.exchanges.emulate.libs.NotSupportedCandlestickIntervalException
import bot.telegram.notificator.exchanges.emulate.libs.UnsupportedStateException
import bot.telegram.notificator.exchanges.BotEvent
import bot.telegram.notificator.exchanges.CandlestickListsIterator
import bot.telegram.notificator.libs.convertTime
import bot.telegram.notificator.libs.percent
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

class TestClient(
    val iterator: CandlestickListsIterator,
    val balance: TestBalance,
    startCandleNum: Int,
    private val fee: Double = 0.15
) : Client {
    private val log = KotlinLogging.logger {}
    private var firstOrder: Order? = null
    private var secondOrder: Order? = null
    var lastSellPrice: Double = 0.0
        private set
    private var lastBuyPrice: Double = 0.0
    val queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque()

    private var candlesticks: List<Candlestick> = iterator.next().also {
//        addAll(readObjectFromFile(files.component1(), ArrayList::class.java).map { toCandlestick(it) })
//        addAll(readObjectFromFile(files.component2(), ArrayList::class.java).map { toCandlestick(it) })
//        addAll(readObjectFromFile(files.component3(), ArrayList::class.java).map { toCandlestick(it) })

        balance.apply { if (firstBalance == 0.0) firstBalance = secondBalance / it.first().close }
    }

    val interval = when (candlesticks.first().run { closeTime + 1 - openTime }) {
        60_000L -> INTERVAL.ONE_MINUTE
        180_000L -> INTERVAL.THREE_MINUTES
        300_000L -> INTERVAL.FIVE_MINUTES
        900_000L -> INTERVAL.FIFTEEN_MINUTES
        1_800_000L -> INTERVAL.HALF_HOURLY
        3_600_000L -> INTERVAL.HOURLY
        else -> throw NotSupportedCandlestickIntervalException()
    }

    val startFirstBalance = balance.firstBalance
    val startSecondBalance = balance.secondBalance

    private var candlestickNum: Int = startCandleNum
    private var prevCandlestick: Candlestick = candlesticks[candlestickNum - 1]
    private var state = EventState.FIRST_DEPTH
    private var clientOrderId = 0

    var updateStaticOrdersCount: Int = 0
        private set
    var executedOrdersCount: Int = 0
        private set

    val firstPrice: Double = candlesticks.first().close

    override fun getAllOpenOrders(pairs: List<TradePair>): Map<String, List<Order>> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOpenOrders(pair: TradePair): List<Order> = ArrayList<Order>().apply {
        firstOrder?.let { add(it) }
        secondOrder?.let { add(it) }
    }

    override fun getBalances(): List<Balance> = listOf(
        Balance(
            asset = balance.tradePair.first,
            free = balance.firstBalance,
            total = balance.firstBalance,
            locked = 0.0
        ),
        Balance(
            asset = balance.tradePair.second,
            free = balance.secondBalance,
            total = balance.secondBalance,
            locked = 0.0
        )
    )

    override fun getOrderBook(pair: TradePair, limit: Int): OrderBook = OrderBook(
        asks = listOf(OrderEntry(price = lastBuyPrice, qty = 0.0)),
        bids = listOf(OrderEntry(price = lastSellPrice, qty = 0.0))
    )

    override fun getAssetBalance(asset: String): Balance = getBalances().find { it.asset == asset }
        ?: Balance(asset = asset, free = 0.0, total = 0.0, locked = 0.0)

    override fun getOrder(pair: TradePair, orderId: String): Order =
        getOpenOrders(pair).find { it.orderId == orderId }
            ?: Order(
                pair = balance.tradePair,
                side = SIDE.SELL,
                type = TYPE.LIMIT,
                origQty = 0.0,
                executedQty = 0.0,
                price = lastBuyPrice,
                status = STATUS.NEW,
                orderId = orderId
            )

    override fun getCandlestickBars(pair: TradePair, interval: INTERVAL, countCandles: Int): List<Candlestick> {

        val from = (candlestickNum + 1 - countCandles).let {
                if (it < 0) {
                    log.warn("Can't get full candlesticks list! Need ${it * -1} more candlesticks from start.")
                    0
                } else it
            }
        val to = (candlestickNum + 1).let {
            if (candlesticks.size < it) {
                log.warn("Can't get full candlesticks list! Need ${it - candlesticks.size} more candlesticks from end.")
                candlesticks.size
            } else it
        }
        val result = candlesticks.subList(from, to)
        return result
    }

    override fun newOrder(
        pair: TradePair,
        side: SIDE,
        type: TYPE,
        amount: Double,
        price: Double,
        isStaticUpdate: Boolean,
        formatCount: String,
        formatPrice: String
    ): Order {
        if (isStaticUpdate) updateStaticOrdersCount++
        ++clientOrderId
        val order = Order(
            pair = pair,
            side = side,
            type = type,
            origQty = amount,
            executedQty = 0.0,
            price = price,
            status = STATUS.NEW,
            orderId = clientOrderId.toString()
        )

        if (side == SIDE.SELL)
            balance.firstBalance -= amount
        else if (side == SIDE.BUY)
            balance.secondBalance -= amount * price

        if (firstOrder == null || firstOrder?.status in listOf(STATUS.FILLED, STATUS.CANCELED, STATUS.REJECTED)) {
            firstOrder = order
        } else if (secondOrder == null || secondOrder?.status in listOf(
                STATUS.FILLED,
                STATUS.CANCELED,
                STATUS.REJECTED
            )
        ) {
            secondOrder = order
        } else
            throw NoEmptyOrdersException("Empty orders not found!")

        return order
    }

    override fun cancelOrder(pair: TradePair, orderId: String, isStaticUpdate: Boolean) {
        when (orderId) {
            firstOrder?.orderId -> {
                if (firstOrder!!.status != STATUS.NEW) return
                firstOrder!!.status = STATUS.CANCELED

                if (firstOrder!!.side == SIDE.SELL)
                    balance.firstBalance += firstOrder!!.origQty
                else if (firstOrder!!.side == SIDE.BUY)
                    balance.secondBalance += firstOrder!!.origQty * firstOrder!!.price
            }
            secondOrder?.orderId -> {
                if (secondOrder!!.status != STATUS.NEW) return
                secondOrder!!.status = STATUS.CANCELED

                if (secondOrder!!.side == SIDE.SELL)
                    balance.firstBalance += secondOrder!!.origQty
                else if (secondOrder!!.side == SIDE.BUY)
                    balance.secondBalance += secondOrder!!.origQty * secondOrder!!.price
            }
            else -> log.info("Order: id = $orderId Not found!")
        }
    }

    override fun nextEvent() {
        val candlestick = candlesticks[candlestickNum]

        when (state) {
            EventState.FIRST_DEPTH -> {

                val openPrice = candlestick.open
                val closePrice = candlestick.close

                if (openPrice > closePrice) {
                    lastBuyPrice = openPrice
                    lastSellPrice = closePrice
                } else {
                    lastBuyPrice = closePrice
                    lastSellPrice = openPrice
                }
                checkOrderExecuted(lastBuyPrice)
                checkOrderExecuted(lastSellPrice)

                queue.put(DepthEventOrders(OrderEntry(lastBuyPrice, 0.0), OrderEntry(lastSellPrice, 0.0)))
                state = EventState.from(state.number + 2)
            }
            EventState.SELL_TRADE -> {
                checkOrderExecuted(lastSellPrice)
                queue.put(OrderEntry(price = lastSellPrice, qty = 0.0))
                state = EventState.from(state.number + 1)
            }
            EventState.BUY_DEPTH -> {
                checkOrderExecuted(lastBuyPrice)
                checkOrderExecuted(lastSellPrice)
                queue.put(DepthEventOrders(OrderEntry(lastBuyPrice, 0.0), OrderEntry(lastSellPrice, 0.0)))
                state = EventState.from(state.number + 1)
            }
            EventState.BUY_TRADE -> {
                checkOrderExecuted(lastBuyPrice)
                queue.put(OrderEntry(price = lastBuyPrice, qty = 0.0))
                state = EventState.from(state.number + 1)
            }
            EventState.SELL_MAX_DEPTH -> {
                checkOrderExecuted(candlestick.low)
                queue.put(DepthEventOrders(OrderEntry(lastBuyPrice, 0.0), OrderEntry(candlestick.low, 0.0)))
                state = EventState.from(state.number + 1)
            }
            EventState.SELL_MAX_TRADE -> {
                checkOrderExecuted(candlestick.low)
                queue.put(OrderEntry(price = candlestick.low, qty = 0.0))
                state = EventState.from(state.number + 1)
            }
            EventState.BUY_MAX_DEPTH -> {
                checkOrderExecuted(candlestick.high)
                queue.put(DepthEventOrders(OrderEntry(candlestick.high, 0.0), OrderEntry(lastSellPrice, 0.0)))
                state = EventState.from(state.number + 1)
            }
            EventState.BUY_MAX_TRADE -> {
                checkOrderExecuted(candlestick.high)
                queue.put(OrderEntry(price = candlestick.high, qty = 0.0))
                state = EventState.from(state.number + 1)
            }
            EventState.CANDLESTICK -> {
                ++candlestickNum
                if (candlestickNum >= candlesticks.size)
                    if (iterator.hasNext()) {
                        candlesticks = iterator.next()
                        candlestickNum = 0
                    } else
                        return queue.put(BotEvent(type = BotEvent.Type.INTERRUPT))

                queue.put(candlestick)

                check(prevCandlestick, candlestick)
                prevCandlestick = candlestick

                state = EventState.from(1)
            }
            else -> {
                throw UnsupportedStateException()
            }
        }
    }

    override fun socket(pair: TradePair, interval: INTERVAL, queue: BlockingQueue<CommonExchangeData>) =
        SocketThreadStub()

    private fun check(prev: Candlestick, current: Candlestick) {
        if (prev.closeTime + 1 != current.openTime)
            log.warn(
                "Candlesticks has a gap between ${convertTime(prev.closeTime)} ${prev.closeTime} " +
                        "AND ${convertTime(current.openTime)} ${current.openTime}"
            )
    }

    enum class EventState(val number: Int) {
        FIRST_DEPTH(1),
        SELL_DEPTH(2),
        SELL_TRADE(3),
        BUY_DEPTH(4),
        BUY_TRADE(5),
        SELL_MAX_DEPTH(6),
        SELL_MAX_TRADE(7),
        BUY_MAX_DEPTH(8),
        BUY_MAX_TRADE(9),
        CANDLESTICK(10);

        companion object {
            fun from(findValue: Int) = values().first { it.number == findValue }
        }
    }

    private fun checkOrderExecuted(price: Double) {

        val checkOrder: (order: Order) -> Order = { order ->
            if (order.status == STATUS.NEW) {
                when {
                    order.price > price -> {
                        if (order.side == SIDE.BUY) {
                            order.status = STATUS.FILLED
                            order.executedQty = order.origQty
                            executedOrdersCount++

                            val profit = (order.origQty - order.origQty.percent(fee))

                            balance.firstBalance += profit
                        }

                        order
                    }
                    order.price < price -> {
                        if (order.side == SIDE.SELL) {
                            order.status = STATUS.FILLED
                            order.executedQty = order.origQty
                            executedOrdersCount++

                            var profit = order.origQty * order.price
                            profit = (profit - profit.percent(fee))

                            balance.secondBalance += profit
                        }

                        order
                    }
                    else -> order
                }
            } else order
        }

        firstOrder = firstOrder?.let { checkOrder(it) }
        secondOrder = secondOrder?.let { checkOrder(it) }
    }

    override fun getAllPairs(): List<TradePair> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() = Unit
}