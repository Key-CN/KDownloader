package io.keyss.library.kdownloader.config

import androidx.annotation.IntDef

/**
 * @author Key
 * Time: 2021/04/22 16:55
 * Description:
 */
@Target(AnnotationTarget.TYPE)
@IntDef
annotation class PersistenceType() {
    companion object {
        const val MEMORY_CACHE = 0
        const val DATABASE = 1
    }
}
