package io.keyss.library.kdownloader

import io.keyss.library.kdownloader.core.AbstractDownloadTaskImpl
import io.keyss.library.kdownloader.utils.MD5Util
import org.junit.Test

import org.junit.Assert.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun md5ValueTest() {
        val stringToMD5 = MD5Util.stringToMD5("abc")
        println(stringToMD5)
        // 900150983cd24fb0d6963f7d28e17f72 正确
    }

    @Test
    fun listTest() {
        val tasks: CopyOnWriteArrayList<Int> = CopyOnWriteArrayList()
        repeat(10) {
            tasks.add(it)
        }
        println(tasks)
        tasks.removeIf { it % 2 == 0 }
        println(tasks)

    }
}