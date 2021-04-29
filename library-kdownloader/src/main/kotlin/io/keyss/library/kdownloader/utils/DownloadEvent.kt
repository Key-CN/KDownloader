package io.keyss.library.kdownloader.utils

import io.keyss.library.kdownloader.core.AbstractKDownloadTask

/**
 * @author Key
 * Time: 2020/09/15 17:30
 * Description: 每个一个数据都来自于task，只是为了方便使用拿出第二个参数
 */
open class DownloadEvent<T : AbstractKDownloadTask>(val task: T) {
    var progress: (() -> Unit)? = null
        private set

    var fail: ((e: Exception) -> Unit)? = null
        private set

    var pause: (() -> Unit)? = null
        private set

    var finish: (() -> Unit)? = null
        private set

    /**
     * 无论成功或失败都会走的结束点
     */
    var terminate: (() -> Unit)? = null
        private set

    var start: (() -> Unit)? = null
        private set

    fun onFail(block: (e: Exception) -> Unit) {
        fail = block
    }

    fun onTerminate(block: () -> Unit) {
        terminate = block
    }

    fun onStart(block: () -> Unit) {
        start = block
    }

    fun onPause(block: () -> Unit) {
        pause = block
    }

    fun onFinish(block: () -> Unit) {
        finish = block
    }

    fun onProgress(block: () -> Unit) {
        progress = block
    }
}