package io.keyss.library.kdownloader.core

import android.os.Looper
import android.util.Log
import io.keyss.library.kdownloader.config.KDownloadException
import io.keyss.library.kdownloader.config.PersistenceType
import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile

/**
 * @author Key
 * Time: 2021/04/21 22:38
 * Description: 不在类上写范型是为了方便使用，在一些需要自定义类型，但是只是简单下载的场景。同时可以使用多种任务类型（Task的实现）来区分业务。
 */
abstract class AbstractKDownloader(taskPersistenceType: @PersistenceType Int = PersistenceType.MEMORY_CACHE) {
    // todo 把这个改成一个配置类的方式，用户可以自己实现具体的参数
    companion object {
        private const val TAG = "KDownloader"
        private const val TEMP_FILE_SUFFIX = ".kd"
        private const val CONFIG_SUFFIX = ".cfg"
        const val MIN_BLOCK_LENGTH = 1024 * 1024 * 1024

        // 8K显然已经不能满足现在的设备和网速，重新定义一个
        const val DEFAULT_BUFFER_SIZE = 32 * 1024
    }

    /**最大线程数量*/
    var maxConnections = 1
        set(value) {
            field = when {
                value <= 0 -> {
                    1
                }
                value >= 20 -> {
                    20
                }
                else -> {
                    value
                }
            }
        }

    /**
     * 队列下载的队列，非历史任务列表，比如通过同步下载或者单独下载的任务就不在此列表中
     */
    private val mTasks: TaskList<AbstractKDownloadTask> = TaskList {
        if (it > 0) {
            nextQueue()
        }
    }
    var downloadListener: IDownloadListener? = null

    // fixme 改成直接传实现
    //private val mTaskPersistenceType: @PersistenceType Int = taskPersistenceType

    /**
     * todo 改成可自定义实现类型
     */
    val history = MemoryCacheHistoryTask()

    /**
     * 整个下载器的状态，默认就是运行状态。
     */
    @Volatile
    private var isPause = false

    /** 是否正在遍历Task */
    @Volatile
    private var isTaskTraversing = false

    /**
     * 最小进度事件输出时间，默认5秒，想要每次都输出只要设小就行了
     */
    var minProgressEventInterval = 5_000

    /**
     * 而我更想要百分比输出都方式，我再加一个，而且设成了默认
     */
    var isPercentageProgressEvent = true

    // 内存的话每次启动都会从0开始，数据库的话需要读取一次ID
    /*private var mIdCounter: AtomicInteger = when (mTaskPersistenceType) {
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
        return mIdCounter.getAndIncrement()
    }*/

    /**
     * 理论上使用okhttp优于使用HttpURLConnection，且几乎所有项目都使用okhttp
     * 如后续使用中没有什么不可解决的因素不再换回HttpURLConnection
     *
     */
    private var mClient = OkHttpClient
        .Builder()
        // 下载文件可能很大，不实用超时，只管理进度超时，其他超时都采用默认都10秒
        // todo 我可能对这个readTimeout存在误解，再深究
        //.readTimeout(0, TimeUnit.NANOSECONDS)
        .build()

    /**
     * 自定义OkHttpClient
     */
    fun setCustomOkHttpClient(client: OkHttpClient) {
        this.mClient = client
    }

    fun getRunningTasks(): List<AbstractKDownloadTask> {
        return mTasks.filter {
            it.isStarting()
        }
    }

    fun getWaitingTasks(): List<AbstractKDownloadTask> {
        return mTasks.filter {
            it.isWaiting()
        }.sortedByDescending { it.priority }
    }

    fun getInQueueTasks(): List<AbstractKDownloadTask> {
        return mTasks.filter {
            it.isInQueue()
        }
    }

    fun getWithoutFinishTasks(): List<AbstractKDownloadTask> {
        return mTasks.filter {
            !it.isFinished()
        }
    }

    /**
     * 移出不会删除任务文件，包括临时文件，会留下垃圾
     */
    fun <T : AbstractKDownloadTask> removeTask(vararg tasks: T) {
        for (task in tasks) {
            if (task.isStarting()) {
                task.suspend()
            }
            mTasks.remove(task)
        }
    }

    fun <T : AbstractKDownloadTask> removeTaskAndDeleteFile(vararg tasks: T) {
        for (task in tasks) {
            if (task.isStarting()) {
                // 如果还在执行中，先挂起，至少先停掉，万一还没执行完也麻烦
                task.cancel()
            } else {
                task.deleteRelatedFiles()
            }
            mTasks.remove(task)
        }
    }

    fun <T : AbstractKDownloadTask> cancelTask(vararg tasks: T) {
        for (task in tasks) {
            task.cancel()
            mTasks.remove(task)
        }
    }

    private fun nextQueue(): Unit {
        if (!isPause) {
            startTaskQueue()
        }
    }

    open fun startTaskQueue(): Unit = withDefers {
        defer {
            isTaskTraversing = false
        }
        if (isTaskTraversing) {
            Debug.log("正在遍历集合，撤销此次startTaskQueue")
            // 不需要修改状态，直接return
            return
        }
        isTaskTraversing = true
        isPause = false
        // 启动队列中的任务
        val runningTasks = getRunningTasks()
        if (runningTasks.size >= maxConnections) {
            return@withDefers
        }
        val remainingSize = maxConnections - runningTasks.size
        // fixme 这有一个问题，队列中优先级最高的任务，被手动暂停后，再次开启的还是这个任务？所以这个优先级的原则应该是要不要排除手动暂停的方式呢？这样的话难道优先级最高的反而要去队尾？这不科学
        // 所以我决定去掉单任务暂停，只留下取消
        // todo 已经完成和已取消的任务，考虑处理方式，理论上检测是否下载应该已本地文件为准，而非任务队列
        val waitingTasks = getWaitingTasks()
        Debug.log("还有${remainingSize}个可下载位置，以及${waitingTasks.size}个等待下载的任务")
        repeat(remainingSize) { index ->
            waitingTasks.getOrNull(index)?.let { task ->
                // 基类没有绑定生命周期，设定也是全局可用
                asyncDownloadWrapper(task)
            }
        }
    }

    fun stopTaskQueue(): Unit {
        isPause = true
    }

    /**
     * 添加一个下载任务，不启动
     */
    open fun addTask(task: AbstractKDownloadTask): Boolean {
        return mTasks.addIfAbsent(task)
    }

    /**
     * 添加一个下载任务，并启动
     */
    fun <T : AbstractKDownloadTask> addTaskAndStart(task: T): Boolean {
        val addIfAbsent = addTask(task)
        Debug.log("添加任务并启动，添加=$addIfAbsent")
        // 暂停的情况下才启动，否则有add启动
        startTaskQueue()
        return addIfAbsent
    }

    /**
     * 已组级别概念进行下载，一整组资源完成算完成
     */
    @Throws(Exception::class)
    fun <T : AbstractKDownloadTask> createTaskGroup(vararg task: T): Boolean {
        val taskGroup = TaskGroup(task.hashCode(), task.toList())
        return addTaskGroup(taskGroup)
    }

    /**
     * 已组级别概念进行下载，一整组资源完成算完成
     */
    @Throws(Exception::class)
    fun <T : AbstractKDownloadTask> createTaskGroup(tasks: Collection<T>): Boolean {
        val taskGroup = TaskGroup(tasks.hashCode(), tasks)
        return addTaskGroup(taskGroup)
    }

    /**
     * 新增一个任务组
     */
    open fun <T : TaskGroup> addTaskGroup(taskGroup: T): Boolean {
        if (!checkAndAddGroup(taskGroup)) {
            return false
        }

        // fixme 线程的处理，获取长度在子线程
        taskGroup.tasks.forEach {
            // 相当于顺便检查正确性
            taskGroup.addTotalLength(
                try {
                    getContentLengthLongOrNull(it.url) ?: 0
                } catch (e: Exception) {
                    0
                }
            )
            it.group = taskGroup
        }
        return mTasks.addAll(taskGroup.tasks)
    }

    private fun <T : TaskGroup> checkAndAddGroup(taskGroup: T): Boolean {
        if (taskGroup.tasks.isEmpty()) {
            return false
        }
        // 去重
        for (task in mTasks) {
            for (newTask in taskGroup.tasks) {
                // 不删除已存在 并且 URL、存储路径、name相同
                if (!newTask.isDeleteExist && newTask == task) {
                    Log.w(TAG, "$newTask - checkAndAddGroup: 该任务已存在，取消下载整组任务")
                    return false
                }
            }
        }
        return true
    }

    /**
     * 添加任务组并启动
     */
    fun <T : TaskGroup> addTaskGroupAndStart(taskGroup: T): Boolean {
        val addTaskGroup = addTaskGroup(taskGroup)
        Debug.log("添加任务组并启动，添加=$addTaskGroup")
        startTaskQueue()
        return addTaskGroup
    }

    /**
     * 添加任务组并启动
     */
    fun <T : AbstractKDownloadTask> createTaskGroupAndStart(tasks: Collection<T>): Boolean {
        val addTaskGroup = createTaskGroup(tasks)
        Debug.log("创建任务组并启动，添加=$addTaskGroup")
        startTaskQueue()
        return addTaskGroup
    }

    /**
     * 已切换线程，可直接再Main中挂起调用
     */
    @Throws(Exception::class)
    suspend fun <T : AbstractKDownloadTask> syncDownloadTask(task: T): T = task.apply {
        withContext(Dispatchers.IO) {
            downloadWrapper(this@apply, null, true)
        }
    }

    /**
     * 异步单任务，直接启动型，跳过队列，异步任务不抛异常
     */
    fun <T : AbstractKDownloadTask> asyncDownloadTask(task: T, event: DownloadEvent<T>) {
        // 内部不实用扩展方法，因为扩展方法是使用默认下载器，也许调用者此次是使用了其他的下载器
        //task.async(event)
        asyncDownloadWrapper(task, event)
    }

    private fun <T : AbstractKDownloadTask> asyncDownloadWrapper(task: T, event: DownloadEvent<T>? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            downloadWrapper(task, event, false)
        }
    }

    @Throws(Exception::class)
    private suspend fun <T : AbstractKDownloadTask> downloadWrapper(task: T, event: DownloadEvent<T>?, isThrowOut: Boolean) {
        // 判断是否历史任务
        val inHistoryTask = isInHistoryTaskList(task)
        if (inHistoryTask == null) {
            val addToHistoryTaskList = addToHistoryTaskList(task)
            // 如果加入失败的话，暂时没处理，就会不可避免的重复下载 todo 如果处理合适？无UI的情况下，是无法交予用户决定的
        } /*else {
            // 如果存在，则采用任务列表中的属性，也许任务类型不同，所以不能采用直接替换对象
            task._id = inHistoryTask._id
            task.status = inHistoryTask.status
        }*/
        // 加上判断任务列表中的值
        // 不重复执行，不加锁会发生进入两次(异步时进入等)，为了不走finally移到外面
        if (!isTaskCanStart(task) || (inHistoryTask != null && !isTaskCanStart(inHistoryTask))) {
            Debug.log("${task.getLogName()} 该任务已经开始，撤销本次执行")
            // TODO: 2021/5/25 被中断？
            // todo 这时候应该传一个什么action出去呢？
            return
        }
        // 将最后一次调用的子类设为默认下载器，如果同时用了两个则会变成最后一个，因为只设定了一次
        setDefault()
        try {
            downloadCore(task, event)
        } catch (e: Exception) {
            // 先走catch，自动重试的，哪怕最后一个任务，在finally中也可以继续被执行到
            onFailedEvent(task, event, e, isThrowOut)
        } finally {
            // onTerminate 应该是流程结束的必走，类似于菊花消失的场景，至于是什么情况走到这里的，应该再自主判断状态
            onTerminateEvent(task, event)
            nextQueue()
        }
    }

    /**
     * 下载核心逻辑
     * 请在子线程执行
     */
    @Throws(Exception::class)
    private suspend fun <T : AbstractKDownloadTask> downloadCore(task: T, event: DownloadEvent<T>? = null) {
        // 启动在最前方，必然要走，例如弹出dialog子类的
        onStartEvent(task, event)
        // 先给启动状态，再抛出异常，状态过程才完整
        if (Looper.getMainLooper().isCurrentThread) {
            throw KDownloadException("请在子线程下载")
        }
        // 验证路径的可用性。首先校验本地
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
            throw KDownloadException("父文件夹没有写入权限, Path: ${storageFolder.absolutePath}")
        }

        // 校验URL的正确性。然后校验的是网络
        if (!task.url.startsWith("http")) {
            // 报错，或者直接加个http
            throw KDownloadException("下载地址不正确, URL=[${task.url}]")
        }

        // 写入长度，以及判断是否足够写入。最后相当于组合校验
        //task.fileLength = getContentLengthLongOrNull(task.url)
        checkContentLengthAndBreakpointSupport(task)
        // ContentLength 有可能会不存在，取消校验
        // 验证硬盘是否够写入
        StorageUtil.isStorageEnough(storageFolder, task.fileLength ?: 0)

        // 验证文件名
        if (task.name.isNullOrBlank()) {
            task.name = task.url.takeLastWhile { it != '/' }
        }
        if (task.name.isNullOrBlank()) {
            // 要保证每个相同的URL转出来的是一样的，所以不能使用random的做法
            //task.name = UUID.randomUUID().toString()
            task.name = MD5Util.stringToMD5(task.url)
        }
        // byte/1024/1024/1024 G
        Debug.log("${task.name} - length=${task.fileLength}≈${(task.fileLength ?: 0) / 1024 / 1024 / 1000.0}GB, 是否支持断点续传=${task.isSupportBreakpoint}")

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
                // todo 文件正确的情况下可以直接回调
                throw KDownloadException("下载存在同名文件，且未设定覆盖")
            }
        }
        // 开始下载
        // 同时还要检测是否是任务，任务的话断点续传，未完成的任务前面加点隐藏。完成后重命名，如果这个过程中名字被占用怎么随机重命名
        File(storageFolder, task.name!! + TEMP_FILE_SUFFIX).let {
            task.tempFile = it
            val configFile = File(it.absolutePath + CONFIG_SUFFIX)
            task.configFile = configFile
            if (it.exists()) {
                // 判断是否是同一个文件
                val configText = try {
                    configFile.readText()
                } catch (e: Exception) {
                    ""
                }
                Debug.log("${task.name} - 比对etag，任务=${task.etag}，本地=${configText}")
                if (configText == task.etag) {
                    task.downloadedLength = it.length()
                    Debug.log("${task.name} - etag相同，跳到文件末尾, length=${task.downloadedLength}")
                } else {
                    if (task.deleteTemporaryFile()) {
                        throw KDownloadException("删除临时文件失败")
                    }
                    if (task.deleteConfigFile()) {
                        throw KDownloadException("删除配置文件失败")
                    }
                }
            }
        }
        // MappedByteBuffer 也有不足，就是在数据量很小的时候，表现比较糟糕，那是因为 direct buffer 的初始化时间较长，所以建议大家只有在数据量较大的时候，在用 MappedByteBuffer。
        // 且存在内存占用和文件关闭等不确定问题。被 MappedByteBuffer 打开的文件只有在垃圾收集时才会被关闭，而这个点是不确定的。javadoc 里是这么说的：
        // A mapped byte buffer and the file mapping that it represents remain valid until the buffer itself is garbage-collected. ——JavaDoc
        val writeTempFile = RandomAccessFile(task.tempFile, "rw")
        /*if (task.fileLength != null && task.fileLength!! > 0) {
            // length=3839937610≈3.662GB 耗时大约2分钟，取消此方案
            writeTempFile.setLength(task.fileLength!!)
        }*/
        // 需要校验写了多少了
        /*if (task.maxConnections == 1) {
            val getRequest = headRequest.newBuilder().get().build()
        }*/
        // 一条线程下载
        val getRequest = Request
            .Builder()
            .url(task.url)
            .get()
            .also {
                if (task.downloadedLength != 0L && task.fileLength != null) {
                    if (task.downloadedLength < task.fileLength!!) {
                        it.header("Range", "bytes=${task.downloadedLength}-${task.fileLength!!}")
                        // seek length 不需要-1
                        writeTempFile.seek(task.downloadedLength)
                    } else if (task.downloadedLength == task.fileLength!!) {
                        // 已下完 todo 好像不用处理
                    } else {
                        task.downloadedLength = 0
                        writeTempFile.seek(0)
                    }
                }
            }
            .build()
        Debug.log("getRequest已构建：headers=${getRequest.headers}")
        val responseByGet = mClient.newCall(getRequest).execute()
        // todo 验证是否支持，Range是在HTTP/1.1中新增的请求头，如果Server端支持Range，会在响应头中添加Accept-Range: bytes；否则，会返回 Accept-Range: none
        // todo 2. code=416也是不支持
        //val isAcceptRanges = responseByGet.header("Accept-Ranges", "none")
        //Debug.log("getRequest已返回：http code=${responseByGet.code}, headers=${responseByGet.headers}")
        // 前面head已校验，此处不再多余校验，算了，我觉得万一有个万一呢，来个一次性校验吧
        val body = responseByGet.body
        if (!responseByGet.isSuccessful) {
            throw KDownloadException("GET请求错误, HTTP status code=${responseByGet.code}, HTTP status message=${responseByGet.message}, ContentLength=${responseByGet.getContentLengthOrNull()}")
        }
        if (body == null) {
            throw KDownloadException("GET请求body为null, HTTP status code=${responseByGet.code}, HTTP status message=${responseByGet.message}, ContentLength=${responseByGet.getContentLengthOrNull()}")
        }
        task.status = Status.RUNNING
        // 将etag写入配置文件
        task.etag?.takeIf { it.isNotBlank() }?.let {
            try {
                task.configFile?.writeText(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // 非正常走完
        var notFinished = false
        BufferedInputStream(body.byteStream(), DEFAULT_BUFFER_SIZE).use { bis ->
            // 假设一次32K，如果1MB刷新一次，则32次刷新一次，或者再加一个时间纬度可能更好，1秒一次，如果下载速度1秒钟10MB，则一秒钟刷新320次
            val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            var readLength: Int
            var lastEventTime = 0L
            var lastEventPercentage = -1
            var lastGroupEventPercentage = -1
            while (bis.read(byteArray).also { readLength = it } != -1) {
                writeTempFile.write(byteArray, 0, readLength)
                task.downloadedLength += readLength
                task.updatePercentageProgress()
                task.group?.apply {
                    addTotalDownloadedLength(readLength)
                    updatePercentageProgress()
                    // 考虑到整组的任务数据量可能很大，只按百分比输出
                    if (lastGroupEventPercentage != percentageProgress) {
                        lastGroupEventPercentage = percentageProgress
                        onGroupProgressEvent(this, event as? GroupDownloadEvent<T>)
                    }
                }

                if ((isPercentageProgressEvent && lastEventPercentage != task.percentageProgress) || (!isPercentageProgressEvent && System.currentTimeMillis() - lastEventTime > minProgressEventInterval)) {
                    lastEventTime = System.currentTimeMillis()
                    lastEventPercentage = task.percentageProgress
                    onProgressEvent(task, event)
                }
                if (isPause || task.isSuspend() || task.isCancel()) {
                    notFinished = true
                    Debug.log("${task.name} - 任务被暂停, All=${isPause}, Self Suspend=${task.isSuspend()}, cancel=${task.isCancel()}")
                    break
                }
            }
        }
        // 流关闭失败是否会导致文件不完整或其他问题
        writeTempFile.closeQuietly()
        if (task.isCancel()) {
            Debug.log("${task.name} - 取消下载")
            // 直接删除，取消删除可以不管错误
            task.deleteRelatedFiles()
        } else {
            // 判断是finish还是pause，task.fileLength为null则无法判断是否完成，只能从是否正常结尾来判断
            // todo 不能在任务内操作，因为还需要额外操作，如果任务完成，则从队列中删除，如果是暂停则再放回队列中
            if (!notFinished || (task.fileLength != null && task.downloadedLength >= task.fileLength!!)) {
                if (!task.tempFile!!.renameTo(file)) {
                    throw KDownloadException("下载完成，缓存文件重命名失败")
                }
                // 下完了才有file，所以下载完成才赋值，不然外部拿到task使用会有歧义
                task.file = file
                task.tempFile = null
                task.deleteConfigFile()
                onFinishEvent(task, event)
            } else {
                onPauseEvent(task, event)
            }
            // 这里记录进度，而不是在每次写入时，不考虑突然crash的情况，因为每个buffer记录一次影响的效率更多，得不偿失，也可以考虑一种折中的策略
        }
        Debug.log("${task.name} downloadCore执行结束（非完成）")
        // 抛出异常的情况下走不到这里。所以下一个动作要放到外层
    }

    @Synchronized
    private fun <T : AbstractKDownloadTask> isTaskCanStart(task: T): Boolean {
        if (task.isStarting()) {
            Debug.log("${task.getLogName()} 该任务已经开始，撤销本次执行")
            return false
        }
        // 状态同步修改，防止多线程一起进入
        task.status = Status.CONNECTING
        return true
    }

    /** 不用默认的-1，防止出现加法错误以及方便后续的判断 */
    @Throws(Exception::class)
    private fun getContentLengthLongOrNull(url: String): Long? {
        val headRequest = Request
            .Builder()
            .url(url)
            .header("Accept-Encoding", "identity")
            .head()
            .build()
        // @Throws(IOException::class)
        val responseByHead = mClient.newCall(headRequest).execute()
        // 验证地址的连接
        if (!responseByHead.isSuccessful) {
            throw KDownloadException("HEAD请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        //Debug.log("$url - getContentLengthLongOrNull-headers:\n${responseByHead.headers}")
        //Debug.log("$url - ContentLengthString: ${responseByHead.headers["Content-Length"]}, ContentLengthLong: $contentLengthOrNull")
        return responseByHead.getContentLengthOrNull()
    }


    /**
     * 根据head获取文件长度，是否支持断点续传，ETag等
     */
    @Throws(Exception::class)
    private fun <T : AbstractKDownloadTask> checkContentLengthAndBreakpointSupport(task: T) {
        val headRequest = Request
            .Builder()
            .url(task.url)
            .header("Accept-Encoding", "identity")
            .head()
            .build()
        // @Throws(IOException::class)
        val responseByHead = mClient.newCall(headRequest).execute()
        // 验证地址的连接
        if (!responseByHead.isSuccessful) {
            throw KDownloadException("HEAD请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        task.fileLength = responseByHead.getContentLengthOrNull()
        if (task.fileLength == null) {
            Debug.log("${task.url} - 获取不到Content-Length，Header:\n${responseByHead.headers}")
        }
        task.isSupportBreakpoint = responseByHead.headers["Accept-Ranges"] == "bytes"
        // todo 应该是比对 新老etag是否相等
        task.etag = responseByHead.headers["ETag"]
    }

    /**
     * 检查整组的完成状态，fixme 关于组的操作不是很满意
     */
    private suspend fun <T : AbstractKDownloadTask> checkGroupStatus(group: TaskGroup, event: DownloadEvent<T>?) {
        var isGroupFinished = true
        var isGroupTerminated = true
        // todo 假设3个任务，一个完成，一个取消，一个暂停，那就一个都不会走，这在处理上应该有问题
        group.tasks.forEach {
            // 有一个未完结
            if (isGroupTerminated && !it.isTerminated()) {
                isGroupTerminated = false
            }
            // 有一个未完成
            if (isGroupFinished && !it.isFinished()) {
                isGroupFinished = false
            }
        }
        if (isGroupFinished) {
            onGroupFinishEvent(group, event as? GroupDownloadEvent<T>)
        }
        if (isGroupTerminated) {
            onGroupTerminateEvent(group, event as? GroupDownloadEvent<T>)
        }
    }

    /**
     * 任务结束后的通知，群组任务就放到单任务到终止位置检测
     */
    private suspend fun <T : AbstractKDownloadTask> onTerminateEvent(task: T, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - 当前状态=${task.getStatusText()}")
        event?.terminate?.invoke()
        downloadListener?.onTerminate(task)
        task.group?.let { group ->
            checkGroupStatus(group, event)
        }
    }

    private fun <T : AbstractKDownloadTask> onProgressEvent(task: AbstractKDownloadTask, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - ${task.percentageProgress}%")
        // 内部处理协程
        task.callOnProgress()
        // 进度的耗时业务处理不能影响到下载，防止调用者不知道
        event?.progress?.let {
            GlobalScope.launch(Dispatchers.IO) {
                it.invoke()
            }
        }
        downloadListener?.let {
            GlobalScope.launch(Dispatchers.IO) {
                it.onProgress(task)
            }
        }
    }

    private suspend fun <T : AbstractKDownloadTask> onFinishEvent(task: AbstractKDownloadTask, event: DownloadEvent<T>?) {
        task.status = Status.FINISHED
        Debug.log("${task.name} - 下载完成")
        event?.finish?.invoke()
        // 下载完成后无UI类的可以在event事件中移除任务，UI类的不移除，展示给用户已完成状态，也可以在Terminate中统一处理多个状态
        downloadListener?.onFinish(task)
    }

    private suspend fun <T : AbstractKDownloadTask> onPauseEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.PAUSED
        // 要把队列中的任务加回队列中（目前方案没有移出，依旧在队列中），todo 如果是直接异步启动的话呢？是否应该在业务中处理？
        Debug.log("${task.name} - 下载暂停")
        event?.pause?.invoke()
        downloadListener?.onPause(task)
    }

    /**
     * 启动事件
     */
    private suspend fun <T : AbstractKDownloadTask> onStartEvent(task: T, event: DownloadEvent<T>?) {
        Debug.log("download core - name: ${task.name} path: ${task.localPath}\nurl: ${task.url}")
        // 如果业务需要二选一，可以再加个变量控制，走了自己的回调就不走全局回调
        //event?.start?.invoke() ?: downloadListener?.onStart(task)
        event?.start?.invoke()
        downloadListener?.onStart(task)
    }

    /**
     * 失败的事件，这里很复杂，能否重试完全依赖于是因为什么错误
     */
    @Throws(Exception::class)
    private suspend fun <T : AbstractKDownloadTask> onFailedEvent(task: T, event: DownloadEvent<T>?, e: Exception, isThrowOut: Boolean) {
        // failed()内部处理状态和重试，然后event完了刚好进到finally进行重试，这一系列流程是连贯的，修改时要注意！！
        if (task.failed(e)) {
            downloadListener?.onFail(task, e)
            event?.fail?.invoke(e)
            // 关于重试，自动重试还是业务层去重试？最终方案，不应在逻辑层直接重试，但可以设定重试的条件，相当于还是由业务层来管理
            // todo 如果有自动重试，是不是要再加一个重试的event？
            // 最终觉得采用多加一个参数来简化，少包一层try catch，只有同步模式才抛到外面
            // 如果不采用回调方式则继续往外抛，总之要丢给业务层去处理
            // 网络没连接UnknownHostException，超时SocketTimeout
            if (isThrowOut) {
                throw e
            }
        }
    }

    private suspend fun <T : AbstractKDownloadTask> onGroupFinishEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载完成")
        event?.groupFinish?.invoke()
        (downloadListener as? IGroupDownloadListener)?.onGroupFinish(group)
    }

    private suspend fun <T : AbstractKDownloadTask> onGroupTerminateEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载结束")
        event?.groupTerminate?.invoke()
        (downloadListener as? IGroupDownloadListener)?.onGroupTerminate(group)
    }

    private fun <T : AbstractKDownloadTask> onGroupProgressEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载进度: ${group.percentageProgress}%")
        // 进度的耗时业务处理不能影响到下载
        GlobalScope.launch(Dispatchers.IO) {
            event?.groupProgress?.invoke()
            (downloadListener as? IGroupDownloadListener)?.onGroupProgress(group)
        }
    }


    /** Returns the Content-Length as reported by the response headers. */
    private fun Response.getContentLengthOrNull(): Long? {
        return headers["Content-Length"]?.toLongOrNull()
    }

    /**
     * 是否在任务列表中
     */
    private fun <T : AbstractKDownloadTask> isInHistoryTaskList(task: T): AbstractKDownloadTask? {
        return history.find(task)
    }

    private fun <T : AbstractKDownloadTask> addToHistoryTaskList(task: T): Boolean {
        return history.add(task)
    }

    /** 设为默认下载，没有反选，暂时想不到有反选的需求的场景 */
    fun setDefault() {
        if (mDefaultKDownloader != this) {
            mDefaultKDownloader = this
        }
    }
}