package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/26 17:42
 * Description: 之前设计有误，去掉范型，因为是单管理器，多任务类型，所以势必是不需要范型，而是需要类型判断的，因为可能接收到任何类型的任务
 */
interface IDownloadListener {
    fun onFail(task: AbstractKDownloadTask, e: Exception)

    fun onTerminate(task: AbstractKDownloadTask)

    fun onStart(task: AbstractKDownloadTask)

    fun onPause(task: AbstractKDownloadTask)

    fun onFinish(task: AbstractKDownloadTask)

    fun onProgress(task: AbstractKDownloadTask)
}