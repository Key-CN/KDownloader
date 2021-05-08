package io.keyss.library.kdownloader.config

import androidx.annotation.IntDef

/**
 * @author Key
 * Time: 2021/04/22 15:22
 * Description: 创建未启动，连接中，启动，已暂停，完成
 * 取消更像是一种action而非状态
 * 挂起，类似于手动暂停，不会被视为暂停而被启动，挂起状态只能被手动再次启动
 * 结尾状态目前可归类为两种：正常（FINISHED）、异常（CANCEL、FAILED）
 */
@Target(AnnotationTarget.TYPE)
@IntDef
annotation class Status {
    companion object {
        const val CREATED = 0
        const val CONNECTING = 1
        const val RUNNING = 2
        const val PAUSED = 3
        const val FINISHED = 4
        const val CANCEL = 5
        const val FAILED = 6
        const val SUSPEND = 7

        fun getStatusText(status: Int): String {
            return when (status) {
                CREATED -> {
                    "已创建（未启动过）"
                }
                CONNECTING -> {
                    "连接中"
                }
                RUNNING -> {
                    "下载中"
                }
                PAUSED -> {
                    "已暂停"
                }
                FINISHED -> {
                    "已完成"
                }
                CANCEL -> {
                    "取消"
                }
                FAILED -> {
                    "失败（报错）"
                }
                SUSPEND -> {
                    "挂起"
                }
                else -> "未知"
            }
        }
    }
}
