package bot.trade.exchanges.parallel_tasks

import bot.trade.Communicator
import bot.trade.exchanges.params.BotEmulateParams
import bot.trade.libs.*
import java.io.File

class EmulateFromFile(
    private val sendMessage: (String, Boolean) -> Unit,
    private val sendFile: (File) -> Unit,
    val emulateParams: BotEmulateParams,
    private val communicatorBot: Communicator
) : Thread() {


    override fun run() {

        val emulateResponse = communicatorBot.emulate(emulateParams)

        val msg = "#EmulateResponse params:\n```json\n${json(emulateParams)}\n```\n" +
                "\n\nResponse:\n```json\n${json(emulateResponse.first)}\n```"

        sendMessage(msg, true)
        emulateResponse.second?.let { sendFile(it) }
    }
}