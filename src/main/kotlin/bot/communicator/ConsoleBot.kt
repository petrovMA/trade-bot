package bot.communicator

import mu.KotlinLogging

class ConsoleBot(override val communicator: Communicator) : Bot {
    init {
        Thread {
            while (true) {
                val input = readlnOrNull()
                input?.let { communicator.onUpdate(it) }
            }
        }.start()
    }
    override val log = KotlinLogging.logger {}
    override fun sendMessage(messageText: String, isMarkDown: Boolean): Unit = println(messageText)
}
