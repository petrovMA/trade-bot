package bot.trade.exchanges

import bot.trade.exchanges.clients.Order
import bot.trade.libs.reWriteObject
import bot.trade.libs.readObjectFromFile
import bot.trade.libs.removeFile
import mu.KotlinLogging
import java.io.File

class ObservableHashMap(
    private val map: MutableMap<String, Order> = mutableMapOf(),
    private val filePath: String,
    private val keyToFileName: (String) -> String,
    private val fileNameToKey: (String) -> String
) : MutableMap<String, Order> by map {

    private val log = KotlinLogging.logger {}

    override fun put(key: String, value: Order): Order? = map.put(key, value).also {
        try {
            reWriteObject(value, File("$filePath/${keyToFileName(key)}"))
        } catch (t: Throwable) {
            log.error("Error reWriteObject: $value\n", t)
        }
    }

    override fun remove(key: String): Order? = map.remove(key)?.also {
        try {
            removeFile(File("$filePath/${keyToFileName(key)}"))
        } catch (t: Throwable) {
            log.error("Error removeFile: $it\n", t)
        }
    }

    fun readFromFile() {
        File(filePath).listFiles()?.forEach {
            try {
                map[fileNameToKey(it.name)] = readObjectFromFile(it, Order::class.java)
            } catch (t: Throwable) {
                log.error("Error readObjectFromFile, reading this file will be skipped: $it\n", t)
            }
        }
    }
}