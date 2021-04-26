package io.keyss.library.kdownloader.utils

import io.keyss.library.kdownloader.LifecycleKDownloader
import io.keyss.library.kdownloader.core.AbstractDownloadTaskImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * @author Key
 * Time: 2021/04/26 15:40
 * Description: 全局使用的扩展函数，考试是先调用接口还是，先调用下载，大部分地方可能是现有的下载URL
 */

/**
 * 执行下载，直接启动，非添加任务栈模式
 * @param context additional to [CoroutineScope.coroutineContext] context of the coroutine.
 */
fun <T : AbstractDownloadTaskImpl> CoroutineScope.download(
    task: T,
    context: CoroutineContext = Dispatchers.IO,
    onEvent: DownloadEvent<T>.() -> Unit,
) {
    // SAM 转换:函数式接口就是只定义一个抽象方法的接口
    launch(context) {
        // onEvent 执行是一个赋值过程
        LifecycleKDownloader.asyncDownloadTask(task, DownloadEvent(task).apply { onEvent() })
    }
}