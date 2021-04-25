package io.keyss.library.kdownloader.core

import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.utils.Debug

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
) : IDownloadTask {
    internal var status: @Status Int = Status.CREATED
    var fileLength: Long = 0

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


    //test field
    private var lastUpdateTime = 0L
    fun updatePercentageProgress() {
        // 向下取整，防止提早出现100
        percentageProgress = (totalDownloadedLength * 1.0 / fileLength * 100).toInt()
        if (System.currentTimeMillis() - lastUpdateTime > 3_000) {
            lastUpdateTime = System.currentTimeMillis()
            Debug.log("$name - $percentageProgress%")
        }
    }

    /**
     * 暂停任务，给外部调用
     */
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
        status = Status.CANCEL
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

    fun compareStatus(status: @Status Int): Boolean {
        return this.status == status
    }

    override fun equals(other: Any?): Boolean {
        return if (other is AbstractDownloadTaskImpl) {
            url == other.url
        } else {
            false
        }
    }
}