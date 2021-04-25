package io.keyss.library.kdownloader

import io.keyss.library.kdownloader.utils.Debug
import io.keyss.library.kdownloader.utils.MD5Util
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class LogUnitTest {

    @Test
    fun logTest1() {
        repeat(10000) {
            // 空跑预热
            val a = Math.PI * Math.PI
        }
        /*Debug.log = {
            println(it)
        }*/
        Debug.isDebug = true
        val arrayListOf = arrayListOf<Long>()

        repeat(10) {
            val currentTimeMillis = System.currentTimeMillis()
            repeat(100000) {
                // 10000 耗时=[70, 59, 37, 37, 35, 34, 34, 35, 54, 35]ms
                // 100000 耗时=[404, 335, 423, 473, 385, 332, 330, 382, 342, 376]ms
                // 耗时=[408, 412, 495, 395, 331, 329, 350, 388, 415, 390]ms, avg=391.3
                Debug.log("repeat=$it")
                // 10000 61ms 76ms 74ms 耗时=[83, 42, 37, 38, 35, 33, 32, 34, 52, 41]ms
                // 100000 耗时=[421, 420, 465, 367, 331, 327, 360, 399, 431, 366]ms
                // 耗时=[444, 437, 600, 446, 422, 447, 396, 416, 388, 356]ms, avg=435.2
                //Debug.testLog("repeat=$it")
            }
            arrayListOf.add(System.currentTimeMillis() - currentTimeMillis)
        }

        println("耗时=${arrayListOf}ms, avg=${arrayListOf.average()}")
    }
}