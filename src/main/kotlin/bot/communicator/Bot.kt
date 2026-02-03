package bot.communicator

import mu.KLogger
import java.io.File

interface Bot {
    val log: KLogger
    val communicator: Communicator
    public open fun sendMessage(messageText: String, isMarkDown: Boolean = false) {
        log.error { "Method `sendMessage` is not implemented in ${this::class.simpleName}! Message: $messageText" }
    }

    fun sendFile(file: File) {
        log.error { "Method `sendFile` is not implemented in ${this::class.simpleName}! File: ${file.absolutePath}" }
    }

    fun onUpdate(message: String): Unit = communicator.onUpdate(message)
}