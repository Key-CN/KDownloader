package io.keyss.library.kdownloader.utils

import io.keyss.library.kdownloader.core.AbstractKDownloadTask

/**
 * @author Key
 * Time: 2021/04/28 20:44
 * Description:
 */
class GroupDownloadEvent<T : AbstractKDownloadTask>(task: T) : DownloadEvent<T>(task) {
    var groupFinish: (() -> Unit)? = null
        private set

    var groupTerminate: (() -> Unit)? = null
        private set

    var groupProgress: (() -> Unit)? = null
        private set

    fun onGroupFinish(block: () -> Unit) {
        groupFinish = block
    }

    fun onGroupTerminate(block: () -> Unit) {
        groupTerminate = block
    }

    fun onGroupProgress(block: () -> Unit) {
        groupProgress = block
    }
}