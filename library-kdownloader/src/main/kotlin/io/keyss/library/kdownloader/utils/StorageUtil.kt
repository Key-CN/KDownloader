package io.keyss.library.kdownloader.utils

import android.os.StatFs
import android.util.Log
import io.keyss.library.kdownloader.config.StorageInsufficientException
import java.io.File

/**
 * @author Key
 * Time: 2021/04/27 19:19
 * Description:
 */
object StorageUtil {

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun getAvailableBytes(path: File): Long {
        // 文件系统上可用的字节数，包括保留的块（普通应用程序不可用）。大多数应用程序将改为使用getAvailableBytes（）。
        val statFs = StatFs(path.absolutePath)
        var freeSpace = statFs.availableBytes
        // val statFs = StatFs(storageFolder.absolutePath)
        // File读取的freeSpace=12350640128, StatFs读取的freeBytes=12350640128, availableBytes=12199645184
        // freeSpace = freeBytes，都要大于availableBytes 扩展：available < (free + cache + buffers)
        //Debug.log("File读取的freeSpace=$freeSpace, StatFs读取的freeBytes=${statFs.freeBytes}, availableBytes=${statFs.availableBytes}")
        if (freeSpace == 0L) {
            freeSpace = path.usableSpace
            Debug.log("StatFs获取失败，改用File的usableSpace获取")
        }
        return freeSpace
    }

    /**
     * 检测空间是否足够，没问题就过，有问题直接抛，方便 todo 最后抽到Util类中
     */
    @Throws(Exception::class)
    @JvmStatic
    fun isStorageEnough(path: File, fileLength: Long) {
        val freeSpace = getAvailableBytes(path)
        if (freeSpace == 0L) {
            throw StorageInsufficientException("可用空间为0", freeSpace, fileLength)
        }
        // 加上=，防止出现 0 < 0的情况
        if (freeSpace <= fileLength) {
            throw StorageInsufficientException("可用空间不足", freeSpace, fileLength)
        }
    }

    fun isStorageEnough2(path: File, fileLength: Long): Boolean {
        return try {
            isStorageEnough(path, fileLength)
            true
        } catch (e: Exception) {
            //e.printStackTrace()
            // 改用w级别输出
            Log.w("可用空间不足", e)
            false
        }
    }

    fun isStorageEnough3(path: File, fileLength: Long): Boolean {
        val statFs = StatFs(path.absolutePath)
        var freeSpace = statFs.availableBytes
        if (freeSpace == 0L) {
            freeSpace = path.usableSpace
            Debug.log("StatFs获取失败，改用File的usableSpace获取")
        }
        return freeSpace > fileLength
    }
}