package bot.communicator

import bot.trade.exchanges.clients.log
import bot.trade.libs.UnsupportedClientException
import com.typesafe.config.Config
import org.telegram.telegrambots.meta.TelegramBotsApi
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession

enum class BotType {
    CONSOLE,
    TELEGRAM,
    WEBSITE;


    companion object {
        fun BotType.newBot(communicator: Communicator, config: Config): Bot = when (this) {
            CONSOLE -> ConsoleBot(communicator)
            WEBSITE -> throw UnsupportedClientException()
            TELEGRAM -> try {
                TelegramBot(config = config, communicator = communicator)
                    .also { TelegramBotsApi(DefaultBotSession::class.java).registerBot(it) }
            } catch (e: Exception) {
                e.printStackTrace()
                log.error(e.message, e)
                throw e
            }

            else -> throw UnsupportedClientException()
        }
    }
}