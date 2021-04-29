package io.keyss.library.kdownloader.bean

import io.keyss.library.kdownloader.core.AbstractKDownloadTask

/**
 * @author Key
 * Time: 2021/04/21 22:39
 * Description:
 */
class DefaultDownloadTask(id: Int, url: String, localPath: String, name: String? = null, isDeleteExist: Boolean = true) :
    AbstractKDownloadTask(id, url, localPath, name, isDeleteExist)