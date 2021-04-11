package bot.telegram.notificator.exchanges

import bot.telegram.notificator.*
import bot.telegram.notificator.libs.*
import bot.telegram.notificator.exchanges.BotEvent.Type.*
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.clients.socket.SocketThread
import com.typesafe.config.Config
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.absoluteValue

class TraderAlgorithm(
    conf: Config,
    val queue: LinkedBlockingDeque<CommonExchangeData> = LinkedBlockingDeque(),
    private val exchangeEnum: ExchangeEnum = conf.getEnum(ExchangeEnum::class.java, "exchange"),
    private val api: String = conf.getString("api"),
    private val sec: String = conf.getString("sec"),
    private var client: Client = newClient(exchangeEnum, api, sec),
    private val firstSymbol: String = conf.getString("symbol.first")!!,
    private val secondSymbol: String = conf.getString("symbol.second")!!,
    private val tradePair: TradePair = TradePair(firstSymbol, secondSymbol),
    private val path: String = "exchange/${tradePair}",
    balanceTrade: BigDecimal = conf.getDouble("balance_trade").toBigDecimal(),
    private val minTradeBalance: BigDecimal = conf.getDouble("min_balance_trade").toBigDecimal(),
    private val minOverheadBalance: BigDecimal = conf.getDouble("min_overhead_balance").toBigDecimal(),
    private val syncTimeInterval: Duration = conf.getDuration("sync_interval_time"),
    private val cancelUnknownOrdersInterval: ActionInterval = ActionInterval(conf.getDuration("cancel_unknown_orders_interval")),
    private val retrySentOrderCount: Int = conf.getInt("retry_sent_order_count"),
    isLog: Boolean = true,
    private val isEmulate: Boolean = false,
    val sendMessage: (String) -> Unit
) : Thread() {
    private val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()
    private var averageHigh = 0.toBigDecimal()
    private var averageLow = 0.toBigDecimal()
    private val percent = conf.getDouble("percent.static_orders").toBigDecimal()
    private val deltaPercent = conf.getDouble("percent.delta").toBigDecimal()
    private val percentBuyProf = conf.getDouble("percent.buy_prof").toBigDecimal()
    private val percentSellProf = conf.getDouble("percent.sell_prof").toBigDecimal()
    private val percentCountForPartiallyFilledUpd =
        conf.getDouble("percent.count_for_partially_filled_upd").toBigDecimal()
    private val intervalCandlesBuy = conf.getInt("interval.candles_buy")
    private val intervalCandlesSell = conf.getInt("interval.candles_sell")

    private val log = if (isLog) KotlinLogging.logger {} else null

    private val countCandles =
        if (intervalCandlesBuy > intervalCandlesSell)
            intervalCandlesBuy
        else
            intervalCandlesSell

    private val waitTime = conf.getDuration("interval.wait_socket_time")
    private val mainBalance = 0.0.toBigDecimal()
    private val firstBalanceFixed: Boolean = conf.getBoolean("first_balance_fixed")
    private val formatCount = conf.getString("format.count")
    private val formatPrice = conf.getString("format.price")
    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")
    private val reconnectWaitTime = conf.getDuration("wait_time.reconnect")
    private val exceptionWaitTime = conf.getDuration("wait_time.exception")
    private val updateCandlestickOrdersInterval =
        ActionInterval(conf.getDuration("update_candlestick_orders_time_interval"))
    private val waitBetweenCancelAndUpdateOrders = conf.getDuration("wait_between_cancel_and_update_orders")

    //    private val writeEthBalanceToHistoryInterval = conf.getDuration("save.eth_balance_to_history_interval")
//    private val isWriteEthBalance = conf.getBoolean("save.eth_balance_to_history")
    private val pauseBetweenCheckOrder = conf.getDuration("pause_between_check_orders")
    private val printOrdersAndOff = conf.getBoolean("print_orders_and_off")
    private val autoCreateAnyOrders = conf.getBoolean("auto_create_any_orders")
    private val isCancelUnknownOrders = conf.getBoolean("cancel_unknown_orders")
    private val updateIfAlmostFilled = conf.getBoolean("update_if_almost_filled")

    private var stopThread = false
    private var nearSellPrice: BigDecimal = 0.toBigDecimal()
    private var nearBuyPrice: BigDecimal = 0.toBigDecimal()
    private var lastTradePrice: BigDecimal = 0.toBigDecimal()
    private var checkBuyOrderTrigger = false to if (isEmulate) 0.s() else 1.s()
    private var checkSellOrderTrigger = false to if (isEmulate) 0.s() else  1.s()
    private var lastCheckBuyOrderTime: Duration = 5.s()
    private var lastCheckSellOrderTime: Duration = 5.s()
    private var lastSyncTime: Duration = 0.s()

    //    private var lastLastWriteEthBalanceToHistory: Duration = 0.s()
    private val maxTryingUpdateOrderSell: Int = 3
    private var tryingUpdateOrderSell: Int = 0
    private val maxTryingUpdateOrderBuy: Int = 3
    private var tryingUpdateOrderBuy: Int = 0
    private var candlestickList = ListLimit<Candlestick>(limit = countCandles)
    private val klineConstructor = KlineConstructor(interval)

    val balance = BalanceInfo(
        symbols = tradePair,
        firstBalance = 0.0.toBigDecimal(),
        secondBalance = mainBalance,
        balanceTrade = balanceTrade
    )

    private var socket: SocketThread = client.socket(TradePair(firstSymbol, secondSymbol), interval, queue)

    fun interruptThis(msg: String? = null) {
        socket.interrupt()
        var msgErr = "#Interrupt_$tradePair Thread, socket.status = ${socket.state}"
        var logErr = "Thread for $tradePair Interrupt, socket.status = ${socket.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        sendMessage(msgErr)
        log?.warn(logErr)
        stopThread = true
    }

    override fun run() {
        stopThread = false
        try {
            if (!File(path).isDirectory)
                Files.createDirectories(Paths.get(path))

            val orders = client.getOpenOrders(tradePair)

            log?.info("All $tradePair open orders: " + orders.joinToString(prefix = "\n", separator = "\n"))
            println("All $tradePair open orders: " + orders.joinToString(prefix = "\n", separator = "\n"))

            if (printOrdersAndOff) return

            synchronizeOrders()


            socket.run()

            calcAveragePrice()

            client.nextEvent() /* only for test */
            var msg = queue.poll(waitTime)
            do {
                if (stopThread) return
                try {
                    when (msg) {
                        is DepthEventOrders -> {
                            nearBuyPrice = msg.bid.price
                            nearSellPrice = msg.ask.price
                            log?.debug("$tradePair First DepthEvent:\n$msg")
                        }
                        is Trade -> {
                            lastTradePrice = msg.price
                            log?.debug("$tradePair TradeEvent:\n$msg")

                            klineConstructor.nextKline(msg).forEach { kline ->
                                if (kline.first) {

                                    log?.info("$tradePair First CandlestickEvent:\n${kline.second}")
                                    val closePrice = kline.second.close
                                    val openPrice = kline.second.open
                                    log?.info("$tradePair ClosePrice = $closePrice, OpenPrice = $openPrice")

                                    if (closePrice > openPrice) {
                                        nearBuyPrice = closePrice
                                        nearSellPrice = openPrice
                                    } else {
                                        nearBuyPrice = openPrice
                                        nearSellPrice = closePrice
                                    }

                                    candlestickList.add(kline.second)
                                    calcAveragePrice()

                                }
                            }
                        }
                    }

                    client.nextEvent() /* only for test */

                    msg = queue.poll(waitTime)
                } catch (e: InterruptedException) {
                    log?.error("$tradePair ${e.message}", e)
                    sendMessage("#Error_$tradePair: \n${printTrace(e)}")

                    socket = client.socket(TradePair(firstSymbol, secondSymbol), interval, queue)

                    socket.start()
                }
            } while (msg !is DepthEventOrders)

            createStaticOrdersOnStart()

            balance.orderB ?: run {
                log?.error("$tradePair orderB is NULL. Not enough balance for start.")
                sendMessage("#orderB_${tradePair}_is_NULL. Not enough balance for start.")
                interruptThis()
            }

            balance.orderS ?: run {
                log?.error("$tradePair orderS is NULL. Not enough balance for start.")
                sendMessage("#orderS_${tradePair}_is_NULL. Not enough balance for start.")
                interruptThis()
            }

            if (stopThread) return

            if (!isEmulate) writeLine(balance, File("$path/balance.json"))

            client.nextEvent() /* only for test */
            msg = queue.poll(waitTime)
            while (true) {
                if (stopThread) return
                try {
                    if (msg != null) {
                        try {
                            when (msg) {
                                is DepthEventOrders -> {
                                    nearBuyPrice = msg.bid.price
                                    nearSellPrice = msg.ask.price
                                    log?.trace("$tradePair DepthEvent:\n$msg")

                                    if (balance.orderB?.side == SIDE.SELL && balance.orderS?.side == SIDE.BUY) {
                                        updateStaticOrders()
                                    }
                                }
                                is Trade -> {
                                    lastTradePrice = msg.price
                                    log?.debug("$tradePair TradeEvent:\n$msg")

                                    klineConstructor.nextKline(msg).forEach { kline ->
                                        if (kline.first) {
                                            candlestickList.add(kline.second)
                                            if (isEmulate) {
                                                calcAveragePrice()
                                                updateOrders()
                                            }
                                        }
                                    }
                                    updateCandlestickOrdersInterval.tryInvoke {
                                        log?.debug("$tradePair Update orders by Trade!")
                                        calcAveragePrice()
                                        updateOrders()
                                    }

                                    if (checkBuyOrderTrigger.first) {
                                        if ((checkBuyOrderTrigger.second + pauseBetweenCheckOrder - time()).isNegative) {
                                            log?.info("$tradePair Run trigger buy order check")
                                            checkBuyOrder()
                                            checkBuyOrderTrigger = false to 1.s()
                                        } else
                                            log?.info("$tradePair Trigger Buy wait: ${checkBuyOrderTrigger.second.format()}")
                                    }

                                    if (checkSellOrderTrigger.first) {
                                        if ((checkSellOrderTrigger.second + pauseBetweenCheckOrder - time()).isNegative) {
                                            log?.info("$tradePair Run trigger sell order check")
                                            checkSellOrder()
                                            checkSellOrderTrigger = false to 1.s()
                                        } else
                                            log?.info("$tradePair Trigger Sell wait: ${checkSellOrderTrigger.second.format()}")
                                    }

                                    if (balance.orderB!!.side == SIDE.BUY) {
                                        if (lastTradePrice <= balance.orderB!!.price) {
                                            if (!checkBuyOrderTrigger.first)
                                                checkBuyOrder()
                                            else
                                                log?.info("$tradePair Wait Buy trigger: ${checkBuyOrderTrigger.second.format()}")
                                        }
                                    } else if (balance.orderB!!.side == SIDE.SELL) {
                                        if (lastTradePrice >= balance.orderB!!.price) {
                                            if (!checkBuyOrderTrigger.first)
                                                checkBuyOrder()
                                            else
                                                log?.info("$tradePair Wait Buy trigger: ${checkBuyOrderTrigger.second.format()}")
                                        }
                                    } else {
                                        if (isUnknown(balance.orderB)) checkBuyOrder()
                                        else log?.warn("$tradePair Unsupported order side: ${balance.orderB!!.side}")
                                    }

                                    if (balance.orderS!!.side == SIDE.SELL) {
                                        if (lastTradePrice > balance.orderS!!.price) {
                                            if (!checkSellOrderTrigger.first)
                                                checkSellOrder()
                                            else
                                                log?.info("$tradePair Wait Sell trigger: ${checkSellOrderTrigger.second.format()}")
                                        }
                                    } else if (balance.orderS!!.side == SIDE.BUY) {
                                        if (lastTradePrice < balance.orderS!!.price) {
                                            if (!checkSellOrderTrigger.first)
                                                checkSellOrder()
                                            else
                                                log?.info("$tradePair Wait Sell trigger: ${checkSellOrderTrigger.second.format()}")
                                        }
                                    } else {
                                        if (isUnknown(balance.orderS)) checkSellOrder()
                                        else log?.warn("$tradePair Unsupported order side: ${balance.orderS!!.side}")
                                    }

                                }
                                is Order -> {
                                    log?.debug("$tradePair OrderUpdate:\n$msg")

                                    if (msg.pair.first == tradePair.first && msg.pair.second == tradePair.second) // todo DELETE IT WHEN socket will be one for all Pairs

                                        if (msg.status == STATUS.FILLED || msg.status == STATUS.PARTIALLY_FILLED)
                                            if (msg.orderId == balance.orderB?.orderId) {
                                                if (!checkBuyOrderTrigger.first)
                                                    checkBuyOrder(msg)
                                                else
                                                    log?.info("$tradePair Wait Buy trigger: ${checkBuyOrderTrigger.second.format()}")
                                            } else if (msg.orderId == balance.orderS?.orderId) {
                                                if (!checkSellOrderTrigger.first)
                                                    checkSellOrder(msg)
                                                else
                                                    log?.info("$tradePair Wait Sell trigger: ${checkSellOrderTrigger.second.format()}")
                                            } else
                                                log?.warn("$tradePair Unknown order: $msg\norderB: ${balance.orderB}\norderS: ${balance.orderS}")

                                }
                                is BotEvent -> {
                                    when (msg.type) {
                                        GET_PAIR_OPEN_ORDERS -> {
                                            val symbols = msg.message.split("[^a-zA-Z]+".toRegex())
                                                .filter { it.isNotBlank() }

                                            sendMessage(
                                                client.getOpenOrders(TradePair(symbols[0], symbols[1]))
                                                    .joinToString("\n\n")
                                            )
                                        }
                                        GET_ALL_OPEN_ORDERS -> {
                                            val pairs = msg.message
                                                .split("\\s+".toRegex())
                                                .filter { it.isNotBlank() }
                                                .map { pair ->
                                                    val symbols = pair.split("[^a-zA-Z]+".toRegex())
                                                        .filter { it.isNotBlank() }
                                                    TradePair(symbols[0], symbols[1])
                                                }

                                            client.getAllOpenOrders(pairs)
                                                .forEach { sendMessage("${it.key}\n${it.value.joinToString("\n\n")}") }
                                        }
//                                        SHOW_ALL_BALANCES -> {
//                                            sendMessage(
//                                                    "#AllBalances " +
//                                                            client.getBalances()
//                                                                    .joinToString(prefix = "\n", separator = "\n")
//                                            )
//                                        }
                                        SHOW_BALANCES -> {
                                            sendMessage(
                                                "#AllBalances " +
                                                        client.getBalances()
                                                            .joinToString(prefix = "\n", separator = "\n")
                                            )
                                        }
//                                        SHOW_FREE_BALANCES -> {
//                                            sendMessage(
//                                                    "#FreeBalances " +
//                                                            getFreeBalances(client,
//                                                                    msg.message
//                                                                            .split("\\s+".toRegex())
//                                                                            .let { it.subList(1, it.size) }
//                                                            )
//                                                                    .sortedBy { it.first }
//                                                                    .joinToString(prefix = "\n", separator = "\n") { "${it.first} ${it.second}" }
//                                            )
//                                        }
                                        SHOW_GAP -> {
                                            if (balance.orderB != null && balance.orderS != null)
                                                sendMessage(
                                                    "#Gap $tradePair\n${
                                                        calcGapPercent(
                                                            balance.orderB!!,
                                                            balance.orderS!!
                                                        )
                                                    }"
                                                )
                                            else
                                                sendMessage("#orderB_or_orderS_is_NULL_Cannot_calc_Gap.")
                                        }
                                        INTERRUPT -> {
                                            socket.interrupt()
                                            return
                                        }
                                        else -> sendMessage("Unsupported command: ${msg.type}")
                                    }
                                }
                                else -> log?.warn("Unsupported message: $msg")
                            }
                        } catch (e: KotlinNullPointerException) {
                            log?.error("$tradePair Error:", e)
                            sendMessage("#Error_$tradePair: \n${printTrace(e)}")
                            sleep(exceptionWaitTime.toMillis())
                            synchronizeOrders()
                            createStaticOrdersOnStart()
                        }
                    } else {
                        log?.error("$tradePair Connection lost. No messages during $waitTime!!!")
                        sendMessage("#lost_connection_$tradePair\nNo messages during $waitTime!!!")
                        sleep(reconnectWaitTime.toMillis())
                        socket = client.socket(TradePair(firstSymbol, secondSymbol), interval, queue)

                        socket.start()

                        synchronizeOrders()
                        createStaticOrdersOnStart()

                        log?.info("$tradePair Try to reconnect...")
                    }

                    client.nextEvent() /* only for test */

                    msg = queue.poll(waitTime)
                } catch (e: InterruptedException) {
                    log?.error("$tradePair ${e.message}", e)
                    sendMessage("#Error_$tradePair: \n${printTrace(e)}")
                    stopThread = true
                }
            }
        } catch (e: Exception) {
            log?.error("$tradePair MAIN ERROR:\n", e)
            sendMessage("#Error_$tradePair: \n${printTrace(e)}")
        } finally {
            interruptThis()
        }
    }


    private fun checkBuyOrder(order: Order? = null): BigDecimal? {
        var freeSecondBalance: BigDecimal? = null

        if (isUnknown(balance.orderB)) {
            val price =
                if (averageHigh - averageHigh.percent(percentBuyProf) < nearBuyPrice)
                    averageHigh - averageHigh.percent(percentBuyProf)
                else
                    nearBuyPrice - nearBuyPrice.percent(deltaPercent)

            freeSecondBalance = freeSecondBalance ?: client.getAssetBalance(secondSymbol).free

            if (freeSecondBalance > balance.balanceTrade + balance.balanceTrade.percent(minOverheadBalance)) {
                balance.orderB = sentOrder(
                    amount = balance.balanceTrade.div8(price),
                    price = price,
                    orderSide = SIDE.BUY,
                    isStaticUpdate = false
                )
                freeSecondBalance -= balance.balanceTrade
                if (!isEmulate) {
                    reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                    writeLine(balance, File("$path/balance.json"))
                }
                tryingUpdateOrderBuy = 0
            } else {
                log?.warn("can't create orderB")
                sendMessage(
                    "#Cannot_create_orderB_$tradePair:" +
                            "\nbalanceTrade.percent = ${balance.balanceTrade.percent(minOverheadBalance)}" +
                            "\nbalanceTrade = ${balance.balanceTrade}" +
                            "\nfreeSecondBalance = $freeSecondBalance"
                )

                if (tryingUpdateOrderBuy >= maxTryingUpdateOrderBuy) {
                    tryingUpdateOrderBuy = 0
                    interruptThis()
                } else tryingUpdateOrderBuy++

                return freeSecondBalance
            }

            log?.info("$tradePair BUY Order created:\n${balance.orderB}")
        } else {

            // todo replace with ActionInterval
            if ((lastCheckBuyOrderTime + pauseBetweenCheckOrder - time()).isNegative) {

                balance.orderB = order ?: getOrder(balance.symbols, balance.orderB!!.orderId)
                if (!isEmulate) reWriteObject(balance.orderB!!, File("$path/orderB.json"))

                lastCheckBuyOrderTime = time()

                log?.info("$tradePair BUY Order status: ${balance.orderB!!.status}")

                if (balance.orderB!!.status == STATUS.FILLED) {
                    sendMessage("#$tradePair OrderB #FILLED:\n${strOrder(balance.orderB)}")
                    updateBuyOrder()
                    lastCheckBuyOrderTime = 0.s()
                } else log?.info("$tradePair Order BUY:\n${balance.orderB}\nnot filled, but tradePrice in orderPrice")
            } else {
                if (!checkBuyOrderTrigger.first) {
                    checkBuyOrderTrigger = true to time() + pauseBetweenCheckOrder

                    log?.info(
                        "$tradePair Requests trying more tran 1 times in 10 seconds. " +
                                "Time = ${time().format()}; lastCheckBuyOrderTime = ${lastCheckBuyOrderTime.format()};\n" +
                                "TRIGGER FOR CHECK BUY: ${checkBuyOrderTrigger.second.format()}"
                    )
                }
            }
        }
        return freeSecondBalance
    }

    private fun checkSellOrder(order: Order? = null): BigDecimal? {
        var freeFirstBalance: BigDecimal? = null

        if (isUnknown(balance.orderS)) {
            val price =
                if (averageLow + averageLow.percent(percentSellProf) > nearSellPrice)
                    averageLow + averageLow.percent(percentSellProf)
                else
                    nearSellPrice + nearSellPrice.percent(deltaPercent)

            freeFirstBalance = client.getAssetBalance(firstSymbol).free

            if (freeFirstBalance > (balance.balanceTrade.div8(price)) + (balance.balanceTrade.div8(price)).percent(
                    minOverheadBalance
                )
            ) {
                balance.orderS = sentOrder(
                    amount = balance.balanceTrade.div8(price),
                    price = price,
                    orderSide = SIDE.SELL,
                    isStaticUpdate = false
                )
                freeFirstBalance -= balance.balanceTrade.div8(price)
                if (!isEmulate) {
                    reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                    writeLine(balance, File("$path/balance.json"))
                }
                tryingUpdateOrderSell = 0
            } else {
                log?.warn("can't create orderS")
                sendMessage(
                    "#Cannot_create_orderS_$tradePair:" +
                            "\n(balance.balanceTrade / price).percent = " +
                            (balance.balanceTrade.div8(price)).percent(minOverheadBalance) +
                            "\n(balance.balanceTrade / price) = ${(balance.balanceTrade.div8(price))}" +
                            "\nfreeFirstBalance = $freeFirstBalance"
                )

                if (tryingUpdateOrderSell >= maxTryingUpdateOrderSell) {
                    tryingUpdateOrderSell = 0
                    interruptThis()
                } else tryingUpdateOrderSell++

                return freeFirstBalance
            }

            log?.info("$tradePair SELL Order created:\n${balance.orderS}")
        } else {

            // todo replace with ActionInterval
            if ((lastCheckSellOrderTime + pauseBetweenCheckOrder - time()).isNegative) {

                balance.orderS = order ?: getOrder(balance.symbols, balance.orderS!!.orderId)
                if (!isEmulate) reWriteObject(balance.orderS!!, File("$path/orderS.json"))

                lastCheckSellOrderTime = time()

                val status = balance.orderS!!.status
                log?.info("$tradePair SELL Order status: $status")

                if (status == STATUS.FILLED) {
                    sendMessage("#$tradePair OrderS #FILLED:\n${strOrder(balance.orderS)}")
                    updateSellOrder()
                    lastCheckSellOrderTime = 0.s()
                } else log?.info("$tradePair Order SELL:\n${balance.orderS}\nnot filled, but tradePrice in orderPrice")
            } else {
                if (!checkSellOrderTrigger.first) {
                    checkSellOrderTrigger = true to time() + pauseBetweenCheckOrder

                    log?.info(
                        "$tradePair Requests trying more tran 1 times in 10 seconds. " +
                                "Time = ${time().format()}; lastCheckSellOrderTime = ${lastCheckSellOrderTime.format()};\n" +
                                "TRIGGER FOR CHECK SELL: ${checkSellOrderTrigger.second.format()}"
                    )
                }
            }
        }
        return freeFirstBalance
    }

    private fun updateOrders() {
        balance.orderB = getOrder(balance.symbols, balance.orderB!!.orderId)
        log?.debug("$tradePair Start update buy: ${balance.orderB}")

        var price =
            if (averageHigh - averageHigh.percent(percentBuyProf) < nearBuyPrice)
                averageHigh - averageHigh.percent(percentBuyProf)
            else
                nearBuyPrice - nearBuyPrice.percent(deltaPercent)

        when (balance.orderB!!.status) {
            STATUS.NEW -> {
                if (balance.orderB!!.side == SIDE.BUY) {

                    val countToBuy = balance.orderB!!.origQty

                    if (price != balance.orderB?.price) {
                        cancelOrder(balance.symbols, balance.orderB!!, false)
                        wait(waitBetweenCancelAndUpdateOrders)
                        if (BigDecimal(100) - (countToBuy.div8(balance.balanceTrade.div8(price))
                                .percent()) > percentCountForPartiallyFilledUpd
                        )
                            balance.orderB = sentOrder(
                                amount = countToBuy,
                                price = price,
                                orderSide = SIDE.BUY,
                                isStaticUpdate = false
                            )
                        else
                            balance.orderB = sentOrder(
                                amount = balance.balanceTrade.div8(price),
                                price = price,
                                orderSide = SIDE.BUY,
                                isStaticUpdate = false
                            )
                    } else
                        log?.debug("No need update! OrderB has same price!")

                    if (!isEmulate) {
                        reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                        writeLine(balance, File("$path/balance.json"))
                    }

                    log?.info("$tradePair BUY Order UPDATED:\n${balance.orderB}")
                }
            }
            STATUS.PARTIALLY_FILLED -> {
                if (balance.orderB!!.side == SIDE.BUY) {
                    val countToBuy = balance.orderB!!.origQty - balance.orderB!!.executedQty
                    log?.debug("$tradePair PARTIALLY_FILLED buy count = $countToBuy")

                    if (countToBuy * balance.orderB!!.price > minTradeBalance) {
                        if (price > balance.orderB!!.price) {

                            cancelOrder(balance.symbols, balance.orderB!!, false)
                            wait(waitBetweenCancelAndUpdateOrders)
                            balance.orderB = sentOrder(
                                amount = countToBuy,
                                price = price,
                                orderSide = SIDE.BUY,
                                isStaticUpdate = false
                            )

                            if (!isEmulate) {
                                reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                                writeLine(balance, File("$path/balance.json"))
                            }

                            log?.info("$tradePair BUY PARTIALLY_FILLED Order UPDATED:\n${balance.orderB}")
                        } else
                            log?.warn(
                                "$tradePair Can't update PARTIALLY_FILLED Order BUY;\n" +
                                        " Price: $price < ${balance.orderB!!.price}"
                            )
                    } else {
                        if (updateIfAlmostFilled) {
                            sendMessage("#$tradePair OrderB #ALMOST_FILLED:\n${strOrder(balance.orderB)}")
                            cancelOrder(balance.symbols, balance.orderB!!, false)
                            updateBuyOrder()
                        } else
                            log?.warn("$tradePair Can't update PARTIALLY_FILLED Order BUY:\n${balance.orderB}")
                    }
                }
            }
            STATUS.FILLED -> {
                sendMessage("#$tradePair OrderB #Update #FILLED:\n${strOrder(balance.orderB)}")
                log?.info("$tradePair Create new BUY order, instead update")
                updateBuyOrder()
            }
            else -> log?.warn("$tradePair Order BUY update cancelled, OrderStatus is ${balance.orderB!!.status}")

        }

        balance.orderS = getOrder(balance.symbols, balance.orderS!!.orderId)
        log?.debug("$tradePair Start update sell: ${balance.orderS}")

        price =
            if (averageLow + averageLow.percent(percentSellProf) > nearSellPrice)
                averageLow + averageLow.percent(percentSellProf)
            else
                nearSellPrice + nearSellPrice.percent(deltaPercent)

        when (balance.orderS!!.status) {
            STATUS.NEW -> {
                if (balance.orderS!!.side == SIDE.SELL) {

                    val countToSell = balance.orderS!!.origQty

                    if (price != balance.orderS?.price) {
                        cancelOrder(balance.symbols, balance.orderS!!, false)
                        wait(waitBetweenCancelAndUpdateOrders)
                        if (BigDecimal(100) - (countToSell.div8(balance.balanceTrade.div8(price))
                                .percent()) > percentCountForPartiallyFilledUpd
                        )
                            balance.orderS = sentOrder(
                                amount = countToSell,
                                price = price,
                                orderSide = SIDE.SELL,
                                isStaticUpdate = false
                            )
                        else
                            balance.orderS = sentOrder(
                                amount = balance.balanceTrade.div8(price),
                                price = price,
                                orderSide = SIDE.SELL,
                                isStaticUpdate = false
                            )
                    } else
                        log?.debug("No need update! OrderS has same price!")

                    if (!isEmulate) {
                        reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                        writeLine(balance, File("$path/balance.json"))
                    }

                    log?.info("$tradePair SELL Order UPDATED:\n${balance.orderS}")
                }
            }
            STATUS.PARTIALLY_FILLED -> {
                if (balance.orderS!!.side == SIDE.SELL) {
                    val countToSell = balance.orderS!!.origQty - balance.orderS!!.executedQty
                    log?.debug("$tradePair PARTIALLY_FILLED sell count = $countToSell")

                    if (countToSell * balance.orderS!!.price > minTradeBalance) {
                        if (price < balance.orderS!!.price) {

                            cancelOrder(balance.symbols, balance.orderS!!, false)
                            wait(waitBetweenCancelAndUpdateOrders)
                            balance.orderS = sentOrder(
                                amount = countToSell,
                                price = price,
                                orderSide = SIDE.SELL,
                                isStaticUpdate = false
                            )

                            if (!isEmulate) {
                                reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                                writeLine(balance, File("$path/balance.json"))
                            }

                            log?.info("$tradePair SELL PARTIALLY_FILLED Order UPDATED:\n${balance.orderS}")
                        } else
                            log?.warn(
                                "$tradePair Can't update PARTIALLY_FILLED Order SELL;\n" +
                                        " Price: $price > ${balance.orderS!!.price}"
                            )
                    } else {
                        if (updateIfAlmostFilled) {
                            sendMessage("#$tradePair OrderS #ALMOST_FILLED:\n${strOrder(balance.orderS)}")
                            cancelOrder(balance.symbols, balance.orderS!!, false)
                            updateSellOrder()
                        } else
                            log?.warn("$tradePair Can't update PARTIALLY_FILLED Order SELL:\n${balance.orderS}")
                    }
                }
            }
            STATUS.FILLED -> {
                sendMessage("#$tradePair OrderS #Update #FILLED:\n${strOrder(balance.orderS)}")
                log?.info("$tradePair Create new SELL order, instead update")
                updateSellOrder()
            }
            else -> log?.warn("$tradePair Order SELL update cancelled, OrderStatus is ${balance.orderS!!.status}")
        }

    }

    private fun updateBuyOrder() {
        if (balance.orderB!!.side == SIDE.BUY) {
            log?.info("$tradePair BUY Order FILLED 1:\n${balance.orderB}")
            val price = (balance.orderB!!.price
                    + balance.orderB!!.price.percent(percentBuyProf))

            val prevPrice = balance.orderB!!.price

            balance.orderB = sentOrder(
                amount = if (firstBalanceFixed) balance.balanceTrade.div8(prevPrice)
                else balance.balanceTrade.div8(price),
                price = price,
                orderSide = SIDE.SELL,
                isStaticUpdate = false
            )

            if (!isEmulate) {
                reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                writeLine(balance, File("$path/balance.json"))
            }

            log?.info("$tradePair new BUY Order 2:\n${balance.orderB}")
        } else {
            val price =
                if (averageHigh - averageHigh.percent(percentBuyProf) < nearBuyPrice)
                    averageHigh - averageHigh.percent(percentBuyProf)
                else
                    nearBuyPrice - nearBuyPrice.percent(deltaPercent)

            log?.info("$tradePair BUY Order FILLED 2:\n${balance.orderB}")

            balance.orderB = sentOrder(
                amount = balance.balanceTrade.div8(price),
                price = price,
                orderSide = SIDE.BUY,
                isStaticUpdate = false
            )

            if (!isEmulate) {
                reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                writeLine(balance, File("$path/balance.json"))
            }
            log?.info("$tradePair new BUY Order 1:\n${balance.orderB}")
        }
    }

    private fun updateSellOrder() {
        if (balance.orderS!!.side == SIDE.SELL) {
            log?.info("$tradePair SELL Order FILLED 1:\n${balance.orderS}")
            val price = (balance.orderS!!.price -
                    balance.orderS!!.price.percent(percentSellProf))

            val prevPrice = balance.orderS!!.price

            balance.orderS = sentOrder(
                amount = if (firstBalanceFixed) balance.balanceTrade.div8(prevPrice)
                else balance.balanceTrade.div8(price),
                price = price,
                orderSide = SIDE.BUY,
                isStaticUpdate = false
            )

            if (!isEmulate) {
                reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                writeLine(balance, File("$path/balance.json"))
            }
            log?.info("$tradePair new SELL Order 2:\n${balance.orderS}")
        } else {
            val price =
                if (averageLow + averageLow.percent(percentSellProf) > nearSellPrice)
                    averageLow + averageLow.percent(percentSellProf)
                else
                    nearSellPrice + nearSellPrice.percent(deltaPercent)

            log?.info("$tradePair SELL Order FILLED 2:\n${balance.orderS}")

            balance.orderS = sentOrder(
                amount = balance.balanceTrade.div8(price),
                price = price,
                orderSide = SIDE.SELL,
                isStaticUpdate = false
            )

            if (!isEmulate) {
                reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                writeLine(balance, File("$path/balance.json"))
            }

            log?.info("$tradePair new SELL Order 1:\n${balance.orderS}")
        }
    }

    private fun calcAveragePrice() {
        val (averageHigh, averageLow, candlestickList) = calcAveragePriceStatic(
            currentCandlestickList = candlestickList,
            countCandles = countCandles,
            intervalCandlesBuy = intervalCandlesBuy,
            intervalCandlesSell = intervalCandlesSell,
            interval = interval,
            symbols = tradePair,
            client = client,
            log = log,
            isEmulate = isEmulate
        )
        this.averageHigh = averageHigh
        this.averageLow = averageLow
        this.candlestickList = candlestickList
    }

    private fun sentOrder(price: BigDecimal, amount: BigDecimal, orderSide: SIDE, isStaticUpdate: Boolean): Order {

        log?.info("$tradePair Sent order with params: price = $price; count = $amount; side = $orderSide")

        var retryCount = retrySentOrderCount

        var order = Order("", tradePair, price, amount, BigDecimal(0), orderSide, TYPE.LIMIT, STATUS.NEW)

        do {
            try {
                order = client.newOrder(order, isStaticUpdate, formatCount, formatPrice)
                log?.debug("$tradePair Order sent: $order")
                return order
            } catch (e: Exception) {

                if (e.message == "Account has insufficient balance for requested action.") {
                    throw Exception(
                        "$tradePair Account has insufficient balance for requested action.\n" +
                                "#insufficient_${tradePair}_balance_for: $order\n" +
                                "$firstSymbol = ${client.getAssetBalance(firstSymbol).free}\n" +
                                "$secondSymbol = ${client.getAssetBalance(secondSymbol).free}"
                    )
                }

                retryCount--
                sendMessage("#Cannot_send_order_$tradePair: $order\nError:\n${printTrace(e, 50)}")
                log?.error("$tradePair Can't send: $order", e)

                e.printStackTrace()
                log?.debug("$tradePair Balances:\nBuy = ${balance.orderB}\nSell = ${balance.orderS}")
                client = newClient(exchangeEnum, api, sec)
                cancelOrInit()
            }
            try {
                sleep(5000)
            } catch (e: InterruptedException) {
                log?.error("$tradePair ${e.stackTrace}", e)
                sendMessage("#Error_$tradePair: \n${printTrace(e)}")
            }

        } while (isUnknown(order) && retryCount > 0)
        interruptThis("Error: Can't send order.")
        throw Exception("$tradePair Error: Can't send order.")
    }

    private fun cancelOrder(symbols: TradePair, order: Order, isStaticUpdate: Boolean) {
        val tryTimes = 5
        var trying = 0
        do {
            try {
                client.cancelOrder(symbols, order.orderId, isStaticUpdate)
                log?.debug("$symbols Cancelled order: $order")
                break
            } catch (e: Exception) {
                ++trying
                e.printStackTrace()
                log?.warn("$symbols ${e.stackTrace}", e)
                sendMessage("#Warn_$symbols: $\n${printTrace(e)}")

                if (trying > tryTimes) {
                    log?.error("$symbols can't cancel order ${e.stackTrace}", e)
                    sendMessage("#Error_cannot_cancel_order_$symbols:\n${printTrace(e)}")
                    interruptThis()
                    throw e
                } else {
                    sleep(1.m().toMillis())
                    client = newClient(exchangeEnum, api, sec)
                    val status = getOrder(balance.symbols, order.orderId).status
                    if (status != STATUS.NEW && status != STATUS.PARTIALLY_FILLED) {
                        log?.warn("$symbols Order already cancelled: $order")
                        sendMessage("#Order_already_cancelled_$symbols: $order")
                        break
                    } else
                        log?.debug("$symbols Trying $trying to cancel order: $order")
                }
            }
        } while (true)
    }

    private fun cancelOrInit() {
        val orders = client.getOpenOrders(balance.symbols)
        var orderId: String
        for (order in orders) {
            orderId = order.orderId
            when (orderId) {
                balance.orderB!!.orderId -> {
                    balance.orderB = order
                    log?.info("$tradePair Order buy Synchronized:\n$order")
                }
                balance.orderS!!.orderId -> {
                    balance.orderS = order
                    log?.info("$tradePair Order sell Synchronized:\n$order")
                }
                else -> {
                    client.cancelOrder(balance.symbols, orderId, false)
                    log?.info("$tradePair Order cancelled:\n$order")
                }
            }
        }
        log?.info("$tradePair All orders Synchronized")
    }

    private fun updateStaticOrders() {
        if (isCancelUnknownOrders) cancelUnknownOrders()
        if (isUnknown(balance.orderB)) {
            checkBuyOrder()
            return
        }

        if (isUnknown(balance.orderS)) {
            checkSellOrder()
            return
        }

        if ((balance.orderB!!.origQty - balance.orderB!!.executedQty) * balance.orderB!!.price < minTradeBalance && updateIfAlmostFilled) {
            sendMessage("#$tradePair OrderB #ALMOST_FILLED:\n${strOrder(balance.orderB)}")
            cancelOrder(balance.symbols, balance.orderB!!, false)
            updateBuyOrder()
            return
        }

        if ((balance.orderS!!.origQty - balance.orderS!!.executedQty) * balance.orderS!!.price < minTradeBalance && updateIfAlmostFilled) {
            sendMessage("#$tradePair OrderS #ALMOST_FILLED:\n${strOrder(balance.orderS)}")
            cancelOrder(balance.symbols, balance.orderS!!, false)
            updateSellOrder()
            return
        }

        // Sell = Buy && Buy = Sell  -  because order.side conversely
        val sellPrice = balance.orderB!!.price
        val buyPrice = balance.orderS!!.price

        if (sellPrice > nearSellPrice && buyPrice > nearBuyPrice) {
            log?.warn(
                "$tradePair sellPrice = ${String.format("%.8f", sellPrice)} " +
                        "more than nearSellPrice = ${String.format("%.8f", nearSellPrice)} " +
                        "buyPrice = ${String.format("%.8f", buyPrice)} " +
                        "more than nearBuyPrice = ${String.format("%.8f", nearBuyPrice)}"
            )

            if ((lastSyncTime + syncTimeInterval - time()).isNegative) {
                synchronizeOrders()
                checkBuyOrder()
                checkSellOrder()
                lastSyncTime = time()
            } else
                log?.warn(
                    "$tradePair Requests trying more tran 60 seconds. Time = ${time().format()}; lastSyncTime = " +
                            "${lastSyncTime.format()};"
                )
        }

        if (sellPrice < nearSellPrice && buyPrice < nearBuyPrice) {
            log?.warn(
                "$tradePair sellPrice = ${String.format("%.8f", sellPrice)} " +
                        "less than nearSellPrice = ${String.format("%.8f", nearSellPrice)} " +
                        "buyPrice = ${String.format("%.8f", buyPrice)} " +
                        "less than nearBuyPrice = ${String.format("%.8f", nearBuyPrice)}"
            )

            if ((lastSyncTime + 60.s() - time()).isNegative) {
                synchronizeOrders()
                checkBuyOrder()
                checkSellOrder()
                lastSyncTime = time()
            } else
                log?.warn(
                    "$tradePair Requests trying more tran 60 seconds. Time = ${time().format()}; lastSyncTime = " +
                            "${lastSyncTime.format()};"
                )
        }

        val minPriceDifference =
            if (sellPrice - nearSellPrice > nearBuyPrice - buyPrice)
                nearBuyPrice - buyPrice
            else
                sellPrice - nearSellPrice

        log?.trace(
            "$tradePair Update static IF: $minPriceDifference > ${sellPrice.percent(percent)} &&" +
                    " $minPriceDifference > ${buyPrice.percent(percent)}"
        )

        if (minPriceDifference > sellPrice.percent(percent) && minPriceDifference > buyPrice.percent(percent)) {

            log?.info("$tradePair Update STATIC Orders from:\nB:\n${balance.orderB}\n\nS:\n${balance.orderS}")

            balance.orderB = getOrder(balance.symbols, balance.orderB!!.orderId)

            balance.orderS = getOrder(balance.symbols, balance.orderS!!.orderId)

            if (!isEmulate) {
                reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                writeLine(balance, File("$path/balance.json"))
            }

            log?.debug("$tradePair StatusOrderB: ${balance.orderB!!.status}, StatusOrderS: ${balance.orderS!!.status}")

            if ((balance.orderB!!.status == STATUS.NEW || balance.orderB!!.status == STATUS.PARTIALLY_FILLED) &&
                (balance.orderS!!.status == STATUS.NEW || balance.orderS!!.status == STATUS.PARTIALLY_FILLED)
            ) {

                val priceBuyS = ((balance.orderS!!.price + minPriceDifference) -
                        (balance.orderS!!.price + minPriceDifference).percent(deltaPercent))

                val countToBuyS =
                    balance.orderS!!.origQty * balance.orderS!!.price.div8(priceBuyS) - balance.orderS!!.executedQty

                if (countToBuyS * priceBuyS < minTradeBalance && updateIfAlmostFilled) {
                    sendMessage("#$tradePair OrderS #ALMOST_FILLED:\n${strOrder(balance.orderS)}")
                    cancelOrder(balance.symbols, balance.orderS!!, false)
                    updateBuyOrder()
                    return
                }


                val priceSellB = ((balance.orderB!!.price - minPriceDifference) +
                        (balance.orderB!!.price - minPriceDifference).percent(deltaPercent))

                val countToSellB =
                    balance.orderB!!.origQty * balance.orderB!!.price.div8(priceSellB) - balance.orderB!!.executedQty

                if (countToSellB * priceSellB < minTradeBalance && updateIfAlmostFilled) {
                    sendMessage("#$tradePair OrderB #ALMOST_FILLED:\n${strOrder(balance.orderB)}")
                    cancelOrder(balance.symbols, balance.orderB!!, false)
                    updateSellOrder()
                    return
                }

                if (countToBuyS * priceBuyS > minTradeBalance && countToSellB * priceSellB > minTradeBalance) {
                    sendMessage(
                        "#UpdateStatic_$tradePair Orders from:\nB:\n${strOrder(balance.orderB)}\n\nS:\n" +
                                "${strOrder(balance.orderS)}\n\n${calcGapPercent(balance.orderB!!, balance.orderS!!)}"
                    )

                    cancelOrder(balance.symbols, balance.orderB!!, true)
                    cancelOrder(balance.symbols, balance.orderS!!, true)
                    wait(waitBetweenCancelAndUpdateOrders)

                    balance.orderB = sentOrder(
                        amount = countToSellB,
                        price = priceSellB,
                        orderSide = SIDE.SELL,
                        isStaticUpdate = true
                    )

                    if (!isEmulate) reWriteObject(balance.orderB!!, File("$path/orderB.json"))
                    log?.info("$tradePair STATIC BUY Order UPDATED:\n${balance.orderB}")

                    balance.orderS = sentOrder(
                        amount = countToBuyS,
                        price = priceBuyS,
                        orderSide = SIDE.BUY,
                        isStaticUpdate = true
                    )

                    if (!isEmulate) {
                        reWriteObject(balance.orderS!!, File("$path/orderS.json"))
                        writeLine(balance, File("$path/balance.json"))
                    }

                    log?.info("$tradePair STATIC SELL Order UPDATED:\n${balance.orderS}")
                    sendMessage(
                        "#UpdateStatic_$tradePair Orders to:\nB:\n${strOrder(balance.orderB)}\n\nS:${strOrder(balance.orderS)}" +
                                "\n\n${calcGapPercent(balance.orderB!!, balance.orderS!!)}"
                    )
                } else
                    log?.info(
                        "$tradePair \nSTATIC Orders update cancelled, orders PARTIALLY_FILLED:" +
                                "\nBuy = ${balance.orderB}\nSell = ${balance.orderS}"
                    )
            } else
                log?.warn("$tradePair \nSTATIC Orders update cancelled, BuyOrderStatus is ${balance.orderB!!.status} != NEW, SellOrderStatus is ${balance.orderS!!.status} != NEW")
            log?.info("$tradePair STATIC Update Orders to:\nB:\n${balance.orderB}\n\nS:\n${balance.orderS}")
        }
    }


    private fun synchronizeOrders() {
        if (File("$path/orderB.json").exists()) {
            val oldOrder = readObjectFromFile(File("$path/orderB.json"), Order::class.java)

            balance.orderB = getOrder(balance.symbols, oldOrder.orderId)

            if (balance.orderB?.status == STATUS.FILLED)
                sendMessage("#$tradePair OrderB sync #FILLED:\n${strOrder(balance.orderB)}")

            log?.info("$tradePair OrderB status: ${balance.orderB}")

            if (balance.orderB!!.status != STATUS.NEW && balance.orderB!!.status != STATUS.PARTIALLY_FILLED) {
                removeFile(File("$path/orderB.json"))
                balance.orderB = null
            } else {
                if (!isEmulate) reWriteObject(balance.orderB!!, File("$path/orderB.json"))

                log?.info("$tradePair orderB synchronized: ${balance.orderB}")
            }
        }

        if (File("$path/orderS.json").exists()) {
            val oldOrder = readObjectFromFile(File("$path/orderS.json"), Order::class.java)

            balance.orderS = getOrder(balance.symbols, oldOrder.orderId)

            if (balance.orderS?.status == STATUS.FILLED)
                sendMessage("#$tradePair OrderS sync #FILLED:\n${strOrder(balance.orderS)}")

            log?.info("$tradePair orderS status: ${balance.orderS}")

            if (balance.orderS!!.status != STATUS.NEW && balance.orderS!!.status != STATUS.PARTIALLY_FILLED) {
                removeFile(File("$path/orderS.json"))
                balance.orderS = null
            } else {
                if (!isEmulate) reWriteObject(balance.orderS!!, File("$path/orderS.json"))

                log?.info("$tradePair orderS synchronized: ${balance.orderS}")
            }
        }
    }

    private fun getOrder(pair: TradePair, orderId: String): Order {
        var retryCount = retryGetOrderCount
        do {
            try {
                return client.getOrder(pair, orderId)
            } catch (e: Exception) {
                log?.warn("$pair getOrder trying ${(retryCount - retryGetOrderCount).absoluteValue}:\n", e)
                sendMessage(
                    "#getOrder_${pair}_trying ${(retryCount - retryGetOrderCount).absoluteValue}\n" +
                            printTrace(e, 0)
                )
                sleep(retryGetOrderInterval.toMillis())
            }
        } while (--retryCount > 0)
        throw Exception("Can't get Order! retry = $retryGetOrderCount; interval = $retryGetOrderInterval")
    }

    private fun strOrder(order: Order?) =
        if (order == null) "Order is null"
        else "price = ${String.format("%.8f", order.price)}" +
                "\nqty = ${order.executedQty}/${order.origQty} | ${order.side} ${order.status}"

    private fun createStaticOrdersOnStart() {
        val freeFirstBalance = checkSellOrder() ?: client.getAssetBalance(firstSymbol).free
        if (isCancelUnknownOrders) cancelUnknownOrders()
        if (isUnknown(balance.orderB) && autoCreateAnyOrders) {
            val priceFirst =
                if (averageLow + averageLow.percent(percentSellProf) > nearSellPrice)
                    averageLow + averageLow.percent(percentSellProf)
                else
                    nearSellPrice + nearSellPrice.percent(deltaPercent)

            if (freeFirstBalance > (balance.balanceTrade.div8(priceFirst)) + (balance.balanceTrade.div8(priceFirst)).percent(
                    minOverheadBalance
                )
            ) {
                balance.orderB = sentOrder(
                    amount = balance.balanceTrade.div8(priceFirst),
                    price = priceFirst,
                    orderSide = SIDE.SELL,
                    isStaticUpdate = false
                )
                if (!isEmulate) reWriteObject(balance.orderB!!, File("$path/orderB.json"))
            } else {
                val warnMsg = "#cannot_create_orderB_$tradePair:" +
                        "\n(balance.balanceTrade / price).percent = " +
                        (balance.balanceTrade.div8(priceFirst)).percent(minOverheadBalance) +
                        "\n(balance.balanceTrade / price) = ${(balance.balanceTrade.div8(priceFirst))}" +
                        "\nfreeFirstBalance = $freeFirstBalance"
                log?.warn(warnMsg)
                sendMessage(warnMsg)
            }
        }

        val freeSecondBalance = checkBuyOrder() ?: client.getAssetBalance(secondSymbol).free
        if (isUnknown(balance.orderS) && autoCreateAnyOrders) {
            val priceSecond =
                if (averageHigh - averageHigh.percent(percentBuyProf) < nearBuyPrice)
                    averageHigh - averageHigh.percent(percentBuyProf)
                else
                    nearBuyPrice - nearBuyPrice.percent(deltaPercent)

            if (freeSecondBalance > balance.balanceTrade + balance.balanceTrade.percent(minOverheadBalance)) {
                balance.orderS = sentOrder(
                    amount = balance.balanceTrade.div8(priceSecond),
                    price = priceSecond,
                    orderSide = SIDE.BUY,
                    isStaticUpdate = false
                )
                if (!isEmulate) reWriteObject(balance.orderS!!, File("$path/orderS.json"))
            } else {
                val warnMsg = "#cannot_create_orderS_$tradePair:" +
                        "\nbalanceTrade.percent = ${balance.balanceTrade.percent(minOverheadBalance)}" +
                        "\nbalanceTrade = ${balance.balanceTrade}" +
                        "\nfreeSecondBalance = $freeSecondBalance"
                log?.warn(warnMsg)
                sendMessage(warnMsg)
            }
        }
    }

    private fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    private fun cancelUnknownOrders(orders: List<Order>? = null) = cancelUnknownOrdersInterval.tryInvoke {
        (orders ?: client.getOpenOrders(tradePair)).forEach {
            if (it.orderId != balance.orderB?.orderId && it.orderId != balance.orderS?.orderId)
                cancelOrder(tradePair, it, false)
        }
    }
}