package bot.trade.libs

import mu.KLogger
import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.io.File
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter

class CustomFileLoggingProcessor(private val logMessageQueue: BlockingQueue<Message> = LinkedBlockingQueue()) {
    private val log: KLogger = KotlinLogging.logger {}
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    init {
        Thread {
            while (true) {
                try {
                    val message = logMessageQueue.take() // This will wait if the queue is empty
                    processMessage(message)
                } catch (e: InterruptedException) {
                    log.entry("Interrupted while waiting for message on queue", e)
                    break
                }
            }
        }.start()
    }

    private fun processMessage(message: Message) {
        if (message.outputFile.exists().not())
            message.outputFile.createNewFile()

        message.outputFile.appendText("\n${now().format(formatter)} - ${message.text}")
    }

    data class Message(val outputFile: File, val text: String)
}
