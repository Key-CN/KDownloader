package io.keyss.library.kdownloader

import io.keyss.library.kdownloader.config.StorageInsufficientException
import io.keyss.library.kdownloader.utils.MD5Util
import org.junit.Assert.assertEquals
import org.junit.Test
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

    @Test
    fun hashTest() {
        val e = StorageInsufficientException("可用空间为0", 0, 10000)
        var b =
            """
                                    * 受影响教室：1231231231
                                    * 设备号：12312312313
                                    * 环境：123123123
                                    * 版本：123123123(12312313)
                                    * 文件名：1231231
                                    * URL：123123
                                    * 错误信息：
                                        ```${e.message}```
                                    """.trimIndent()
        var b2 =
            """
                                    * 受影响教室：1231231231
                                    * 设备号：12312312313
                                    * 环境：123123123
                                    * 版本：123123123(12312313)
                                    * 文件名：1231231
                                    * URL：123123
                                    * 错误信息：
                                        ```${e.message}```
                                    """.trimIndent()
        var b3 = b

        b = MD5Util.stringToMD5(b)
        b2 = MD5Util.stringToMD5(b2)
        b3 = MD5Util.stringToMD5(b3)
        // 相同的字符串，hash相等
        println("d388a72acfff969928491a75a3aec8f6".hashCode())
        println(b.hashCode())
        println(b3.hashCode())
        println(b2.hashCode())
    }


    @Test
    fun castTest() {
    }
}