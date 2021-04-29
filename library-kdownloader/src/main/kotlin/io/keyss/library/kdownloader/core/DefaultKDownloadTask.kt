package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/28 15:14
 * Description:
 */
class DefaultKDownloadTask(
    id: Int,
    url: String,
    localPath: String,
    name: String? = null,
    isDeleteExist: Boolean = true
) : AbstractKDownloadTask(id, url, localPath, name, isDeleteExist) {

    /**
     * 还要再优化
     */
    open class Builder : AbstractKDownloadTask.Builder<DefaultKDownloadTask>() {
        override fun build(): DefaultKDownloadTask {
            afterBuild()
            return DefaultKDownloadTask(id, url, localPath, name, isDeleteExist).apply {
                this.priority = this@Builder.priority
                this.maxConnections = this@Builder.maxConnections
            }
        }
    }
}