package bot.telegram

import bot.trade.Communicator
import bot.trade.database.service.OrderService
import bot.trade.exchanges.clients.ExchangeEnum
import bot.trade.libs.CustomFileLoggingProcessor
import bot.trade.libs.escapeMarkdownV2Text
import bot.trade.libs.m
import mu.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingDeque

class TelegramBot(
    private val chatId: String,
    private val adminId: String,
    exchangeBotsFiles: String,
    orderService: OrderService,
    private val botUsername: String,
    botToken: String,
    exchangeFiles: File,
    intervalCandlestick: Duration?,
    intervalStatistic: Duration?,
    timeDifference: Duration?,
    candlestickDataCommandStr: String?,
    candlestickDataPath: Map<ExchangeEnum, String>,
    logMessageQueue: LinkedBlockingDeque<CustomFileLoggingProcessor.Message>? = null,
    taskQueue: BlockingQueue<Thread>,
    val tempUrlCalcHma: String,
    private val defaultCommands: Map<String, String>
) : TelegramLongPollingBot(botToken) {

    private val orderIds = mutableMapOf<String, Long>()

    private val log = KotlinLogging.logger {}
    private val regex = """"orderId":\s*"([a-fA-F0-9-]+)"""".toRegex()

    val communicator: Communicator = Communicator(intervalCandlestick = intervalCandlestick,
        exchangeBotsFiles = exchangeBotsFiles,
        orderService = orderService,
        intervalStatistic = intervalStatistic,
        timeDifference = timeDifference,
        candlestickDataCommandStr = candlestickDataCommandStr,
        candlestickDataPath = candlestickDataPath,
        taskQueue = taskQueue,
        exchangeFiles = exchangeFiles,
        logMessageQueue = logMessageQueue,
        sendFile = { sendFile(it) },
        tempUrlCalcHma = tempUrlCalcHma,
        sendMessage = { message, isMarkDown -> sendMessage(message, isMarkDown) })

    override fun onUpdateReceived(update: Update) {
        log.info("Income update message: $update")
        val text = try {
            if (chatId == adminId) update.message.text
            else {
                if (update.message.from.id.toString() == adminId && Regex("@?$botUsername.+").matches(update.message.text)) update.message.text.replace(
                    Regex("@?$botUsername\\s+"),
                    ""
                )
                else null
            }
        } catch (e: java.lang.NullPointerException) {
            if (update.inlineQuery.from.id.toString() == adminId) null
            //update.inlineQuery.query
            else null
        } catch (e: java.lang.NullPointerException) {
            update.channelPost.text
        } catch (e: java.lang.NullPointerException) {
            log.error("Can't get text from update: $update", e)
            throw e
        }

        text?.let { communicator.onUpdate(it) }
    }

    override fun getBotUsername(): String = botUsername

    private fun sendMessage(messageText: String, isMarkDown: Boolean = false): Unit = try {

        if (getOrderIdFromMessages(messageText) != null) Unit // todo:: STUB for filter orders notifications by orderId
        else execute(SendMessage().also {
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


    // todo:: STUB for filter orders notifications by orderId
    private fun getOrderIdFromMessages(text: String): String? {
        val id = regex.find(text)?.groups?.get(1)?.value

        if (id != null) {
            orderIds[id]?.let {
                orderIds.entries.removeIf { (_, v) -> (v < System.currentTimeMillis() - 5.m().toMillis()) }
                return text
            } ?: run {
                orderIds[id] = System.currentTimeMillis()
                return null
            }
        } else return null
    }
}
