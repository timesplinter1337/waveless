package dev.waveless.ui

import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return String.format(Locale.US, if (value < 10) "%.1f %s" else "%.0f %s", value, units[unit])
}

fun formatRate(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatDuration(millis: Long): String {
    val total = millis / 1000
    return String.format(Locale.US, "%02d:%02d:%02d", total / 3600, total % 3600 / 60, total % 60)
}
