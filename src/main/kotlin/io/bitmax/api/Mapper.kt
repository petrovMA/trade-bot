package io.bitmax.api

import com.google.gson.Gson
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.reflect.Type

/**
 * Converts json to java object and vice versa
 */
object Mapper {
    private val gson = Gson()

    @JvmStatic
    fun <T> asObject(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)

    @JvmStatic
    fun <T> asListObjects(json: String, type: Type): List<T> = gson.fromJson(json, type)

    @JvmStatic
    fun <T> asObject(file: File, clazz: Class<T>): T = gson.fromJson(FileReader(file), clazz)

    @JvmStatic
    fun <T> asListObjects(file: File, type: Type): List<T> = gson.fromJson(FileReader(file), type)

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
}