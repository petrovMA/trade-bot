package bot.trade.exchanges

import bot.trade.libs.*
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.clients.stream.Stream
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
    val sendMessage: (String, Boolean) -> Unit
) : Thread() {
    val interval: INTERVAL = conf.getString("interval.interval")!!.toInterval()

    protected val path: String = "$exchangeBotsFiles/${botSettings.name}".also { File(it).mkdirs() }
    private val settingsPath = "$path/settings.json".also { saveBotSettings(botSettings, it) }

    private val log = if (isLog) KotlinLogging.logger {} else null

    val waitTime = conf.getDuration("interval.wait_socket_time")!!
    val formatAmount = "%.${botSettings.countOfDigitsAfterDotForAmount}f"
    val formatPrice = "%.${botSettings.countOfDigitsAfterDotForPrice}f"

    val firstBalanceForOrderAmount = botSettings.orderBalanceType == "first"

    private val retryGetOrderCount = conf.getInt("retry.get_order_count")
    private val retrySentOrderCount: Int = conf.getInt("retry.sent_order_count")
    private val retryGetOrderInterval = conf.getDuration("retry.get_order_interval")

    var stopThread = false
    var currentPrice: BigDecimal = 0.toBigDecimal()
    var prevPrice: BigDecimal = 0.toBigDecimal()

    val ordersPath = "$path/orders"
    open val orders: MutableMap<String, Order> =
        if (isEmulate.not()) ObservableHashMap(
            filePath = ordersPath,
            keyToFileName = { key -> key.replace('.', '_') + ".json" },
            fileNameToKey = { key -> key.replace('_', '.').replace(".json", "") }
        )
        else mutableMapOf()

    var stream: Stream = client.stream(botSettings.pair, interval, queue)

    /**
     * custom actions before start thread
     */
    abstract fun setup()

    override fun run() {
        setup()
        saveBotSettings(botSettings)
        stopThread = false
        try {
            if (isEmulate.not() && File(ordersPath).isDirectory.not()) Files.createDirectories(Paths.get(ordersPath))

            synchronizeOrders()

            stream.run { start() }

            var msg = if (isEmulate) client.nextEvent() /* only for test */
            else queue.poll(waitTime)

            do {
                if (stopThread) return
                try {

                    handle(msg)

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

    abstract fun handle(msg: CommonExchangeData?)

    fun interruptThis(msg: String? = null) {
        stream.interrupt()
        var msgErr = "#Interrupt #${botSettings.name} Thread, socket.status = ${stream.state}"
        var logErr = "Thread for ${botSettings.name} Interrupt, socket.status = ${stream.state}"
        msg?.let { msgErr = "$msgErr\nMessage: $it"; logErr = "$logErr\nMessage: $it"; }
        send(msgErr)
        log?.warn(logErr)
        stopThread = true
    }

    fun sentOrder(
        price: BigDecimal,
        amount: BigDecimal,
        orderSide: SIDE,
        orderType: TYPE,
        isStaticUpdate: Boolean = false
    ): Order {

        var retryCount = retrySentOrderCount

        log?.info("${botSettings.name} Sent $orderType order with params: price = $price; amount = $amount; side = $orderSide")

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

        do {
            try {
                val before = System.currentTimeMillis()
                val qtyStr = String.format(formatAmount, order.origQty).replace(",", ".")
                val priceStr = String.format(formatPrice, order.price).replace(",", ".")
                order = client.newOrder(order, isStaticUpdate, qtyStr, priceStr)
                val after = System.currentTimeMillis()
                log?.info("{}, request time = {}ms, Order sent: {}", botSettings.name, after - before, order)
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
                log?.debug("{} Orders:\n{}", botSettings.name, orders)
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
        interruptThis("Error: Can't send order.")
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

    fun isUnknown(order: Order?): Boolean =
        order == null || order.status == STATUS.UNSUPPORTED || order.side == SIDE.UNSUPPORTED

    fun BigDecimal.toPrice() = String.format(Locale.US, "%.8f", this)

    fun saveBotSettings(botSettings: BotSettings, settingsPath: String = this.settingsPath) {
        if(isEmulate.not()) {
            val settingsDir = File(path)

            if (settingsDir.isDirectory.not()) Files.createDirectories(Paths.get(path))

            val settingsFile = File(settingsPath)

            reWriteObject(botSettings, settingsFile)
        }
    }

    abstract fun calcAmount(amount: BigDecimal, price: BigDecimal): BigDecimal

    fun price(price: BigDecimal) = price.round(botSettings.countOfDigitsAfterDotForPrice)

    fun send(message: String, isMarkDown: Boolean = false) = sendMessage(message, isMarkDown)

    override fun toString(): String = "status = $state, settings = $botSettings"

    fun orders() = botSettings to orders
}