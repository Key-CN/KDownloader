package io.keyss.library.kdownloader.core

import android.os.Looper
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
    // todo 把这个改成一个配置类的方式，用户可以自己实现具体的参数
    companion object {
        private const val TAG = "KDownloader"
        private const val TEMP_FILE_SUFFIX = ".kd"
        private const val CONFIG_SUFFIX = ".cfg"
        const val MIN_BLOCK_LENGTH = 1024 * 1024 * 1024

        // 8K显然已经不能满足现在的设备和网速，重新定义一个
        const val DEFAULT_BUFFER_SIZE = 32 * 1024
    }

    val mTasks: CopyOnWriteArrayList<AbstractKDownloadTask> = CopyOnWriteArrayList()
    var downloadListener: IDownloadListener? = null
    private var mTaskPersistenceType: @PersistenceType Int = taskPersistenceType

    /**
     * 整个下载器的状态，默认就是运行状态。
     */
    @Volatile
    private var isPause = false

    /**是否为默认下载器*/
    private var isDefault = false

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
        // todo 我可能对这个readTimeout存在误解，再深究
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

    /**
     * 清楚所有生命周期已完结的任务，包括finish和cancel
     */
    fun removeAllLifecycleOverTasks() {
        mTasks.removeAll {
            it.isLifecycleOver()
        }
        // Call requires API level 24
        // tasks.removeIf
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

    fun <T : AbstractKDownloadTask> cancelTask(vararg tasks: T) {
        for (task in tasks) {
            task.cancel()
            mTasks.remove(task)
        }
    }

    fun startTaskQueue() {
        // 如果正在运行中返回，也可以执行，如果有空位置则补进去
        /*if () {
            return
        }*/
        isPause = false
        // 启动队列中的任务
        val runningTasks = getRunningTasks()
        if (runningTasks.size >= maxConnections) {
            return
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
                asyncDownloadWrap(task)
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
        val addIfAbsent = mTasks.addIfAbsent(task)
        // CopyOnWriteArrayList 不能排序，取的时候排
        /*mTasks.sortBy {
            it.priority
        }*/
        Debug.log("仅添加任务，添加=$addIfAbsent")
        if (!isPause) {
            startTaskQueue()
        }
        return addIfAbsent
    }

    /**
     * 添加一个下载任务，并启动
     */
    fun <T : AbstractKDownloadTask> addTaskAndStart(task: T): Boolean {
        val addIfAbsent = addTask(task)
        Debug.log("添加任务并启动，添加=$addIfAbsent")
        // 暂停的情况下才启动，否则有add启动
        if (isPause) {
            startTaskQueue()
        }
        return addIfAbsent
    }

    /**
     * 已组级别概念进行下载，一整组资源完成算完成
     */
    @Throws(Exception::class)
    fun <T : AbstractKDownloadTask> createTaskGroup(tasks: Collection<T>): Boolean {
        if (tasks.isEmpty()) {
            return false
        }

        val taskGroup = TaskGroup(tasks.hashCode(), tasks)
        tasks.forEach {
            // 相当于顺便检查正确性
            taskGroup.addTotalLength(getContentLengthLong(it.url))
            it.group = taskGroup
        }
        // 开始下载
        val addAllSuccessful = mTasks.addAll(tasks)
        if (!isPause) {
            startTaskQueue()
        }
        return addAllSuccessful
    }

    @Throws(Exception::class)
    fun <T : TaskGroup> addTaskGroup(taskGroup: T): Boolean {
        if (taskGroup.tasks.isEmpty()) {
            return false
        }

        taskGroup.tasks.forEach {
            // 相当于顺便检查正确性
            taskGroup.addTotalLength(getContentLengthLong(it.url))
            it.group = taskGroup
        }
        // 开始下载
        val addAllSuccessful = mTasks.addAll(taskGroup.tasks)
        if (!isPause) {
            startTaskQueue()
        }
        return addAllSuccessful
    }

    /**
     * 添加任务组并启动
     */
    fun <T : TaskGroup> addTaskGroupAndStart(taskGroup: T): Boolean {
        val addTaskGroup = addTaskGroup(taskGroup)
        Debug.log("添加任务组并启动，添加=$addTaskGroup")
        if (isPause) {
            startTaskQueue()
        }
        return addTaskGroup
    }

    /**
     * 添加任务组并启动
     */
    fun <T : AbstractKDownloadTask> createTaskGroupAndStart(tasks: Collection<T>): Boolean {
        val addTaskGroup = createTaskGroup(tasks)
        Debug.log("创建任务组并启动，添加=$addTaskGroup")
        if (isPause) {
            startTaskQueue()
        }
        return addTaskGroup
    }

    /**
     * 已切换线程，可直接再Main中挂起调用
     */
    @Throws(Exception::class)
    suspend fun <T : AbstractKDownloadTask> syncDownloadTask(task: T): T = task.apply {
        withContext(Dispatchers.IO) {
            downloadWrap(this@apply, null, true)
        }
    }

    /**
     * 异步单任务，直接启动型，跳过队列，异步任务不抛异常
     */
    fun <T : AbstractKDownloadTask> asyncDownloadTask(task: T, event: DownloadEvent<T>) {
        // 内部不实用扩展方法，因为扩展方法是使用默认下载器，也许调用者此次是使用了其他的下载器
        //task.async(event)
        asyncDownloadWrap(task, event)
    }

    private fun <T : AbstractKDownloadTask> asyncDownloadWrap(task: T, event: DownloadEvent<T>? = null) {
        GlobalScope.launch(Dispatchers.IO) {
            downloadWrap(task, event, false)
        }
    }

    @Throws(Exception::class)
    private fun <T : AbstractKDownloadTask> downloadWrap(task: T, event: DownloadEvent<T>? = null, isThrowOut: Boolean) {
        try {
            downloadCore(task, event)
        } catch (e: Exception) {
            onFailedEvent(task, e, event)
            // 关于重试，自动重试还是业务层去重试？最终方案，不应在逻辑层直接重试，但可以设定重试的条件，相当于还是由业务层来管理
            // todo 如果有自动重试，是不是要再加一个重试的event？
            // 最终觉得采用多加一个参数来简化，少包一层try catch，只有同步模式才抛到外面
            // 如果不采用回调方式则继续往外抛，总之要丢给业务层去处理
            // 网络没连接UnknownHostException，超时SocketTimeout
            if (isThrowOut) {
                throw e
            }
        } finally {
            // onTerminate 应该是流程结束的必走，类似于菊花消失的场景，至于是什么情况走到这里的，应该再自主判断状态
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
    private fun <T : AbstractKDownloadTask> downloadCore(task: T, event: DownloadEvent<T>? = null) {
        if (isDefault) {
            default()
        }
        onStartEvent(task, event)
        // todo 搜索任务栈中是否存在
        // 1。 直接下载，但这个任务和任务栈中的某个任务完全相同？
        // 2。 添加。是添加不进，在add的地方已经被过滤掉了
        /*if (mTasks.contains(task)) {
            mTasks.getOrNull(task)
            if ()
        }*/

        // 先给启动状态，再抛出异常，状态过程才完整
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

        // 写入长度
        task.fileLength = getContentLengthLong(task.url)
        if (task.fileLength <= 0) {
            throw KDownloadException("文件长度不正确, ContentLength=${task.fileLength}")
        }
        // 验证硬盘是否够写入
        StorageUtil.isStorageEnough(storageFolder, task.fileLength)

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
        // 到这里才创建出file
        task.relatedFiles.addIfAbsent(tempFile)
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
                if (task.downloadedLength != 0L) {
                    it.header("Range", "bytes=${task.downloadedLength}-${task.fileLength}")
                    // seek length 不需要+1
                    writeTempFile.seek(task.downloadedLength)
                }
            }
            .build()
        Debug.log("getRequest已构建：headers=${getRequest.headers}")
        val responseByGet = mClient.newCall(getRequest).execute()
        // todo 验证是否支持，Range是在HTTP/1.1中新增的请求头，如果Server端支持Range，会在响应头中添加Accept-Range: bytes；否则，会返回 Accept-Range: none
        //val isAcceptRanges = responseByGet.header("Accept-Ranges", "none")
        //Debug.log("getRequest已返回：http code=${responseByGet.code}, headers=${responseByGet.headers}")
        // 前面head已校验，此处不再多余校验，算了，我觉得万一有个万一呢，来个一次性校验吧
        val body = responseByGet.body
        if (!responseByGet.isSuccessful || responseByGet.headersContentLength() <= 0 || body == null) {
            throw KDownloadException("GET请求错误, HTTP status code=${responseByGet.code}, HTTP status message=${responseByGet.message}")
        }
        task.status = Status.RUNNING
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
                task.group?.apply {
                    addTotalDownloadedLength(readLength)
                    updatePercentageProgress()
                    // 考虑到整组的任务数据量可能很大，只按百分比输出
                    if (lastGroupEventPercentage != percentageProgress) {
                        lastGroupEventPercentage = percentageProgress
                        onGroupProgressEvent(this, event as? GroupDownloadEvent<T>)
                    }
                }
                task.updatePercentageProgress()
                if ((isPercentageProgressEvent && lastEventPercentage != task.percentageProgress) || (!isPercentageProgressEvent && System.currentTimeMillis() - lastEventTime > minProgressEventInterval)) {
                    lastEventTime = System.currentTimeMillis()
                    lastEventPercentage = task.percentageProgress
                    onProgressEvent(task, event)
                }
                if (isPause || task.isSuspend() || task.isCancel()) {
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
            //val tempDelete = tempFile.delete()
            task.deleteRelatedFiles()
        } else {
            // 判断是finish还是pause todo 不能在任务内操作，因为还需要额外操作，如果任务完成，则从队列中删除，如果是暂停则再放回队列中
            if (task.fileLength == task.downloadedLength) {
                if (!tempFile.renameTo(file)) {
                    throw KDownloadException("下载完成，临时文件重命名失败")
                }
                onFinishEvent(task, event)
                // 比较后存入，重命名成功后剩下一个
                task.relatedFiles.addIfAbsent(file)
                task.relatedFiles.remove(tempFile)
            } else {
                onPauseEvent(task, event)
            }
            // 这里记录进度，而不是在每次写入时，不考虑突然crash的情况，因为每个buffer记录一次影响的效率更多，得不偿失，也可以考虑一种折中的策略

        }
        Debug.log("${task.name} downloadCore执行结束（非完成）")
        // 抛出异常的情况下走不到这里。所以下一个动作要放到外层
    }

    @Throws(Exception::class)
    private fun getContentLengthLong(url: String): Long {
        val headRequest = Request
            .Builder()
            .url(url)
            .head()
            .build()
        // @Throws(IOException::class)
        val responseByHead = mClient.newCall(headRequest).execute()
        // 验证地址的连接
        if (!responseByHead.isSuccessful) {
            throw KDownloadException("HEAD请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        return responseByHead.headersContentLength()
    }

    /**
     * 检查整组的完成状态，fixme 关于组的操作不是很满意
     */
    private fun <T : AbstractKDownloadTask> checkGroupStatus(group: TaskGroup, event: DownloadEvent<T>?) {
        var isGroupFinished = true
        var isGroupTerminated = true
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
    private fun <T : AbstractKDownloadTask> onTerminateEvent(task: T, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - 当前状态=${task.getStatusText()}")
        event?.terminate?.invoke()
        downloadListener?.onTerminate(task)
        task.group?.let { group ->
            checkGroupStatus(group, event)
        }
    }

    private fun <T : AbstractKDownloadTask> onProgressEvent(task: AbstractKDownloadTask, event: DownloadEvent<T>?) {
        Debug.log("${task.name} - ${task.percentageProgress}%")
        event?.progress?.invoke()
        downloadListener?.onProgress(task)
    }

    private fun <T : AbstractKDownloadTask> onFinishEvent(task: AbstractKDownloadTask, event: DownloadEvent<T>?) {
        task.status = Status.FINISHED
        Debug.log("${task.name} - 下载完成")
        event?.finish?.invoke()
        downloadListener?.onFinish(task)
    }

    private fun <T : AbstractKDownloadTask> onPauseEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.PAUSED
        // 要把队列中的任务加回队列中（目前方案没有移出，依旧在队列中），todo 如果是直接异步启动的话呢？是否应该在业务中处理？
        Debug.log("${task.name} - 下载暂停")
        event?.pause?.invoke()
        downloadListener?.onPause(task)
    }

    /**
     * 启动事件
     */
    private fun <T : AbstractKDownloadTask> onStartEvent(task: T, event: DownloadEvent<T>?) {
        task.status = Status.CONNECTING
        Debug.log("download core - name: ${task.name} url: ${task.url} path: ${task.localPath}")
        // 如果业务需要二选一，可以再加个变量控制，走了自己的回调就不走全局回调
        //event?.start?.invoke() ?: downloadListener?.onStart(task)
        event?.start?.invoke()
        downloadListener?.onStart(task)
    }

    /**
     * 失败的事件，这里很复杂，能否重试完全依赖于是因为什么错误
     */
    private fun <T : AbstractKDownloadTask> onFailedEvent(task: T, e: Exception, event: DownloadEvent<T>?) {
        // failed()内部处理状态和重试，然后event完了刚好进到finally进行重试，这一系列流程是连贯的，修改时要注意！！
        if (task.failed(e)) {
            downloadListener?.onFail(task, e)
            event?.fail!!.invoke(e)
        }
    }

    private fun <T : AbstractKDownloadTask> onGroupFinishEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载完成")
        event?.groupFinish?.invoke()
        (downloadListener as? IGroupDownloadListener)?.onGroupFinish(group)
    }

    private fun <T : AbstractKDownloadTask> onGroupTerminateEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载结束")
        event?.groupTerminate?.invoke()
        (downloadListener as? IGroupDownloadListener)?.onGroupTerminate(group)
    }

    private fun <T : AbstractKDownloadTask> onGroupProgressEvent(group: TaskGroup, event: GroupDownloadEvent<T>?) {
        Debug.log("GroupId: ${group.groupId} - 整组下载进度: ${group.percentageProgress}%")
        event?.groupProgress?.invoke()
        (downloadListener as? IGroupDownloadListener)?.onGroupProgress(group)
    }

    /** 设为默认下载，没有反选，暂时想不到有反选的需求的场景 */
    fun default() {
        isDefault = true
        if (mDefaultKDownloader != this) {
            mDefaultKDownloader = this
        }
    }
}