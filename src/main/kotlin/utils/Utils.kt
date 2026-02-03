package utils

import java.io.File

inline fun <reified T> resourceFile(name: String): File = File(T::class.java.getResource(name)!!.file)