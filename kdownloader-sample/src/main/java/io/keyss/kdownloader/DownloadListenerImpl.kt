package io.keyss.kdownloader

import io.keyss.library.kdownloader.core.AbstractKDownloadTask
import io.keyss.library.kdownloader.core.IDownloadListener

/**
 * @author Key
 * Time: 2021/04/27 17:09
 * Description:
 */
object DownloadListenerImpl : IDownloadListener {
    override fun onFail(task: AbstractKDownloadTask, e: Exception) {
    }

    override fun onTerminate(task: AbstractKDownloadTask) {
    }

    override fun onStart(task: AbstractKDownloadTask) {
    }

    override fun onPause(task: AbstractKDownloadTask) {

    }

    override fun onFinish(task: AbstractKDownloadTask) {
        when (task) {
            is GoodCourseDownloadTask -> {
                //task.onFinished()
            }
        }
    }

    override fun onProgress(task: AbstractKDownloadTask) {
        when (task) {
            is GoodCourseDownloadTask -> {
                //task.onProgress()
            }
        }
    }
}