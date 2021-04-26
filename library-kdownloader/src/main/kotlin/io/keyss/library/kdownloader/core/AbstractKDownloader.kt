package io.keyss.library.kdownloader.core

import android.os.Looper
import android.os.StatFs
import io.keyss.library.kdownloader.config.KDownloadException
import io.keyss.library.kdownloader.config.PersistenceType
import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.config.StorageInsufficientException
import io.keyss.library.kdownloader.utils.Debug
import io.keyss.library.kdownloader.utils.DownloadEvent
import io.keyss.library.kdownloader.utils.MD5Util
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okhttp3.internal.headersContentLength
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Key
 * Time: 2021/04/21 22:38
 * Description: 不在类上写范型是为了方便使用，在一些需要自定义类型，但是只是简单下载的场景。同时可以使用多种任务类型（Task的实现）来区分业务。
 */
abstract class AbstractKDownloader(taskPersistenceType: @PersistenceType Int = PersistenceType.MEMORY_CACHE) {
    companion object {
        private const val TAG = "KDownloader"
        private const val TEMP_FILE_SUFFIX = ".kd"
        private const val CONFIG_SUFFIX = ".cfg"
        const val MIN_BLOCK_LENGTH = 1024 * 1024 * 1024

        // 8K显然已经不能满足现在的设备和网速，重新定义一个
        const val DEFAULT_BUFFER_SIZE = 32 * 1024
    }

    private val mTasks: CopyOnWriteArrayList<AbstractDownloadTaskImpl> = CopyOnWriteArrayList()
    var downloadListener: IDownloadListener<AbstractDownloadTaskImpl>? = null
    private var mTaskPersistenceType: @PersistenceType Int = taskPersistenceType
    private var isPause = false

    /**
     * 最小进度事件输出时间，默认5秒，想要每次都输出只要设小就行了
     */
    var minProgressEventInterval = 5_000

    /**
     * 而我更想要百分比输出都方式，我再加一个，而且设成了默认
     */
    var isPercentageProgressEvent = true

    // 内存的话每次启动都会从0开始，数据库的话需要读取一次ID
    private var mIdCounter: AtomicInteger = when (mTaskPersistenceType) {
        PersistenceType.MEMORY_CACHE -> {
            AtomicInteger(0)
        }
        PersistenceType.DATABASE -> {
            // 从数据库读取ID值
            AtomicInteger()
        }
        else -> {
            AtomicInteger(0)
        }
    }

    fun generateId(): Int {
        AtomicInteger()
        return mIdCounter.getAndIncrement()
    }

    /**
     * 理论上使用okhttp优于使用HttpURLConnection，且几乎所有项目都使用okhttp
     * 如后续使用中没有什么不可解决的因素不再换回HttpURLConnection
     *
     */
    private var mClient = OkHttpClient
        .Builder()
        // 下载文件可能很大，不实用超时，只管理进度超时，其他超时都采用默认都10秒
        //.readTimeout(0, TimeUnit.NANOSECONDS)
        .build()


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

    /**
     * 自定义OkHttpClient
     */
    fun setCustomOkHttpClient(client: OkHttpClient) {
        this.mClient = client
    }

    fun startTaskQueue() {
        isPause = false
        // 启动队列中的任务
        val runningTasks = getRunningTasks()
        if (runningTasks.size >= maxConnections) {
            return
        }
        val remainingSize = maxConnections - runningTasks.size
        // fixme 这有一个问题，队列中优先级最高的任务，被手动暂停后，再次开启的还是这个任务？所以这个优先级的原则应该是要不要排除手动暂停的方式呢？这样的话难道优先级最高的反而要去队尾？这不科学
        // 所以我决定去掉单任务暂停，只留下取消
        val waitingTasks = getWaitingTasks()
        Debug.log("还有${remainingSize}个可下载位置，以及${waitingTasks.size}个等待下载的任务")
        repeat(remainingSize) { index ->
            waitingTasks.getOrNull(index)?.let { task ->
                // 基类没有绑定生命周期，设定也是全局可用
                GlobalScope.launch {
                    try {
                        downloadWrap(task)
                    } catch (e: Exception) {
                        //e.printStackTrace()
                    }
                }
            }
        }
    }

    fun stopTaskQueue(): Unit {
        isPause = true
    }

    /**
     * 添加一个下载任务，不启动
     */
    fun <T : AbstractDownloadTaskImpl> addTask(task: T): Boolean {
        val addIfAbsent = mTasks.addIfAbsent(task)
        // CopyOnWriteArrayList 不能排序，取的时候排
        /*mTasks.sortBy {
            it.priority
        }*/
        Debug.log("仅添加任务，添加=$addIfAbsent")
        return addIfAbsent
    }

    /**
     * 添加一个下载任务，并启动
     */
    fun <T : AbstractDownloadTaskImpl> addTaskAndStart(task: T): Unit {
        val addIfAbsent = addTask(task)
        Debug.log("添加任务并启动，添加=$addIfAbsent")
        startTaskQueue()
    }

    /**
     * 其实没有用到suspend，只是为了防止在主线程调用，同步的方案是设计给在协程中等待返回的
     */
    @Throws(Exception::class)
    suspend fun <T : AbstractDownloadTaskImpl> syncDownloadTask(task: T) {
        downloadWrap(task)
    }

    /**
     * 异步单任务，直接启动型，跳过队列
     */
    @Throws(Exception::class)
    fun <T : AbstractDownloadTaskImpl> asyncDownloadTask(task: T, event: DownloadEvent<T>) {
        downloadWrap(task, event)
    }

    fun getRunningTasks(): List<AbstractDownloadTaskImpl> {
        return mTasks.filter {
            it.isStarting()
        }
    }

    fun getWaitingTasks(): List<AbstractDownloadTaskImpl> {
        return mTasks.filter {
            it.isWaiting()
        }.sortedByDescending { it.priority }
    }

    @Throws(Exception::class)
    private fun <T : AbstractDownloadTaskImpl> downloadWrap(task: T, event: DownloadEvent<T>? = null) {
        try {
            downloadCore(task, event)
        } catch (e: Exception) {
            e.printStackTrace()
            task.status = Status.FAILED
            downloadListener?.onFail(task, e)
            if (event?.fail != null) {
                event.fail!!.invoke(e)
            } else {
                // 如果不采用回调方式则继续往外抛，总之要丢给业务层去处理
                // 关于重试，自动重试还是业务层去重试，非网络原因的可以直接重试，网络原因的可以抛给业务层
                // 网络没连接UnknownHostException，超时SocketTimeout
                throw e
            }
        } finally {
            onTerminateEvent(task, event)
            if (!isPause) {
                // 继续下载下一个
                startTaskQueue()
            }
        }
    }

    /**
     * 下载核心逻辑
     * 请在子线程执行
     */
    @Throws(Exception::class)
    private fun <T : AbstractDownloadTaskImpl> downloadCore(task: T, event: DownloadEvent<T>? = null) {
        onStartEvent(task, event)
        // todo 搜索任务栈中是否存在

        if (Looper.getMainLooper() == Looper.myLooper()) {
            // todo 到时候如果改成纯Java库就注释掉这部分
            throw KDownloadException("请在子线程下载")
        }
        // 校验URL的正确性
        if (!task.url.startsWith("http")) {
            // 报错，或者直接加个http
            throw KDownloadException("下载地址不正确, URL=[${task.url}]")
        }
        // 验证路径的可用性
        val storageFolder = File(task.localPath)
        if (storageFolder.exists()) {
            if (!storageFolder.isDirectory) {
                throw KDownloadException("存储路径非文件夹，已存在一个同名文件")
            }
        } else {
            if (!storageFolder.mkdirs()) {
                throw KDownloadException("创建存储文件夹失败：${storageFolder.absolutePath}")
            }
        }
        if (!storageFolder.canWrite()) {
            throw KDownloadException("父文件夹没有写入权限")
        }

        val headRequest = Request
            .Builder()
            .url(task.url)
            .head()
            .build()
        val responseByHead = mClient.newCall(headRequest).execute()
        // 验证地址的连接
        if (!responseByHead.isSuccessful) {
            throw KDownloadException("HEAD请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        // 正确之后写入长度
        task.fileLength = responseByHead.headersContentLength()
        if (task.fileLength <= 0) {
            throw KDownloadException("文件长度不正确, ContentLength=${task.fileLength}")
        }
        // 验证硬盘是否够写入
        isStorageEnough(storageFolder, task.fileLength)

        // 验证文件名
        if (task.name.isNullOrBlank()) {
            task.name = task.url.takeLastWhile { it != '/' }
        }
        if (task.name.isNullOrBlank()) {
            // 要保证每个相同的URL转出来的是一样的，所以不能使用random的做法
            //task.name = UUID.randomUUID().toString()
            task.name = MD5Util.stringToMD5(task.url)
        }
        Debug.log("${task.name} - length=${task.fileLength}")
        val file = File(storageFolder, task.name!!)
        // 先检测本地文件
        if (file.exists()) {
            if (task.isDeleteExist) {
                val sameNameDelete = file.delete()
                if (!sameNameDelete) {
                    throw KDownloadException("删除同名文件失败")
                }
                Debug.log("${task.name} - 存在同名文件，删除=${sameNameDelete}")
            } else {
                throw KDownloadException("下载存在同名文件，且未设定覆盖")
            }
        }
        // 开始下载
        // 同时还要检测是否是任务，任务的话断点续传，未完成的任务前面加点隐藏。完成后重命名，如果这个过程中名字被占用怎么随机重命名
        val tempFile = File(storageFolder, task.name!! + TEMP_FILE_SUFFIX)
        // 直接固定起长度
        if (tempFile.exists()) {
            if (tempFile.length() != task.fileLength) {
                if (!tempFile.delete()) {
                    throw KDownloadException("删除临时文件失败")
                }
            }
            // 相同则需要判断seek，
        }
        // MappedByteBuffer 也有不足，就是在数据量很小的时候，表现比较糟糕，那是因为 direct buffer 的初始化时间较长，所以建议大家只有在数据量较大的时候，在用 MappedByteBuffer。
        // 且存在内存占用和文件关闭等不确定问题。被 MappedByteBuffer 打开的文件只有在垃圾收集时才会被关闭，而这个点是不确定的。javadoc 里是这么说的：
        // A mapped byte buffer and the file mapping that it represents remain valid until the buffer itself is garbage-collected. ——JavaDoc
        val writeTempFile = RandomAccessFile(tempFile, "rw")
        writeTempFile.setLength(task.fileLength)
        // 需要校验写了多少了
        /*if (task.maxConnections == 1) {
            val getRequest = headRequest.newBuilder().get().build()
        }*/
        // 一条线程下载
        val getRequest = headRequest
            .newBuilder()
            .get()
            .also {
                if (task.totalDownloadedLength != 0L) {
                    it.header("Range", "bytes=${task.totalDownloadedLength}-${task.fileLength}")
                    // seek length 不需要+1
                    writeTempFile.seek(task.totalDownloadedLength)
                }
            }
            .build()
        val responseByGet = mClient.newCall(getRequest).execute()
        // 前面head已校验，此处不再多余校验，算了，我觉得万一有个万一呢，来个一次性校验吧
        val body = responseByGet.body
        if (!responseByGet.isSuccessful || responseByGet.headersContentLength() <= 0 || body == null) {
            throw KDownloadException("GET请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        task.status = Status.RUNNING
        BufferedInputStream(body.byteStream(), DEFAULT_BUFFER_SIZE).use { bis ->
            // 假设一次32K，如果1MB刷新一次，则32次刷新一次，或者再加一个时间纬度可能更好，1秒一次，如果下载速度1秒钟10MB，则一秒钟刷新320次
            val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            var readLength: Int
            var lastEventTime = 0L
            var lastEventPercentage = -1
            while (bis.read(byteArray).also { readLength = it } != -1) {
                writeTempFile.write(byteArray, 0, readLength)
                task.totalDownloadedLength += readLength
                task.updatePercentageProgress()
                if ((isPercentageProgressEvent && lastEventPercentage != task.percentageProgress) || (!isPercentageProgressEvent && System.currentTimeMillis() - lastEventTime > minProgressEventInterval)) {
                    lastEventTime = System.currentTimeMillis()
                    lastEventPercentage = task.percentageProgress
                    onProgressEvent(task, event)
                }
                if (isPause || task.isPause() || task.isCancel()) {
                    Debug.log("${task.name} - 任务被暂停, All=${isPause}, Self=${task.isPause()}, cancel=${task.isCancel()}")
                    break
                }
            }
        }
        // 流关闭失败是否会导致文件不完整或其他问题
        writeTempFile.closeQuietly()
        if (task.isCancel()) {
            // 直接删除，取消删除可以不管错误
            val tempDelete = tempFile.delete()
            Debug.log("${task.name} - 取消下载，删除=${tempDelete}")
        } else {
            // 判断是finish还是pause todo 不能在任务内操作，因为还需要额外操作，如果任务完成，则从队列中删除，如果是暂停则再放回队列中
            if (task.fileLength == task.totalDownloadedLength) {
                if (!tempFile.renameTo(file)) {
                    throw KDownloadException("下载完成，临时文件重命名失败")
                }
                onFinishEvent(task, event)
            } else {
                onPauseEvent(task, event)
            }
            // 这里记录进度，而不是在每次写入时，不考虑突然crash的情况，因为每个buffer记录一次影响的效率更多，得不偿失，也可以考虑一种折中的策略

        }
        Debug.log("${task.name} downloadCore执行结束（非完成）")
        // 抛出异常的情况下走不到这里。所以下一个动作要放到外层
    }


    private fun <T : AbstractDownloadTaskImpl> onTerminateEvent(task: T, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - 当前状态=${task.getStatusString()}")
        event?.terminate?.invoke()
        downloadListener?.onTerminate(task)
    }

    private fun <T : AbstractDownloadTaskImpl> onProgressEvent(task: T, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - ${task.percentageProgress}%")
        event?.progress?.invoke()
        downloadListener?.onProgress(task)
    }

    private fun <T : AbstractDownloadTaskImpl> onFinishEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.FINISHED
        Debug.log("${task.name} - 下载完成")
        event?.finish?.invoke()
        downloadListener?.onFinish(task)
    }

    private fun <T : AbstractDownloadTaskImpl> onPauseEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.PAUSED
        // 要把队列中的任务加回队列中（目前方案没有移出，依旧在队列中），todo 如果是直接异步启动的话呢？是否应该在业务中处理？
        Debug.log("${task.name} - 下载暂停")
        event?.pause?.invoke()
        downloadListener?.onPause(task)
    }

    /**
     * 启动事件
     */
    private fun <T : AbstractDownloadTaskImpl> onStartEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.CONNECTING
        Debug.log("download core - name: ${task.name} url: ${task.url} path: ${task.localPath}")
        // 如果业务需要二选一，可以再加个变量控制，走了自己的回调就不走全局回调
        //event?.start?.invoke() ?: downloadListener?.onStart(task)
        event?.start?.invoke()
        downloadListener?.onStart(task)
    }

    /**
     * 检测空间是否足够，没问题就过，有问题直接抛，方便 todo 最后抽到Util类中
     */
    @Throws(StorageInsufficientException::class)
    private fun isStorageEnough(storageFolder: File, fileLength: Long) {
        // 文件系统上可用的字节数，包括保留的块（普通应用程序不可用）。大多数应用程序将改为使用getAvailableBytes（）。
        val statFs = StatFs(storageFolder.absolutePath)
        var freeSpace = statFs.availableBytes
        // val statFs = StatFs(storageFolder.absolutePath)
        // File读取的freeSpace=12350640128, StatFs读取的freeBytes=12350640128, availableBytes=12199645184
        // freeSpace = freeBytes，都要大于availableBytes 扩展：available < (free + cache + buffers)
        //Debug.log("File读取的freeSpace=$freeSpace, StatFs读取的freeBytes=${statFs.freeBytes}, availableBytes=${statFs.availableBytes}")
        if (freeSpace == 0L) {
            freeSpace = storageFolder.usableSpace
            Debug.log("StatFs获取失败，改用File的usableSpace获取")
        }
        if (freeSpace == 0L) {
            throw StorageInsufficientException("可用空间为0", freeSpace, fileLength)
        }
        if (freeSpace < fileLength) {
            throw StorageInsufficientException("可用空间不足", freeSpace, fileLength)
        }
    }
}