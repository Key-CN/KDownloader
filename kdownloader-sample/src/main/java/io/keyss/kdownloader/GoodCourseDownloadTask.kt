package io.keyss.kdownloader

import io.keyss.library.kdownloader.core.AbstractDownloadTaskImpl

/**
 * @author Key
 * Time: 2021/04/25 19:44
 * Description:
 */
class GoodCourseDownloadTask(
    id: Int, url: String, localPath: String, name: String? = null, isDeleteExist: Boolean = true,
    val resourceId: Int,
    val resourceType: @ResourceType Int,
    val version: Int,
) :
    AbstractDownloadTaskImpl(id, url, localPath, name, isDeleteExist) {

}