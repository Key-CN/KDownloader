package io.keyss.library.kdownloader.utils

import android.util.Log
import io.keyss.library.kdownloader.BuildConfig

/**
 * @author Key
 * Time: 2021/04/25 15:45
 * Description:
 */
object Debug {
    private var isCustomLogger = false
    var isDebug: Boolean? = null
        set(value) {
            if (!isCustomLogger && field != value) {
                resetLogger()
            }
            field = value
        }

    /*
     * 也可以自定义
     */
    var log: (message: String) -> Unit = initDefaultLogger()
        set(value) {
            field = value
            isCustomLogger = true
        }

    private fun resetLogger() {
        log = initDefaultLogger()
        // kotlin 直接赋值也会走set，只能再改回来
        isCustomLogger = false
    }

    private fun initDefaultLogger(): (message: String) -> Unit = if (isDebug ?: BuildConfig.DEBUG) {
        {
            val thread = Thread.currentThread()
            val stackTraceElement: StackTraceElement = thread.stackTrace[4]
            Log.i(
                "KDownloaderLog",
                "Thread: ${thread.name}  | Method: ${stackTraceElement.methodName}(${stackTraceElement.fileName}:${stackTraceElement.lineNumber}) | $it"
            )
        }
    } else {
        {
            // 不想每一条日志都需要判空，因为调试都日志本身就很频繁，我觉得那样很丑陋，所以决定采用空函数都方式
            // 如果把log类型变成可空，则实际调用判断次数更多
        }
    }
}