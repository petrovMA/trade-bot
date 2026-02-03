package bot.communicator

import bot.trade.libs.escapeMarkdownV2Text
import bot.trade.libs.m
import com.typesafe.config.Config
import mu.KotlinLogging
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.send.SendDocument
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.objects.InputFile
import org.telegram.telegrambots.meta.api.objects.Update
import java.io.File

class TelegramBot(
    config: Config,
    override val communicator: Communicator,
    private val chatId: String = config.getString("bot_properties.bot.chat_id"),
    private val adminId: String = config.getString("bot_properties.bot.admin_id"),
    private val botUsername: String = config.getString("bot_properties.bot.bot_name"),
    botToken: String = config.getString("bot_properties.bot.bot_token")
) : TelegramLongPollingBot(botToken), Bot {

    private val orderIds = mutableMapOf<String, Long>()

    override val log = KotlinLogging.logger {}
    private val regex = """"orderId":\s*"([a-fA-F0-9-]+)"""".toRegex()

    override fun onUpdateReceived(update: Update) {
        log.info("Income update message: $update")
        val text = try {
            if (chatId == adminId) update.message.text
            else {
                if (update.message.from.id.toString() == adminId && Regex("@?$botUsername .+\\s+[\\-+\\w:,\"{}\\.\\s]+").matches(update.message.text))
                    update.message.text.replace(Regex("@?$botUsername\\s+"), "")
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

        text?.let { onUpdate(it) }
    }

    override fun getBotUsername(): String = botUsername

    override fun sendMessage(messageText: String, isMarkDown: Boolean): Unit = try {

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
        log.error(e) { "Error sending message: $messageText" }
    }

    override fun sendFile(resultFile: File): Unit = try {
        val sendDocumentRequest = SendDocument()
        sendDocumentRequest.chatId = chatId
        sendDocumentRequest.document = InputFile(resultFile)
        execute(sendDocumentRequest)
        log.info("Emulate results sent: $resultFile")
    } catch (e: Exception) {
        log.warn(e) { "Can't send file with emulate results: ${resultFile.absolutePath}" }
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
