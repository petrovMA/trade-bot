package bot.telegram.notificator.exchanges.emulate

import bot.telegram.notificator.exchanges.clients.*
import bot.telegram.notificator.exchanges.emulate.libs.writeIntoExcel
import bot.telegram.notificator.exchanges.Trade
import bot.telegram.notificator.libs.*
import mu.KotlinLogging
import java.io.File
import java.lang.StringBuilder
import java.time.LocalDateTime
import kotlin.text.toDouble

class Emulate(
    val sendFile: (File) -> Unit,
    val sendMessage: (String) -> Unit,
    private val regex: Regex,
    private val startDate: String,
    private val endDate: String,
    candlestickDataPath: Map<ExchangeEnum, String>,
    exchangeEnum: ExchangeEnum
) : Thread() {

    private val log = KotlinLogging.logger {}
    private val conf = readConf("exchange/emulate/exchange.conf")
            ?: throw ConfigNotFoundException("Config file not found in: 'exchange/emulate/exchange.conf'")

    private val emulateDataPath = candlestickDataPath.getValue(exchangeEnum)

    override fun run() {
        try {
            val result = emulate(regex, startDate, endDate, emulateDataPath)
            sendFile(result)
        } catch (t: Throwable) {
            t.printStackTrace()
            log.error { "Emulate error:\n$t" }
            sendMessage("Emulate error:\n${printTrace(t)}")
        }
    }

    private fun emulate(regex: Regex, startDate: String, endDate: String, emulateDataPath: String): File {
        val builder = StringBuilder()

        val result = File(emulateDataPath).listFiles()!!.filter { it.name.matches(regex) }
                .map { file ->
                    val tradePair = TradePair(file.name)
                    val firstDay = startDate.toLocalDate()
                    val lastDay = endDate.toLocalDate()
                    try {
                        calcProfit(TestClient(
                                filesWithCandlesticks = file.listFiles()!!
                                        .filter { it.name.matches("\\d{4}_\\d\\d".toRegex()) }
                                        .filter {
                                            "${it.name}_31".toLocalDate().run { isAfter(firstDay) || isEqual(firstDay) } &&
                                                    "${it.name}_01".toLocalDate().run { isBefore(lastDay) || isEqual(lastDay) }
                                        },
                                balance = TestBalance(
                                        secondBalance = 100.0,
                                        balanceTrade = 50.0,
                                        tradePair = tradePair
                                ),
                                startCandleNum = (conf.getInt("interval.candles_buy") to conf.getInt("interval.candles_sell"))
                                        .run { if (first > second) first else second }
                        ))
                    } catch (t: Throwable) {
                        builder.append(t).append('\n')
                    }
                }
                .filterIsInstance<List<String>>()
                .sortedBy { (it[6].toDouble()) * -1 }
                .toMutableList()


        builder.append(result.joinToString("\n", "\n") { "${it[0]} ${it[1]} ${it[2]} ${it[3]} ${it[4]}   Profit ByLastPrice: ${it[5]}    Profit ByFirstPrice: ${it[6]}" })

        result.add(0,
                listOf(
                        "pair",
                        "firstBalance",
                        "secondBalance",
                        "secondBalanceByLastPrice",
                        "secondBalanceByFirstPrice",
                        "Profit ByLastPrice",
                        "Profit ByFirstPrice",
                        "Execute order count",
                        "Update static order count"
                ))

        val resultDir = File("emulate/results")
        if (!resultDir.isDirectory)
            resultDir.mkdirs()

        return writeIntoExcel(
                File("exchange/emulate/results/start_${startDate}___end_${endDate}___time_${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}.xls"),
                result
        )
    }

    private fun calcProfit(client: TestClient): List<String> {
        val trade = Trade(
                conf = conf,
                queue = client.queue,
                firstSymbol = client.balance.tradePair.first,
                secondSymbol = client.balance.tradePair.second,
                balanceTrade = client.balance.balanceTrade,
                exchangeEnum = ExchangeEnum.STUB_TEST,
                client = client,
                path = "exchange/emulate/data/results/${convertTime(LocalDateTime.now(), "yyyy_MM_dd__HH_mm_ss")}",
                syncTimeInterval = (-1000).ms(),
                log = null
        ) { }
        trade.start()
        trade.join()

        val firstBalanceA = client.balance.firstBalance.let {
            client.balance.firstBalance + client.getOpenOrders(TradePair(client.balance.tradePair.first, client.balance.tradePair.second)).map { order ->
                if (order.status == STATUS.NEW && order.side == SIDE.SELL) {
                    order.origQty
                } else 0.0
            }.sum()
        }

        val secondBalanceA = client.balance.secondBalance.let {
            client.balance.secondBalance + client.getOpenOrders(TradePair(client.balance.tradePair.first, client.balance.tradePair.second)).map { order ->
                if (order.status == STATUS.NEW && order.side == SIDE.BUY) {
                    order.origQty * order.price
                } else 0.0
            }.sum()
        }

        val secondBalanceByLastPrice = secondBalanceA + (firstBalanceA * client.lastSellPrice)
        val secondBalanceByFirstPrice = secondBalanceA + (firstBalanceA * client.firstPrice)

        var list = emptyList<String>()
        try {
            list = listOf(
                    client.balance.tradePair.toString(),
                    String.format("%.4f", firstBalanceA),
                    String.format("%.4f", secondBalanceA),
                    String.format("%.4f", secondBalanceByLastPrice),
                    String.format("%.4f", secondBalanceByFirstPrice),
                    String.format("%.4f", secondBalanceByLastPrice - ((client.startFirstBalance * client.lastSellPrice) + client.startSecondBalance)),
                    String.format("%.4f", secondBalanceByFirstPrice - ((client.startFirstBalance * client.firstPrice) + client.startSecondBalance)),
                    "${client.executedOrdersCount}",
                    "${client.updateStaticOrdersCount}"
            )

        } catch (t: Throwable) {
            t.printStackTrace()
            log.error("Emulate error: ", t)
        }

        return list
    }
}