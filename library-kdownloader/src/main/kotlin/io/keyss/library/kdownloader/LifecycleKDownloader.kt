package io.keyss.library.kdownloader

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import io.keyss.library.kdownloader.config.PersistenceType

/**
 * @author Key
 * Time: 2021/04/22 20:15
 * Description: sample，生命周期型
 * 也可以使用[ProcessLifecycleOwner]，需要添加[implementation "androidx.lifecycle:lifecycle-process:$lifecycle_version"]
 */
object LifecycleKDownloader : AbstractKDownloader() {
    private val lifecycleObserver: LifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun onStart() {
            startTaskQueue()
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onStop() {
            stopTaskQueue()
        }
    }

    var lifecycleOwner: LifecycleOwner? = null
        set(value) {
            if (value == null) {
                if (field != null) {
                    field!!.lifecycle.removeObserver(lifecycleObserver)
                }
            } else {
                value.lifecycle.addObserver(lifecycleObserver)
            }
            field = value
        }
}