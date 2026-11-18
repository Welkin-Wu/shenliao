package com.shenliao.deepchat

import android.app.Application
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 兜底：把未捕获的异常写到本地，下次启动就能看到「上次崩溃」并一键清数据，
 * 不至于因为一条坏数据把 App 卡在启动即崩的死循环里。
 */
class DeepChatApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { saveCrash(throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrash(t: Throwable) {
        val text = buildString {
            appendLine("时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            appendLine("版本: " + packageManager.getPackageInfo(packageName, 0).versionName)
            appendLine()
            appendLine(android.util.Log.getStackTraceString(t))
        }

        // 私有目录（下次启动自己读）
        runCatching {
            File(filesDir, "crash").apply { mkdirs() }
            File(File(filesDir, "crash"), "last.txt").writeText(text)
        }
        // 外部目录，方便用文件管理器取出来发给别人
        runCatching {
            val ext = getExternalFilesDir("crash") ?: return@runCatching
            ext.mkdirs()
            File(ext, "last.txt").writeText(text)
            File(ext, "crash-" + System.currentTimeMillis() + ".txt").writeText(text)
        }
    }
}
