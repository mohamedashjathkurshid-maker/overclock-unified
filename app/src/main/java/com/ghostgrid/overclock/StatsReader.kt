
package com.ghostgrid.overclock

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import java.io.File

data class SysStats(
    val cpuPct: Int, val ramPct: Int, val netKbps: Long,
    val tempC: Int, val fps: Int, val zramPct: Int
)

object StatsReader {

    private var lastIdle = 0L
    private var lastTotal = 0L
    private var lastRx = 0L
    private var lastTx = 0L
    private var lastNetTime = 0L

    fun read(context: Context): SysStats {
        return SysStats(
            cpuPct = readCpu(),
            ramPct = readRam(context),
            netKbps = readNet(),
            tempC = readTemp(),
            fps = 60, // real FPS needs SurfaceFlinger dumpsys; placeholder
            zramPct = readZram()
        )
    }

    private fun readCpu(): Int {
        return try {
            val line = File("/proc/stat").readLines().first { it.startsWith("cpu ") }
            val parts = line.trim().split("\\s+".toRegex()).map { it.toLong() }
            val idle = parts[4] + parts[5]
            val total = parts.subList(1, parts.size).sum()
            val dIdle = idle - lastIdle
            val dTotal = total - lastTotal
            lastIdle = idle; lastTotal = total
            if (dTotal <= 0) 0 else ((dTotal - dIdle) * 100 / dTotal).toInt().coerceIn(0, 100)
        } catch (e: Exception) { 0 }
    }

    private fun readRam(context: Context): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() else 0
    }

    private fun readNet(): Long {
        return try {
            val now = System.currentTimeMillis()
            val rx = TrafficStats.getTotalRxBytes()
            val tx = TrafficStats.getTotalTxBytes()
            val dt = now - lastNetTime
            val rate = if (dt > 0 && lastRx > 0) ((rx + tx - lastRx - lastTx) * 1000 / dt / 1024) else 0
            lastRx = rx; lastTx = tx; lastNetTime = now
            rate.coerceAtLeast(0)
        } catch (e: Exception) { 0 }
    }

    private fun readTemp(): Int {
        return try {
            val zones = File("/sys/class/thermal").listFiles { f -> f.name.startsWith("thermal_zone") } ?: emptyArray()
            zones.mapNotNull {
                runCatching { File(it, "temp").readText().trim().toInt() / 1000 }.getOrNull()
            }.maxOrNull() ?: 0
        } catch (e: Exception) { 0 }
    }

    private fun readZram(): Int {
        return try {
            val mem = File("/proc/meminfo").readText()
            val total = mem.lineSequence().firstOrNull { it.startsWith("MemTotal") }
                ?.filter { it.isDigit() }?.toLongOrNull() ?: 1
            val zram = mem.lineSequence().firstOrNull { it.startsWith("SwapTotal") }
                ?.filter { it.isDigit() }?.toLongOrNull() ?: 0
            if (total > 0) (zram * 100 / (total + zram)).toInt() else 0
        } catch (e: Exception) { 0 }
    }
}
