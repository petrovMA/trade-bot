package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.stream.Stream
import bot.trade.exchanges.params.BotSettings
import bot.trade.exchanges.params.Param
import com.typesafe.config.Config
import mu.KotlinLogging
import org.knowm.xchange.exceptions.ExchangeException
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.LinkedBlockingDeque
import kotlin.math.absoluteValue


abstract class Algorithm(
    val botSettings: BotSettings,
    exchangeBotsFiles: String,
    val queue: LinkedBlockingDeque<CommonExchangeData>,
    val exchangeEnum: ExchangeEnum,
    val conf: Config,
    val api: String,
    val sec: String,
    var client: Client,
    isLog: Boolean,
    val isEmulate: Boolean,
    private val logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
    val sendMessage: (String, Boolean) -> Unit
) : Thread() {
    val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()

    protected val path: String = "$exchangeBotsFiles/${botSettings.name}".also { File(it).mkdirs() }
    private val settingsPath = "$path/settings.json".also { if (!isEmulate) saveBotSettings(botSettings, it) }

    private val log = if (isLog) KotlinLogging.logger {} else null

    val waitTime = conf.getDuration("interval.wait_socket_time")!!
    val formatAmount = "%.${botSettings.countOfDigitsAfterDotForAmount}f"
    val formatPrice = "%.${botSettings.countOfDigitsAfterDotForPrice}f"

    val firstBalanceForOrderAmount = botSettings.orderBalanceType.equals("second", true).not()

    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retrySentOrderCount: Int = conf.getInt("retry.sent_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")

    var stopThread = false
    var currentPrice: BigDecimal = 0.toBigDecimal()
    var prevPrice: BigDecimal = 0.toBigDecimal()

    var stream: Stream = client.stream(botSettings.pair, interval, queue)

    /**
     * custom actions before start thread
     */
    abstract fun setup()

    override fun run() {
        setup()
        stopThread = false
        try {
            synchronizeOrders()

            stream.run { start() }

            var msg = queue.poll(waitTime)

            do {
                try {
                    handle(msg)

                    if (stopThread) break

                    msg = queue.poll(waitTime)

                } catch (e: InterruptedException) {
                    log?.error("${botSettings.name} ${e.message}", e)
                    send("#Error_${botSettings.name}: \n${printTrace(e)}")
                    if (stopThread) break
                }
            } while (stopThread.not())

        } catch (e: Exception) {
            log?.error("${botSettings.name} MAIN ERROR:\n", e)
            send("#Error_${botSettings.name}: \n${printTrace(e)}")
        } finally {
            stopThis()
        }
    }

    abstract fun handle(msg: CommonExchangeData?)

    fun stopThis(msg: String? = null) {
        stream.interrupt()
        var msgErr = "#Interrupt #${botSettings.name} Thread, socket.status = ${stream.state}"
        var logErr = "Thread for ${botSettings.name} Interrupt, socket.status = ${stream.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        send(msgErr)
        log?.info(logErr)
        stopThread = true
    }

    fun sentOrder(
        price: BigDecimal,
        amount: BigDecimal,
        orderSide: SIDE,
        orderType: TYPE,
        isStaticUpdate: Boolean = false,
        positionSide: DIRECTION? = null,
        isReduceOnly: Boolean = false
    ): Order {

        var retryCount = retrySentOrderCount

        var order = Order(
            orderId = "",
            pair = botSettings.pair,
            price = String.format(formatPrice, price).replace(",", ".").toBigDecimal(),
            origQty = String.format(formatAmount, amount).replace(",", ".").toBigDecimal(),
            executedQty = BigDecimal(0),
            side = orderSide,
            type = orderType,
            status = STATUS.NEW
        )

        log("Send to exchange order: ${json(order)}")

        do {
            try {
                val before = System.currentTimeMillis()
                val qtyStr = String.format(formatAmount, order.origQty).replace(",", ".")
                val priceStr = String.format(formatPrice, order.price).replace(",", ".")
                order = client.newOrder(order, isStaticUpdate, qtyStr, priceStr, positionSide, isReduceOnly)
                val after = System.currentTimeMillis()
                log?.info("{}, request time = {}ms, Order sent: price = {}; amount = {} side = {}", botSettings.name, after - before, order.price, order.origQty, order.side)
                return order
            } catch (be: ExchangeException) {
                throw be
            } catch (e: Exception) {

                if (e.message?.contains("Account has insufficient balance for requested action.") == true) {
                    throw Exception(
                        "${botSettings.pair} Account has insufficient balance for requested action.\n" +
                                "#insufficient_${botSettings.pair}_balance_for: $order\n" +
                                "${botSettings.pair.first} = ${client.getAssetBalance(botSettings.pair.first)}\n" +
                                "${botSettings.pair.second} = ${client.getAssetBalance(botSettings.pair.second)}"
                    )
                }

                retryCount--
                send("#Cannot_send_order #${botSettings.name}: $order\nError:\n${printTrace(e, 50)}")
                log?.error("${botSettings.name} Can't send: $order", e)

                e.printStackTrace()
                client = newClient(exchangeEnum, api, sec)
                synchronizeOrders()
            }
            try {
                sleep(5000)
            } catch (e: InterruptedException) {
                log?.error("${botSettings.name} ${e.stackTrace}", e)
                send("#Error_${botSettings.name}: \n${printTrace(e)}")
            }

        } while (isUnknown(order) && retryCount > 0)
        stopThis("Error: Can't send order.")
        throw Exception("${botSettings.name} Error: Can't send order.")
    }

    abstract fun synchronizeOrders()

    fun getOrder(pair: TradePair, orderId: String): Order? {
        var retryCount = retryGetOrderCount
        do {
            try {
                return client.getOrder(pair, orderId)
            } catch (e: Exception) {
                log?.warn(
                    "$pair ${botSettings.name} getOrder trying ${(retryCount - retryGetOrderCount).absoluteValue}:\n",
                    e
                )
                send(
                    "#${botSettings.name} #getOrder_${pair}_trying ${(retryCount - retryGetOrderCount).absoluteValue}\n" +
                            printTrace(e, 0)
                )
                sleep(retryGetOrderInterval.toMillis())
            }
        } while (--retryCount > 0)
        throw Exception("Can't get Order! retry = $retryGetOrderCount; interval = $retryGetOrderInterval")
    }

    fun cancelOrder(symbols: TradePair, order: Order, isStaticUpdate: Boolean = false) {
        val tryTimes = 5
        var trying = 0
        do {
            try {
                client.cancelOrder(symbols, order.orderId, isStaticUpdate)
                log?.debug("{} Cancelled order: {}", symbols, order)
                break
            } catch (e: Exception) {
                ++trying
                e.printStackTrace()
                log?.warn("$symbols ${e.stackTrace}", e)
                send("#Warn_$symbols: $\n${printTrace(e)}")

                if (trying > tryTimes) {
                    log?.error("$symbols can't cancel order ${e.stackTrace}", e)
                    send("#Error_cannot_cancel_order_$symbols:\n${printTrace(e)}")
                    stopThread = true
                    throw e
                } else {
                    sleep(1.m().toMillis())
                    client = newClient(exchangeEnum, api, sec)
                    val status = getOrder(botSettings.pair, order.orderId)?.status
                    if (status != STATUS.NEW && status != STATUS.PARTIALLY_FILLED) {
                        log?.warn("$symbols Order already cancelled: $order")
                        send("#Order_already_cancelled_$symbols: $order")
                        break
                    } else
                        log?.debug("{} Trying {} to cancel order: {}", symbols, trying, order)
                }
            }
        } while (true)
    }

    fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this.round())

    fun saveBotSettings(botSettings: BotSettings, settingsPath: String = this.settingsPath) {
        if (isEmulate.not()) {
            val settingsDir = File(path)

            if (settingsDir.isDirectory.not()) Files.createDirectories(Paths.get(path))

            val settingsFile = File(settingsPath)

            reWriteObject(botSettings, settingsFile)
        }
    }

    fun price(price: BigDecimal) = price.round(botSettings.countOfDigitsAfterDotForPrice)

    fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)

    fun orderDistance(currPrice: BigDecimal, orderDistance: Param): BigDecimal =
        if (orderDistance.usePercent) currPrice.percent(orderDistance.value)
        else orderDistance.value

    override fun toString(): String = "status = $state, settings = $botSettings"

    fun log(message: String, file: File = File("logging/$path/common_log.txt")) =
        if (isEmulate.not()) logMessageQueue?.add(CustomFileLoggingProcessor.Message(file, message))
        else false
}