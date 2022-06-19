package bot.telegram.notificator

import bot.telegram.notificator.libs.m
import bot.telegram.notificator.libs.readConf
import io.bybit.api.rest.client.ByBitRestApiClient
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")

    val conf = readConf("exchange/BTC_USDC/exchange.conf")!!

    val api = conf.getString("api")
    val sec = conf.getString("sec")

    ByBitRestApiClient(api, sec).apply {
        val resultOrderBook = getOrderBook("BTCUSD")
        println(resultOrderBook)

        val resultKline = getKline(
            "BTCUSD",
            ByBitRestApiClient.INTERVAL.FIVE_MINUTES,
            (System.currentTimeMillis() / 1000 - 300.m().toMillis() / 1000)
        )
        println(resultKline)

        val time = getTime()
        println(time.time_now)

        val balance = getBalance()
        println(balance)

        val createOrder = orderCreate(
            side = "Buy",
            symbol = "BTCUSD",
            order_type = "Market",
            qty = "10",
            time_in_force = "GoodTillCancel"
        )
        println(createOrder)
    }
}
