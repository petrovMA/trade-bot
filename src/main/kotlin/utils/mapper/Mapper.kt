package utils.mapper

import bot.trade.exchanges.clients.BotSettings
import bot.trade.exchanges.clients.BotSettingsBobblesIndicator
import bot.trade.exchanges.clients.BotSettingsTrader
import com.google.gson.*
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type

/**
 * Converts json to java object and vice versa
 */
object Mapper {
    val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(BotSettings::class.java, BotSettingsDeserializer())
        .create()

    @JvmStatic
    inline fun <reified T> asObject(json: String): T = gson.fromJson(json, T::class.java)

    @JvmStatic
    fun <T> asListObjects(json: String, type: Type): List<T> = gson.fromJson(json, type)

    @JvmStatic
    fun <T> asObject(file: File, clazz: Class<T>): T = gson.fromJson(FileReader(file), clazz)

    @JvmStatic
    fun <T> asListObjects(file: File, type: Type): List<T> = gson.fromJson(FileReader(file), type)

    @JvmStatic
    fun <K, V> asMapObjects(file: File, type: Type): Map<K, V> = gson.fromJson(FileReader(file), type)

    @JvmStatic
    fun asString(message: Any): String = gson.toJson(message)

    @JvmStatic
    fun asFile(message: Any, file: File) {
        FileWriter(file).use {
            gson.toJson(message, it)
            it.flush()
            it.close()
        }
    }

    class BotSettingsDeserializer : JsonDeserializer<BotSettings> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): BotSettings {
            val jsonObject = json.asJsonObject

            return when (jsonObject.get("type").asString) {
                "bobbles" -> context.deserialize(json, BotSettingsBobblesIndicator::class.java)
                else -> context.deserialize(json, BotSettingsTrader::class.java)
            }
        }
    }
}