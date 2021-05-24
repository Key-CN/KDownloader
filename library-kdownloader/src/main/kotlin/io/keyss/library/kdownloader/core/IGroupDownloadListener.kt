package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/26 17:42
 * Description:
 */
interface IGroupDownloadListener : IDownloadListener {
    /** 想让结束进程在同一条线程内执行，如果不想也可以手动执行新线程 */
    suspend fun onGroupFinish(group: TaskGroup)

    suspend fun onGroupTerminate(group: TaskGroup)

    suspend fun onGroupProgress(group: TaskGroup)
}