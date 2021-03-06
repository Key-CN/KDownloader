package io.keyss.kdownloader

import android.util.Log
import io.keyss.library.kdownloader.core.AbstractKDownloadTask
import io.keyss.library.kdownloader.core.IGroupDownloadListener
import io.keyss.library.kdownloader.core.TaskGroup

/**
 * @author Key
 * Time: 2021/04/27 17:09
 * Description:
 */
object GroupDownloadListenerImpl : IGroupDownloadListener {
    private const val TAG = "GroupDownloadListener"
    override suspend fun onGroupFinish(group: TaskGroup) {
        Log.i(TAG, "onGroupFinish: $group")
    }

    override suspend fun onGroupTerminate(group: TaskGroup) {
        Log.i(TAG, "onGroupTerminate: $group")
    }

    override suspend fun onGroupProgress(group: TaskGroup) {
        Log.i(TAG, "onGroupProgress: ${group.percentageProgress}%")
    }

    override fun onFail(task: AbstractKDownloadTask, e: Exception) {
        Log.d(TAG, "onFail() called with: task = $task, e = $e")
    }

    override fun onFinish(task: AbstractKDownloadTask) {
        Log.i(TAG, "onFinish: $task")
    }

    override fun onTerminate(task: AbstractKDownloadTask) {
        Log.i(TAG, "onTerminate: $task")
    }

    override fun onStart(task: AbstractKDownloadTask) {
        Log.i(TAG, "onStart: $task")
    }

    override fun onPause(task: AbstractKDownloadTask) {
        Log.i(TAG, "onPause: $task")
    }

    override fun onProgress(task: AbstractKDownloadTask) {
        Log.i(TAG, "onProgress: $task")
    }
}