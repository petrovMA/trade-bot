package bot.telegram.notificator.telegram

import bot.telegram.notificator.Communicator
import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import mu.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File
import java.time.Duration
import java.util.concurrent.BlockingQueue

class TelegramBot(
    private val chatId: String,
    private val botUsername: String,
    private val botToken: String,
    exchangeFiles: File,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    candlestickDataPath: Map<ExchangeEnum, String>,
    taskQueue: BlockingQueue<Thread>,
    private val defaultCommands: Map<String, String>
) : TelegramLongPollingBot() {

    private val log = KotlinLogging.logger {}

    val bot: Communicator = Communicator(
            intervalCandlestick = intervalCandlestick,
            intervalStatistic = intervalStatistic,
            timeDifference = timeDifference,
            candlestickDataCommandStr = candlestickDataCommandStr,
            candlestickDataPath = candlestickDataPath,
            taskQueue = taskQueue,
            exchangeFiles = exchangeFiles,
            sendFile = { sendFile(it) }
    ) { sendMessage(it) }

    override fun onUpdateReceived(update: Update) {
        log.info("Income update message: $update")
        bot.onUpdate(update.message.text)
    }

    override fun getBotUsername(): String = botUsername

    override fun getBotToken(): String = botToken

    private fun sendMessage(messageText: String): Unit = try {
        execute(SendMessage().also {
            log.debug("Send to chatId = $chatId\nMessage: \"$messageText\"")
            it.chatId = chatId
            it.text = messageText
        })
        Unit
    } catch (e: Exception) {
        log.error(e.message, e)
    }

    private fun sendFile(resultFile: File): Unit = try {
        val sendDocumentRequest = SendDocument()
        sendDocumentRequest.chatId = chatId
        sendDocumentRequest.document = InputFile(resultFile)
        execute(sendDocumentRequest)
        log.info("Emulate results sent: $resultFile")
    } catch (e: Exception) {
        log.warn("Can't send file with emulate results", e)
    }
}
