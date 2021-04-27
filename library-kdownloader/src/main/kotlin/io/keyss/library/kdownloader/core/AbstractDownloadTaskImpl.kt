package io.keyss.library.kdownloader.core

import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.utils.Debug
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * @author Key
 * Time: 2021/04/22 16:17
 * Description:
 */
abstract class AbstractDownloadTaskImpl(
    var id: Int,
    val url: String,
    val localPath: String,
    var name: String? = null,
    val isDeleteExist: Boolean = true,
    val relatedFiles: CopyOnWriteArrayList<File> = CopyOnWriteArrayList()
) : IDownloadTask {
    internal var status: @Status Int = Status.CREATED
    var fileLength: Long = 0

    // 剩余重试次数
    var retryTimes = 20

    // 任务的总下载量，用以统计进度，而非每条线程的下载量或所下载到的Index
    var totalDownloadedLength: Long = 0
    var percentageProgress: Int = 0
        private set

    /**
     * 优先级，可以动态修改
     * Int.MIN_VALUE - Int.MAX_VALUE
     */
    var priority: Int = 0

    /**
     * 任务最多启动多少条线程进行下载，todo 还需要对应每条线程的起始byte和结尾byte，已下载byte
     * todo 启动后再修改会复杂的多，暂时设定为启动后不可以再修改
     */
    var maxConnections = 1
        set(value) {
            field = if (value <= 0) {
                1
            } else if (value >= 20) {
                20
            } else {
                value
            }
        }
        get() {
            // 再根据长度来一个强制修改，太小的文件分块没有意义，目前设定一个块至少10MB(基本上网速10mb/s)
            return if (field == 1 || fileLength < AbstractKDownloader.MIN_BLOCK_LENGTH) {
                1
            } else {
                val blockNumber = (fileLength / AbstractKDownloader.MIN_BLOCK_LENGTH).toInt()
                return if (blockNumber < field) {
                    blockNumber
                } else {
                    field
                }
            }
        }

    fun updatePercentageProgress() {
        // 向下取整，防止提早出现100
        percentageProgress = (totalDownloadedLength * 1.0 / fileLength * 100).toInt()
    }

    /**
     * 暂停任务，给外部调用，决定关闭此功能，具体可以看ReadMe矛盾点第一点
     */
    @Deprecated("只是为了留下存在过的证明", ReplaceWith("cancel()"), DeprecationLevel.ERROR)
    fun pause() {
        status = Status.PAUSED
    }

    fun isPause(): Boolean {
        return compareStatus(Status.PAUSED)
    }

    /**
     * 取消任务，给外部调用
     */
    fun cancel() {
        val needDelete = !isStarting()
        status = Status.CANCEL
        if (needDelete) {
            GlobalScope.launch {
                deleteRelatedFiles()
            }
        }
    }

    fun deleteRelatedFiles() {
        relatedFiles.forEach {
            val delete = it.delete()
            val remove = relatedFiles.remove(it)
            Debug.log("${it.name} - 删除${if (delete) "成功" else "失败"}, 从列表移除${if (remove) "成功" else "失败"}")
        }
    }

    fun isFinished(): Boolean {
        return compareStatus(Status.FINISHED)
    }

    fun isCancel(): Boolean {
        return compareStatus(Status.CANCEL)
    }

    fun isStarting(): Boolean {
        return compareStatus(Status.RUNNING) || compareStatus(Status.CONNECTING)
    }

    fun isWaiting(): Boolean {
        return compareStatus(Status.CREATED) || compareStatus(Status.PAUSED)
    }

    fun isInQueue(): Boolean {
        return isWaiting() || isStarting()
    }

    /**
     * 生命周期结束包含：已完成、已取消
     */
    fun isLifecycleOver(): Boolean {
        return isCancel() || isFinished()
    }

    fun compareStatus(status: @Status Int): Boolean {
        return this.status == status
    }

    override fun equals(other: Any?): Boolean {
        return if (other is AbstractDownloadTaskImpl) {
            //id相同 或者  url，存储路径，存储名字，三者相同才视为同一对象
            id == other.id || (url == other.url && localPath == other.localPath && name == other.name)
        } else {
            false
        }
    }

    fun getStatusString(): String {
        return when (status) {
            Status.CREATED -> {
                "已创建（未启动过）"
            }
            Status.CONNECTING -> {
                "连接中"
            }
            Status.RUNNING -> {
                "下载中"
            }
            Status.PAUSED -> {
                "已暂停"
            }
            Status.FINISHED -> {
                "已完成"
            }
            Status.CANCEL -> {
                "取消"
            }
            Status.FAILED -> {
                "失败（报错）"
            }
            else -> "未知"
        }
    }
}