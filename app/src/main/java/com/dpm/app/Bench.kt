package com.dpm.app

import android.content.Context
import android.os.Debug
import org.json.JSONObject
import java.io.File

object Bench {
    class Sample(
        val at: Long,
        val pssMb: Double,
        val heapMb: Double,
        val javaMb: Double,
        val nativeMb: Double,
        val graphicsMb: Double,
        val codeMb: Double,
        val otherMb: Double,
        val cpu: Double,
        val tickAvg: Double,
        val tickMax: Double,
        val mediaAvg: Double,
        val pushesPerMin: Double
    )

    private const val MAX = 150
    private const val WINDOW_MS = 60_000L
    private const val CLK_TCK = 100.0
    private val lock = Any()
    private val pushTimes = ArrayDeque<Long>()
    private val tickLog = ArrayDeque<Pair<Long, Double>>()
    private val mediaLog = ArrayDeque<Pair<Long, Double>>()

    private fun onFile(c: Context) = File(c.filesDir, "bench.on")
    private fun dataFile(c: Context, app: Boolean = false) =
        File(c.filesDir, if (app) "bench_app.jsonl" else "bench.jsonl")

    fun recording(c: Context): Boolean = onFile(c).exists()

    fun start(c: Context) {
        synchronized(lock) {
            pushTimes.clear()
            tickLog.clear()
            mediaLog.clear()
        }
        dataFile(c).delete()
        dataFile(c, app = true).delete()
        onFile(c).createNewFile()
    }

    fun stop(c: Context) {
        onFile(c).delete()
    }

    fun clear(c: Context) {
        dataFile(c).delete()
        dataFile(c, app = true).delete()
    }

    fun tick(ms: Double) {
        synchronized(lock) {
            tickLog.addLast(System.currentTimeMillis() to ms)
        }
    }

    fun media(ms: Double) {
        synchronized(lock) {
            mediaLog.addLast(System.currentTimeMillis() to ms)
        }
    }

    fun push() {
        synchronized(lock) {
            pushTimes.addLast(System.currentTimeMillis())
        }
    }

    private fun prune(now: Long) {
        while (pushTimes.isNotEmpty() && now - pushTimes.first() > WINDOW_MS) pushTimes.removeFirst()
        while (tickLog.isNotEmpty() && now - tickLog.first().first > WINDOW_MS) tickLog.removeFirst()
        while (mediaLog.isNotEmpty() && now - mediaLog.first().first > WINDOW_MS) mediaLog.removeFirst()
    }

    fun sample(c: Context, app: Boolean = false) {
        val now = System.currentTimeMillis()
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()
        val ticks = cpuTicks()
        val prev = readLast(c, app)
        val cpu = if (prev == null) {
            0.0
        } else {
            val wall = ((now - prev.getLong("t")) / 1000.0).coerceAtLeast(0.001)
            ((ticks - prev.getDouble("ticks")) / CLK_TCK / wall * 100.0).coerceAtLeast(0.0)
        }
        var tAvg = 0.0
        var tMax = 0.0
        var mAvg = 0.0
        var pRate = 0.0
        synchronized(lock) {
            prune(now)
            tAvg = if (tickLog.isEmpty()) 0.0 else tickLog.map { it.second }.average()
            tMax = tickLog.maxOfOrNull { it.second } ?: 0.0
            mAvg = if (mediaLog.isEmpty()) 0.0 else mediaLog.map { it.second }.average()
            pRate = pushTimes.size.toDouble()
        }
        val o = JSONObject()
            .put("t", now)
            .put("ticks", ticks)
            .put("pss", mi.totalPss / 1024.0)
            .put("heap", (rt.totalMemory() - rt.freeMemory()) / 1048576.0)
            .put("java", stat(mi, "summary.java-heap"))
            .put("native", stat(mi, "summary.native-heap"))
            .put("graphics", stat(mi, "summary.graphics"))
            .put("code", stat(mi, "summary.code"))
            .put("other", stat(mi, "summary.private-other") + stat(mi, "summary.stack") + stat(mi, "summary.system"))
            .put("cpu", cpu)
            .put("tickAvg", tAvg)
            .put("tickMax", tMax)
            .put("mediaAvg", mAvg)
            .put("push", pRate)
        val f = dataFile(c, app)
        f.appendText(o.toString() + "\n")
        if (f.length() > 80_000L) trim(f)
    }

    fun load(c: Context, app: Boolean = false): List<Sample> = try {
        val f = dataFile(c, app)
        if (!f.exists()) emptyList() else f.readLines().takeLast(MAX).mapNotNull { parse(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun parse(line: String): Sample? = try {
        val o = JSONObject(line)
        Sample(
            at = o.getLong("t"),
            pssMb = o.getDouble("pss"),
            heapMb = o.getDouble("heap"),
            javaMb = o.getDouble("java"),
            nativeMb = o.getDouble("native"),
            graphicsMb = o.getDouble("graphics"),
            codeMb = o.getDouble("code"),
            otherMb = o.getDouble("other"),
            cpu = o.getDouble("cpu"),
            tickAvg = o.getDouble("tickAvg"),
            tickMax = o.getDouble("tickMax"),
            mediaAvg = o.getDouble("mediaAvg"),
            pushesPerMin = o.getDouble("push")
        )
    } catch (e: Exception) {
        null
    }

    private fun readLast(c: Context, app: Boolean): JSONObject? = try {
        val f = dataFile(c, app)
        if (!f.exists()) null else f.useLines { it.lastOrNull { l -> l.isNotBlank() } }?.let { JSONObject(it) }
    } catch (e: Exception) {
        null
    }

    private fun trim(f: File) {
        val keep = f.readLines().takeLast(MAX)
        f.writeText(keep.joinToString("\n") + "\n")
    }

    private fun stat(mi: Debug.MemoryInfo, key: String): Double =
        (mi.getMemoryStat(key)?.toDoubleOrNull() ?: 0.0) / 1024.0

    private fun cpuTicks(): Double =
        try {
            val f = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
            f[11].toDouble() + f[12].toDouble()
        } catch (e: Exception) {
            0.0
        }
}
