package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/26 17:42
 * Description:
 */
interface IGroupDownloadListener : IDownloadListener{
    fun onGroupFinish(group: TaskGroup)

    fun onGroupTerminate(group: TaskGroup)

    fun onGroupProgress(group: TaskGroup)
}