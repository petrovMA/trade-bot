package utils.mapper

import bot.telegram.notificator.exchanges.clients.BotSettings
import bot.telegram.notificator.exchanges.clients.BotSettingsBobblesIndicator
import bot.telegram.notificator.exchanges.clients.BotSettingsTrader
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type

/**
 * Converts json to java object and vice versa
 */
object Mapper {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(BotSettings::class.java, BotSettingsDeserializer())
        .create()

    @JvmStatic
    fun <T> asObject(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)

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