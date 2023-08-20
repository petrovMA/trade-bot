package bot.trade

import mu.KotlinLogging
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

class TaskExecutor(private val tasks: BlockingQueue<Thread>) : Thread() {

    private val log = KotlinLogging.logger {}

    override fun run() {
        do {
            try {
                tasks.poll(30, TimeUnit.MINUTES)?.run { start(); join() }
            } catch (e: InterruptedException) {
                log.debug("Waiting task: {}", e.printStackTrace())
            } catch (t: Throwable) {
                log.error("Waiting task error:", t)
            }
        } while (true)
    }

    fun getQueue() = tasks
}