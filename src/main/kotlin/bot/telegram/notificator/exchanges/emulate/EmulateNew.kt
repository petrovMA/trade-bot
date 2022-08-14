package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.CandlestickListsIterator
import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.TraderAlgorithmNew
import bot.telegram.notificator.exchanges.connect
import bot.telegram.notificator.exchanges.emulate.libs.writeIntoExcelNew
import bot.telegram.notificator.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.text.toDouble

class EmulateNew(
    val sendFile: (File) -> Unit,
    val sendMessage: (String) -> Unit,
    private val botSettings: BotSettings,
    private val startDate: String,
    private val endDate: String,
    candlestickDataPath: Map<ExchangeEnum, String>,
    exchangeEnum: ExchangeEnum
) : Thread() {

    private val log = KotlinLogging.logger {}
    private val conf = readConf("exchange/emulate/exchange.conf")
        ?: throw ConfigNotFoundException("Config file not found in: 'exchange/emulate/exchange.conf'")

    private val emulateDataPath = candlestickDataPath.getValue(exchangeEnum)

    private val testBalance = TestBalance(
        firstBalance = botSettings.firstBalance,
        secondBalance = botSettings.secondBalance,
        tradePair = TradePair(botSettings.pair)
    )

    override fun run() {
        try {
            sendFile(findParams(testBalance.tradePair, startDate, endDate, emulateDataPath))
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error { "Emulate error:\n$t" }
            sendMessage("Emulate error:\n${printTrace(t)}")
        }
    }

    private fun findParams(pair: TradePair, startDate: String, endDate: String, pathDB: String): File {

        val logging = conf.getBoolean("logging")

        val connect = connect(pathDB, log)

        var result: List<EmulateResult>

        try {
            val calculatedProf = calcProfit(
                client = TestClient(
                    iterator = CandlestickListsIterator(
                        connect,
                        tableName = "CANDLESTICK_$pair",
                        listSize = 500,
                        startDateTime = Timestamp.valueOf(
                            "${startDate.replace('_', '-')} 00:00:00.000000"
                        ),
                        endDateTime = Timestamp.valueOf(
                            "${endDate.replace('_', '-')} 00:00:00.000000"
                        ),
                        interval = INTERVAL.FIVE_MINUTES,
                        fillGaps = true
                    ),
                    balance = testBalance,
                    startCandleNum = (conf.getInt("interval.candles_buy") to conf.getInt("interval.candles_sell"))
                        .run { if (first > second) first else second }
                ),
                logging = logging
            )

            result = listOf(calculatedProf!!)

        } catch (t: Throwable) {
            log.error("CalcProfit ERROR:", t)
            sendMessage("Calc profit ERROR:\n${printTrace(t, 5)}")
            result = emptyList()
        } finally {
            connect.close()
        }

        val resultDir = File("emulate/results")
        if (!resultDir.isDirectory)
            resultDir.mkdirs()

        return writeIntoExcelNew(
            file = File(
                "exchange/emulate/results/${pair}_start_${startDate}___end_${endDate}___time_" +
                        "${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}.xls"
            ),
            head = listOf(
                "pair",
                "Profit\nin ${pair.second}\nBy Last\nPrice",
                "Profit\nin ${pair.second}\nByFirst\nPrice",
                "Execute\norder\ncount",
                "Update\nstatic\norder\ncount",
                "${pair.first}\nBalance",
                "${pair.second}\nBalance",
                "${pair.second}\nBalance\nByFirst\nPrice",
                "${pair.second}\nBalance\nByLast\nPrice",
                "from",
                "to",
            ),
            lines = result
        )
    }

    private fun calcProfit(
        client: TestClient,
        logging: Boolean,
        buyProf: Double? = null,
        sellProf: Double? = null,
        candlesBuy: Int? = null,
        candlesSell: Int? = null,
        updStaticOrders: Double? = null
    ): EmulateResult? {
        try {
            val trade = TraderAlgorithmNew(
                conf = conf,
                queue = client.queue,
                botSettings = botSettings,
//                firstSymbol = client.balance.tradePair.first,
//                secondSymbol = client.balance.tradePair.second,
                balanceTrade = client.balance.balanceTrade,
                exchangeEnum = ExchangeEnum.STUB_TEST,
                client = client,
//                path = "exchange/emulate/data/results/${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}",
//                syncTimeInterval = (-1000).ms(),
//                isLog = logging,
                isEmulate = true
            ) { }

            trade.start()
            trade.join()

            File("exchange/emulate/data/results").delete()

            val firstBalanceA = client.balance.firstBalance.let {
                client.balance.firstBalance + client.getOpenOrders(
                    TradePair(
                        client.balance.tradePair.first,
                        client.balance.tradePair.second
                    )
                ).map { order ->
                    if (order.status == STATUS.NEW && order.side == SIDE.SELL) {
                        order.origQty
                    } else BigDecimal(0)
                }.fold(BigDecimal.ZERO, BigDecimal::add)
            }

            val secondBalanceA = client.balance.secondBalance.let {
                client.balance.secondBalance + client.getOpenOrders(
                    TradePair(
                        client.balance.tradePair.first,
                        client.balance.tradePair.second
                    )
                ).map { order ->
                    if (order.status == STATUS.NEW && order.side == SIDE.BUY) {
                        order.origQty * order.price
                    } else BigDecimal(0)
                }.fold(BigDecimal.ZERO, BigDecimal::add)
            }

            val secondBalanceByFirstPrice: BigDecimal = secondBalanceA + (firstBalanceA * client.firstPrice)
            val secondBalanceByLastPrice: BigDecimal = secondBalanceA + (firstBalanceA * client.lastSellPrice)

            val allStartSecondBalance = (client.startFirstBalance * client.firstPrice) + client.startSecondBalance

            var result: EmulateResult? = null
            try {
                result = EmulateResult(
                    pair = client.balance.tradePair.toString(),
                    profitByLastPrice = String.format("%.4f", secondBalanceByLastPrice - allStartSecondBalance)
                        .replace(',', '.')
                        .toDouble(),
                    profitByFirstPrice = String.format("%.4f", secondBalanceByFirstPrice - allStartSecondBalance)
                        .replace(',', '.')
                        .toDouble(),
                    executeOrderCount = client.executedOrdersCount.toDouble(),
                    updateStaticOrderCount = client.updateStaticOrdersCount.toDouble(),
                    firstBalance = String.format("%.4f", firstBalanceA).replace(',', '.').toDouble(),
                    secondBalance = String.format("%.4f", secondBalanceA).replace(',', '.').toDouble(),
                    secondBalanceByFirstPrice = String.format("%.4f", secondBalanceByFirstPrice).replace(',', '.')
                        .toDouble(),
                    secondBalanceByLastPrice = String.format("%.4f", secondBalanceByLastPrice).replace(',', '.')
                        .toDouble(),
                    from = convertTime(trade.from).removePrefix("20"),
                    to = convertTime(trade.to).removePrefix("20"),
                    buyProfitPercent = buyProf,
                    sellProfitPercent = sellProf,
                    candlesBuyInterval = candlesBuy?.toDouble(),
                    candlesSellInterval = candlesSell?.toDouble(),
                    updStaticOrders = updStaticOrders
                )

            } catch (t: Throwable) {
                t.printStackTrace()
                log.error("Emulate error: ", t)
            }

            return result
        } catch (t: Throwable) {
            log.error("Emulate Error:", t)
            return null
        }
    }

    data class EmulateResult(
        val pair: String,
        val profitByLastPrice: Double,
        val profitByFirstPrice: Double,
        val executeOrderCount: Double,
        val updateStaticOrderCount: Double,
        val firstBalance: Double,
        val secondBalance: Double,
        val secondBalanceByFirstPrice: Double,
        val secondBalanceByLastPrice: Double,
        val from: String,
        val to: String,
        val buyProfitPercent: Double? = null,
        val sellProfitPercent: Double? = null,
        val candlesBuyInterval: Double? = null,
        val candlesSellInterval: Double? = null,
        val updStaticOrders: Double? = null
    )
}