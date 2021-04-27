package io.keyss.kdownloader

import androidx.annotation.IntDef

/**
 * @author Key
 * Time: 2021/04/22 15:22
 * Description:
 */
@Target(AnnotationTarget.TYPE)
@IntDef
annotation class ResourceType() {
    companion object{
        const val LAUNCHER_APK = 0
        const val CLASSROOM_APK = 1
        const val LAYA_RESOURCE_ZIP = 3
    }
}
