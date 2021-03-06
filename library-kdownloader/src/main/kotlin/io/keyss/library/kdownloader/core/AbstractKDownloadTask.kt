package io.keyss.library.kdownloader.core

import android.os.Environment
import android.util.Log
import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.utils.Debug
import io.keyss.library.kdownloader.utils.MD5Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * @author Key
 * Time: 2021/04/22 16:17
 * Description:
 */
abstract class AbstractKDownloadTask(
    /**
     * 用户定义的ID
     */
    var id: Int,
    val url: String,
    val localPath: String,
    var name: String? = null,
    var isDeleteExist: Boolean = true,
    /** 方便debug识别任务组，或用于UI展示 */
    var markName: String = ""
) {
    /**
     * 数据库ID
     */
    var _id: Int? = null

    /** 任务状态 */
    @Volatile
    internal var status: @Status Int = Status.CREATED

    // 正式文件，实际也就是File(localPath, name!!)
    var file: File? = null
    var tempFile: File? = null
    var configFile: File? = null

    /** 文件长度（不一定精确），由代码获取验证 */
    var fileLength: Long? = null

    /** 是否支持断点续传，由代码自动验证 */
    var isSupportBreakpoint = false

    /** 资源的token */
    var etag: String? = null

    /** 重试次数 */
    var retryTimes: Int = 0

    /** 剩余自动重试的次数 */
    private var remainingAutoRetryTimes = 3

    /** 任务的总下载量，用以统计进度，而非每条线程的下载量或所下载到的Index */
    var downloadedLength: Long = 0

    /** 百分比进度 */
    var percentageProgress: Int = 0
        private set

    /**
     * 优先级，可以动态修改
     * Int.MIN_VALUE - Int.MAX_VALUE
     */
    var priority: Int = 0

    /**
     * 互相持有，可以从一个任务找到整组任务
     */
    var group: TaskGroup? = null

    /**
     * 独立的进度监听，我想在同步下载时也可以监听到进度，而不用去注册全局监听，注册了也还要判断是哪个任务，也不用额外去开一条协程轮训监听
     */
    private var mOnProgressListener: OnProgressListener? = null

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
            return if (fileLength == null || field == 1 || fileLength!! < AbstractKDownloader.MIN_BLOCK_LENGTH) {
                1
            } else {
                // fileLength == null 已最优先判断过，所以此次一定不为null
                val blockNumber = (fileLength!! / AbstractKDownloader.MIN_BLOCK_LENGTH).toInt()
                return if (blockNumber < field) {
                    blockNumber
                } else {
                    field
                }
            }
        }

    /** 更新百分比进度 */
    fun updatePercentageProgress() {
        // 向下取整，防止提早出现100
        if (fileLength != null) {
            percentageProgress = (downloadedLength * 1.0 / fileLength!! * 100).toInt()
        }
    }

    /**
     * @param task 任务，方便匿名任务使用
     * @param onProgressListener
     */
    /*
    fun <T : AbstractKDownloadTask> setOnProgressListener(onProgressListener: ((task: T, percentageProgress: Int) -> Unit)?) {
        this.onProgressListener = onProgressListener
    }

    fun callOnProgress() {
        onProgressListener?.invoke(percentageProgress)
    }*/

    fun setOnProgressListener(onProgressListener: OnProgressListener?) {
        this.mOnProgressListener = onProgressListener
    }

    /**
     * kotlin的包内使用
     */
    internal fun callOnProgress() {
        mOnProgressListener?.let {
            GlobalScope.launch(Dispatchers.IO) {
                it.onProgress(percentageProgress)
            }
        }
    }

    /**
     * 重新下载任务需要重置一些数据
     */
    fun reset() {
        status = Status.CREATED
        downloadedLength = 0
        remainingAutoRetryTimes = 3
        isDeleteExist = true
    }

    fun retry() {
        status = Status.PAUSED
        remainingAutoRetryTimes = 3
    }

    /**
     * 如果失败了。可以自动重试
     */
    private fun autoRetry(): Boolean {
        val canRetry = isFailed() && (remainingAutoRetryTimes > 0 || retryTimes > 0)
        Debug.log("自动重试, canRetry=${canRetry}, 自动重试剩余次数=$remainingAutoRetryTimes, 用户设定自动重试=${retryTimes}")
        if (canRetry) {
            if (retryTimes > 0) {
                retryTimes--
            } else {
                remainingAutoRetryTimes--
            }
            status = Status.PAUSED
        }
        return canRetry
    }

    /**
     * 暂停任务，给外部调用，决定关闭此功能，具体可以看ReadMe矛盾点第一点
     * 2021/4/28 单任务执行方式其实可以暂停，不冲突，但是都只能靠提前引用task，才能在需要的地方进行暂停
     */
    @Deprecated("只是为了留下存在过的证明", ReplaceWith("cancel()"), DeprecationLevel.ERROR)
    fun pause() {
        status = Status.PAUSED
    }

    /**
     * 挂起，不会被自动启动
     */
    fun suspend() {
        status = Status.SUSPEND
    }

    fun isSuspend(): Boolean {
        return compareStatus(Status.SUSPEND)
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
        // 运行中到任务交由任务终止的动作来执行
        if (needDelete) {
            GlobalScope.launch {
                deleteRelatedFiles()
            }
        }
    }

    /**
     * 仅删除临时文件
     */
    fun deleteTempFiles(): Unit {
        val delete2 = tempFile?.delete()
        Debug.log("$name - 临时文件: 删除${if (delete2 == true) "成功" else "失败"}")

    }

    /**
     * 删除相关的所有文件
     */
    fun deleteRelatedFiles() {
        val delete1 = file?.delete()
        val delete2 = tempFile?.delete()
        val delete3 = configFile?.delete()
        Debug.log("${localPath}/${name} - 正式文件: 删除${if (delete1 == true) "成功" else "失败"}, 临时文件: 删除${if (delete2 == true) "成功" else "失败"}, 配置文件: 删除${if (delete3 == true) "成功" else "失败"}")
    }

    fun deleteTemporaryFile(): Boolean {
        return tempFile.let {
            // 存在且删除失败
            !(it != null && it.exists() && !it.delete())
        }.also {
            if (it) {
                tempFile = null
            }
        }
    }

    fun deleteConfigFile(): Boolean {
        return configFile.let {
            // 存在且删除失败 为失败，其他都是成功
            !(it != null && it.exists() && !it.delete())
        }.also {
            if (it) {
                configFile = null
            }
        }
    }

    /**
     * 完成、失败、取消，这三种状态为结束。
     */
    fun isTerminated(): Boolean {
        return compareStatus(Status.FINISHED) || compareStatus(Status.FAILED) || compareStatus(Status.CANCEL)
    }

    fun isFailed(): Boolean {
        return compareStatus(Status.FAILED)
    }

    /**
     * 任务报错了，失败了
     * @return 是否失败了
     */
    fun failed(e: Exception): Boolean {
        // 为了好理解，先失败，然后重试，不能重试->失败，能重试->暂停(没有独立出重试的状态)
        status = Status.FAILED
        val isRetry = autoRetry()
        // 不能自动重试则为失败
        if (!isRetry) {
            status = Status.FAILED
        }
        Log.w("task:${name}, failed()下载失败, 重试=${isRetry}", e)
        return !isRetry
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
     * 生命周期结束包含：已完成、已取消。也就是任务将不再被执行
     */
    fun isLifecycleOver(): Boolean {
        return isCancel() || isFinished()
    }

    fun compareStatus(status: @Status Int): Boolean {
        return this.status == status
    }

    override fun equals(other: Any?): Boolean {
        return if (other is AbstractKDownloadTask) {
            //etag相同 或者 url，存储路径，存储名字，三者相同才视为同一对象
            (etag != null && other.etag != null && etag == other.etag) || (url == other.url && localPath == other.localPath && name == other.name)
        } else {
            false
        }
    }

    /**
     * 获取状态的中文名
     */
    fun getStatusText(): String = Status.getStatusText(status)

    override fun toString(): String {
        return "${this::class.simpleName} (dataID=${_id}, id=$id, url='$url', localPath='$localPath', name=$name, isDeleteExist=$isDeleteExist, markName='$markName', fileLength=$fileLength, isSupportBreakpoint=$isSupportBreakpoint, priority=$priority, groupId=${group?.groupId}, maxConnections=$maxConnections)"
    }

    /**
     * 方便在日志中识别的名字
     */
    fun getLogName(): String {
        return "${this::class.simpleName}: ${markName.takeIf { it.isNotBlank() } ?: name}(dataID=${_id})"
    }

    interface OnProgressListener {
        fun onProgress(percentageProgress: Int)
    }

    abstract class Builder<T : AbstractKDownloadTask> {
        protected var id: Int = 0
        protected var retryTimes: Int = 0
        protected lateinit var url: String
        protected var localPath: String = ""
        protected var markName: String = ""
        protected var name: String? = null
        protected var isDeleteExist: Boolean = true
        protected var priority: Int = 0
        protected var maxConnections = 1

        fun url(url: String) = apply {
            this.url = url
        }

        fun retryTimes(retryTimes: Int) = apply {
            this.retryTimes = retryTimes
        }

        fun id(id: Int) = apply {
            this.id = id
        }

        fun localPath(localPath: String) = apply {
            this.localPath = localPath
        }

        fun markName(markName: String) = apply {
            this.markName = markName
        }

        fun name(name: String) = apply {
            this.name = name
        }

        fun isDeleteExist(isDeleteExist: Boolean) = apply {
            this.isDeleteExist = isDeleteExist
        }

        fun priority(priority: Int) = apply {
            this.priority = priority
        }

        fun maxConnections(maxConnections: Int) = apply {
            this.maxConnections = maxConnections
        }

        @Throws(Exception::class)
        fun afterBuild() {
            // 顺带优先校验url有没有赋值
            val urlHashCode = url.hashCode()
            if (id == 0) {
                id = urlHashCode
            }
            if (localPath.isBlank()) {
                localPath = Environment.getDownloadCacheDirectory().absolutePath
            }
            if (name.isNullOrBlank()) {
                name = url.takeLastWhile { it != '/' }
            }
            if (name.isNullOrBlank()) {
                // 要保证每个相同的URL转出来的是一样的，所以不能使用random的做法
                //task.name = UUID.randomUUID().toString()
                name = MD5Util.stringToMD5(url)
            }
        }

        /**
         * 可以调用afterBuild()也可以使用自己的逻辑
         */
        @Throws(Exception::class)
        abstract fun build(): T
    }
}