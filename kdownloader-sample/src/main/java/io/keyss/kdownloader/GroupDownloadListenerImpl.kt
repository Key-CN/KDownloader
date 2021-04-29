package io.keyss.kdownloader

import android.util.Log
import io.keyss.library.kdownloader.core.*

/**
 * @author Key
 * Time: 2021/04/27 17:09
 * Description:
 */
object GroupDownloadListenerImpl : IGroupDownloadListener {
    private const val TAG = "GroupDownloadListener"
    override fun onGroupFinish(group: TaskGroup) {
        Log.i(TAG, "onGroupFinish: $group")
    }

    override fun onGroupTerminate(group: TaskGroup) {
        Log.i(TAG, "onGroupTerminate: $group")
    }

    override fun onGroupProgress(group: TaskGroup) {
        Log.i(TAG, "onGroupProgress: ${group.percentageProgress}%")
    }

    override fun onFail(task: AbstractKDownloadTask, e: Exception) {
        Log.d(TAG, "onFail() called with: task = $task, e = $e")
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

    override fun onFinish(task: AbstractKDownloadTask) {
        Log.i(TAG, "onFinish: $task")
    }

    override fun onProgress(task: AbstractKDownloadTask) {
        Log.i(TAG, "onProgress: $task")
    }
}