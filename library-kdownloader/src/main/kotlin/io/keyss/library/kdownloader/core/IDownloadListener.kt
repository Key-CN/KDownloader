package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/26 17:42
 * Description:
 */
interface IDownloadListener<T : AbstractDownloadTaskImpl> {
    fun onFail(task: T, e: Exception)

    fun onTerminate(task: T)

    fun onStart(task: T)

    fun onPause(task: T)

    fun onFinish(task: T)

    fun onProgress(task: T)
}