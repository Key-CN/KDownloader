package io.keyss.library.kdownloader.bean

import io.keyss.library.kdownloader.AbstractKDownloader
import io.keyss.library.kdownloader.config.Status

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
    var status: @Status Int = Status.CREATED
    var fileLength: Long = 0
    var downloadedLength: Long = 0
    var percentageProgress: Int = 0
        private set

    // 优先级，可以动态修改
    var priority: Int = Int.MAX_VALUE

    /**
     * 任务最多启动多少条线程进行下载
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
        percentageProgress = (downloadedLength * 1.0 / fileLength * 100).toInt()
    }
}