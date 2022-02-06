package bot.telegram.notificator

import bot.telegram.notificator.exchanges.clients.ClientByBit
import mu.KotlinLogging


private val log = KotlinLogging.logger {}

fun main() {
    ClientByBit().getCandles()
}
