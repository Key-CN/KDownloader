package io.keyss.library.kdownloader.bean

import io.keyss.library.kdownloader.core.AbstractDownloadTaskImpl

/**
 * @author Key
 * Time: 2021/04/21 22:39
 * Description:
 */
class DefaultTaskInfo(id: Int, url: String, localPath: String, name: String? = null, isDeleteExist: Boolean = true) :
    AbstractDownloadTaskImpl(id, url, localPath, name, isDeleteExist)