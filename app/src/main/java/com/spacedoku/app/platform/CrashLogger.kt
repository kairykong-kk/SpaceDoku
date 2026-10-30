package com.spacedoku.app.platform

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Captures any uncaught exception to a file (and Logcat) so crashes can be diagnosed after the fact.
 *
 * Files land in the app's external files dir, no runtime permission needed:
 *   /sdcard/Android/data/com.spacedoku.app/files/crash/crash-<timestamp>.log
 * Pull them with:  adb pull /sdcard/Android/data/com.spacedoku.app/files/crash
 * Or filter Logcat by tag "SpaceDokuCrash".
 */
object CrashLogger {
    private const val TAG = "SpaceDokuCrash"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable) // keep default behavior (show crash, exit)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val body = "time=$ts\nthread=${thread.name}\n\n$trace"

        Log.e(TAG, "Uncaught exception on '${thread.name}'", throwable)
        runCatching {
            val dir = File(context.getExternalFilesDir(null), "crash").apply { mkdirs() }
            File(dir, "crash-$ts.log").writeText(body)
            Log.e(TAG, "Saved crash log to ${dir.absolutePath}/crash-$ts.log")
        }
    }
}
