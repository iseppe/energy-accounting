package com.empathie.energyaccounting.data.diagnostics

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Small in-memory diagnostic log.
 *
 * Kept deliberately dependency-free so any layer (receivers, repository, export) can
 * record a line without threading a Context through. The buffer is bounded so a long
 * session cannot grow it without limit.
 */
object AppLogger {

    private const val MAX_ENTRIES = 500
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    private val entries = ArrayDeque<String>()
    private val lock = Any()

    fun info(tag: String, message: String) {
        log("INFO", tag, message, null)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        log("ERROR", tag, message, throwable)
    }

    fun dumpLines(): List<String> = synchronized(lock) { entries.toList() }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val line = buildString {
            append(LocalDateTime.now().format(formatter))
            append(" [").append(level).append("] ")
            append(tag).append(": ").append(message)
            throwable?.let { append(" | ").append(it::class.java.simpleName).append(": ").append(it.message) }
        }
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) entries.removeFirst()
            entries.addLast(line)
        }
    }
}
