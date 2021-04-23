package io.keyss.library.kdownloader

import android.os.Looper
import io.keyss.library.kdownloader.bean.AbstractDownloadTaskImpl
import io.keyss.library.kdownloader.config.KDownloadException
import io.keyss.library.kdownloader.config.PersistenceType
import io.keyss.library.kdownloader.config.Status
import io.keyss.library.kdownloader.utils.MD5Util
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.closeQuietly
import okhttp3.internal.headersContentLength
import okio.IOException
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Key
 * Time: 2021/04/21 22:38
 * Description:
 */
abstract class AbstractKDownloader(taskPersistenceType: @PersistenceType Int = PersistenceType.MEMORY_CACHE) {
    companion object {
        private const val TAG = "KDownloader"
        private const val TEMP_FILE_SUFFIX = ".kd"
        private const val CONFIG_SUFFIX = ".cfg"
        const val MIN_BLOCK_LENGTH = 1024 * 1024 * 1024
    }

    private var mTaskPersistenceType: @PersistenceType Int = taskPersistenceType

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


    var maxConnections = 5
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

    fun startTaskQueue(): Unit {

    }

    fun stopTaskQueue(): Unit {

    }

    /**
     * 添加一个下载任务，不启动
     */
    fun <T : AbstractDownloadTaskImpl> addTask(task: T): Unit {

    }

    /**
     * 添加一个下载任务，并启动
     */
    fun <T : AbstractDownloadTaskImpl> addTaskAndStart(task: T): Unit {

    }

    suspend fun <T : AbstractDownloadTaskImpl> syncDownloadTask(task: T) {
        download(task)
    }

    /**
     * 下载核心逻辑
     * 请在子线程执行
     */
    @Throws(Exception::class)
    private fun <T : AbstractDownloadTaskImpl> download(task: T) {
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
        isFreeSpaceEnough(storageFolder, task.fileLength)

        // 验证文件名
        if (task.name.isNullOrBlank()) {
            task.name = task.url.takeLastWhile { it != '/' }
        }
        if (task.name.isNullOrBlank()) {
            // 要保证每个相同的URL转出来的是一样的，所以不能使用random的做法
            //task.name = UUID.randomUUID().toString()
            task.name = MD5Util.stringToMD5(task.url)
        }
        println("task.name=${task.name}")
        val file = File(storageFolder, task.name!!)
        // 先检测本地文件
        if (file.exists()) {
            if (task.isDeleteExist) {
                if (!file.delete()) {
                    throw KDownloadException("删除同名文件失败")
                }
                println("${task.name} - 存在同名文件已删除")
            } else {
                throw KDownloadException("下载存在同名文件，且未设定替换")
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
            // todo 相同则需要seek
        }
        val writeTempFile = RandomAccessFile(tempFile, "rw")
        writeTempFile.setLength(task.fileLength)
        // 需要校验写了多少了
        /*if (task.maxConnections == 1) {
            val getRequest = headRequest.newBuilder().get().build()
        }*/
        // 一条线程下载
        val getRequest = headRequest.newBuilder().get().build()
        val responseByGet = mClient.newCall(getRequest).execute()
        // 前面head已校验，此处不再多余校验，算了，我觉得万一有个万一呢，来个一次性校验吧
        val body = responseByGet.body
        if (!responseByGet.isSuccessful || responseByGet.headersContentLength() <= 0 || body == null) {
            throw KDownloadException("GET请求错误, HTTP status code=${responseByHead.code}, HTTP status message=${responseByHead.message}")
        }
        BufferedInputStream(body.byteStream()).use { bis ->
            val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
            var readLength: Int
            while (bis.read(byteArray).also { readLength = it } != -1) {
                writeTempFile.write(byteArray, 0, readLength)
                task.downloadedLength += readLength
                task.updatePercentageProgress()
                println("${task.name} - ${task.percentageProgress}")
            }
        }
        task.status = Status.FINISHED
        if (!tempFile.renameTo(file)) {
            throw KDownloadException("下载完成，临时文件重命名失败")
        }
        println("${task.name}下载完成")

        // 流关闭失败是否会导致文件不完整？
        writeTempFile.closeQuietly()

    }

    /**
     * 检测空间是否足够，没问题就过，有问题直接抛，方便
     */
    @Throws(IOException::class)
    private fun isFreeSpaceEnough(storageFolder: File, fileLength: Long) {
        var freeSpace = storageFolder.freeSpace
        if (freeSpace == 0L) {
            freeSpace = storageFolder.freeSpace
        }
        if (freeSpace == 0L) {
            throw IOException("freeSpace为0")
        }
        if (freeSpace < fileLength) {
            throw IOException("可用空间不足，freeSpace=$freeSpace, fileLength=$fileLength")
        }
    }
}