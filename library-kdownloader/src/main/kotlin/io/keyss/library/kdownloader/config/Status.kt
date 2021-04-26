package io.keyss.library.kdownloader.config

import androidx.annotation.IntDef

/**
 * @author Key
 * Time: 2021/04/22 15:22
 * Description: 创建未启动，启动，已暂停，完成
 * 取消更像是一种action而非状态
 */
@Target(AnnotationTarget.TYPE)
@IntDef
annotation class Status() {
    companion object {
        const val CREATED = 0
        const val CONNECTING = 1
        const val RUNNING = 2
        const val PAUSED = 3
        const val FINISHED = 4
        const val CANCEL = 5
        const val FAILED = 6
    }
}
