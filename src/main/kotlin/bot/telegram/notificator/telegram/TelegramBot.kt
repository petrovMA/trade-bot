package bot.telegram.notificator.telegram

import bot.telegram.notificator.Communicator
import bot.telegram.notificator.exchanges.clients.ExchangeEnum
import bot.telegram.notificator.libs.escapeMarkdownV2Text
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
    exchangeBotsFiles: String,
    private val botUsername: String,
    botToken: String,
    exchangeFiles: File,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    candlestickDataPath: Map<ExchangeEnum, String>,
    taskQueue: BlockingQueue<Thread>,
    private val defaultCommands: Map<String, String>
) : TelegramLongPollingBot(botToken) {

    private val log = KotlinLogging.logger {}

    val communicator: Communicator = Communicator(
        intervalCandlestick = intervalCandlestick,
        exchangeBotsFiles = exchangeBotsFiles,
        intervalStatistic = intervalStatistic,
        timeDifference = timeDifference,
        candlestickDataCommandStr = candlestickDataCommandStr,
        candlestickDataPath = candlestickDataPath,
        taskQueue = taskQueue,
        exchangeFiles = exchangeFiles,
        sendFile = { sendFile(it) },
        sendMessage = { message, isMarkDown -> sendMessage(message, isMarkDown) }
    )

    override fun onUpdateReceived(update: Update) {
        log.info("Income update message: $update")
        communicator.onUpdate(update.message.text)
    }

    override fun getBotUsername(): String = botUsername

    private fun sendMessage(messageText: String, isMarkDown: Boolean = false): Unit = try {
        execute(SendMessage().also {
            log.debug("Send to chatId = $chatId\nMessage: \"$messageText\"")
            it.chatId = chatId
            it.text = messageText.let { text ->
                if (isMarkDown) escapeMarkdownV2Text(text)
                else text
            }
            it.enableMarkdownV2(isMarkDown)
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
