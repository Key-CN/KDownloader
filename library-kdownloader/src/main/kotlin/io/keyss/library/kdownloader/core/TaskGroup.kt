package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/28 21:28
 * Description:
 */
open class TaskGroup(
    val groupId: Int,
    val tasks: Collection<AbstractKDownloadTask>,
) {
    var totalLength: Long = 0
        private set

    var totalDownloadedLength: Long = 0
        private set

    var percentageProgress: Int = 0
        private set

    fun addTotalLength(length: Long) {
        totalLength += length
    }

    fun addTotalDownloadedLength(length: Int) {
        totalDownloadedLength += length
    }

    fun updatePercentageProgress() {
        // 向下取整，防止提早出现100
        percentageProgress = (totalDownloadedLength * 1.0 / totalLength * 100).toInt()
    }
}
