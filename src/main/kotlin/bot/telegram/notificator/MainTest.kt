package bot.telegram.notificator

import bot.telegram.notificator.libs.m
import io.bybit.api.rest.client.ByBitRestApiClient
import mu.KotlinLogging
import org.apache.log4j.PropertyConfigurator


private val log = KotlinLogging.logger {}

fun main() {
    PropertyConfigurator.configure("log4j.properties")
//    ClientByBit().getCandles()
    val resultOrderBook = ByBitRestApiClient().getOrderBook("BTCUSD")
    println(resultOrderBook)

    val resultKline = ByBitRestApiClient().getKline("BTCUSD", ByBitRestApiClient.INTERVAL.FIVE_MINUTES, (System.currentTimeMillis()/1000 - 300.m().toMillis()/1000))
    println(resultKline)
}
