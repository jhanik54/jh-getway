package com.jh.gateway

import android.content.Context
import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports the request log in a machine-readable form.
 *
 * The export is redacted in the same way as the diagnostics report: only the endpoint,
 * status, latency, provider name and model are written, never a key, a prompt or a
 * response body.
 */
class LogExporter(private val context: Context) {

    /** The log as CSV, one row per request. */
    fun toCsv(stats: GatewayStats): String {
        val builder = StringBuilder()
        builder.append("time,endpoint,status,latencyMs,provider,model\n")
        stats.recent().forEach { log ->
            builder.append(csvCell(formatTime(log.time))).append(',')
                .append(csvCell(log.endpoint)).append(',')
                .append(log.status).append(',')
                .append(log.latencyMs).append(',')
                .append(csvCell(log.provider.orEmpty())).append(',')
                .append(csvCell(log.model)).append('\n')
        }
        return builder.toString()
    }

    /** The log as JSON, matching the shape of the /v1/gateway/logs endpoint. */
    fun toJson(stats: GatewayStats): String {
        val builder = StringBuilder()
        builder.append("{\"object\":\"list\",\"data\":[")
        stats.recent().forEachIndexed { index, log ->
            if (index > 0) builder.append(',')
            builder.append("{\"time\":").append(log.time)
                .append(",\"endpoint\":\"").append(escape(log.endpoint)).append('"')
                .append(",\"status\":").append(log.status)
                .append(",\"latencyMs\":").append(log.latencyMs)
                .append(",\"provider\":\"").append(escape(log.provider.orEmpty())).append('"')
                .append(",\"model\":\"").append(escape(log.model)).append("\"}")
        }
        builder.append("]}")
        return builder.toString()
    }

    /** Writes the export into a document the user picked. */
    fun write(uri: Uri, stats: GatewayStats, asJson: Boolean): Boolean = runCatching {
        val payload = if (asJson) toJson(stats) else toCsv(stats)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(payload.toByteArray())
        } ?: error("Could not open the selected file")
        true
    }.getOrDefault(false)

    private fun formatTime(time: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))

    /** Quotes a CSV cell and escapes quotes and separators. */
    private fun csvCell(value: String): String {
        val cleaned = value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")
        return "\"$cleaned\""
    }

    private fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
            }
        }
    }
}
