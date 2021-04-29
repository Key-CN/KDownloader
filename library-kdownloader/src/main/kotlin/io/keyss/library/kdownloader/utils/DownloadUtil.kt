package io.keyss.library.kdownloader.utils

import io.keyss.library.kdownloader.DefaultKDownloader
import io.keyss.library.kdownloader.LifecycleKDownloader
import io.keyss.library.kdownloader.core.AbstractKDownloadTask
import io.keyss.library.kdownloader.core.AbstractKDownloader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

/**
 * @author Key
 * Time: 2021/04/26 15:40
 * Description: 全局使用的扩展函数，考试是先调用接口还是，先调用下载，大部分地方可能是现有的下载URL
 */

/**
 * 防止从未调用过任何一个KDownloader。
 */
internal var mDefaultKDownloader: AbstractKDownloader? = null
    get() {
        if (field == null) {
            field = DefaultKDownloader
        }
        return field
    }

/**
 * 执行下载，直接启动，非添加任务栈模式
 * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
 */
fun <T : AbstractKDownloadTask> CoroutineScope.download(
    task: T,
    context: CoroutineContext = Dispatchers.IO,
    onEvent: DownloadEvent<T>.() -> Unit,
) {
    launch(context) {
        // onEvent 执行是一个赋值过程
        LifecycleKDownloader.asyncDownloadTask(task, DownloadEvent(task).apply { onEvent() })
    }
}

/**
 * fixme 想不到好的去掉！！的方案
 */
suspend fun <T : AbstractKDownloadTask> T.sync(): T = apply {
    mDefaultKDownloader!!.syncDownloadTask(this)
}

fun <T : AbstractKDownloadTask> T.async(event: DownloadEvent<AbstractKDownloadTask>) {
    mDefaultKDownloader!!.asyncDownloadTask(this, event)
}

fun <T : AbstractKDownloadTask> T.inQueue(event: DownloadEvent<T>): Unit {
    mDefaultKDownloader!!.addTask(this)
}

fun <T : AbstractKDownloadTask> T.inQueueAndStart(event: DownloadEvent<T>): Unit {
    mDefaultKDownloader!!.addTaskAndStart(this)
}