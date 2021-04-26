package io.keyss.library.kdownloader.config

import java.io.IOException

/**
 * @author Key
 * Time: 2021/04/26 14:29
 * Description: 空间不足, [freeSpace]为0的时候大概率是硬盘读不到，需要单独处理
 */
class StorageInsufficientException(message: String?, val freeSpace: Long, val requiredSpace: Long) :
    IOException("$message, freeSpace=$freeSpace, fileLength=$requiredSpace")