package bot.trade.exchanges.emulate

import bot.trade.Communicator
import bot.trade.exchanges.CandlestickListsIterator
import bot.trade.exchanges.clients.*
import bot.trade.exchanges.connect
import bot.trade.exchanges.emulate.libs.writeIntoExcelNew
import bot.trade.libs.*
import mu.KotlinLogging
import java.io.File
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.text.toDouble

class EmulateFromFile(
    private val sendMessage: (String, Boolean) -> Unit,
    private val emulateParams: BotEmulateParams,
    private val communicatorBot: Communicator
) : Thread() {


    override fun run() {

        val emulateResponse = communicatorBot.emulate(emulateParams)

        val msg = "#EmulateResponse params:\n```json\n${json(emulateParams)}\n```\n" +
                "\n\nResponse:\n```json\n${json(emulateResponse)}\n```"

        sendMessage(msg, true)
    }
}