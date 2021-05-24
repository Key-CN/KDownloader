package io.keyss.library.kdownloader.utils

/**
 * @author Key
 * Time: 2021/05/13 15:52
 * Description: 类似于go中的defer的功能
 */
class Deferrer<T> {
    private val actions = mutableListOf<(T?) -> Unit>()

    fun defer(f: (T?) -> Unit) {
        actions.add(f)
    }

    fun done(block1: T?) {
        actions.reverse()
        for (action in actions) {
            action(block1)
        }
    }
}

/**
 * 可能会抛异常，抛了就不会走deferrer.done(result)
 */
inline fun <T> withDefers(body: Deferrer<T>.() -> T): T {
    val deferrer = Deferrer<T>()
    val result = deferrer.body()
    deferrer.done(result)
    return result
}

/**
 * 结尾，还可以拿到返回值再次处理
 */
@Throws(Exception::class)
inline fun <T> withDefersFinally(block: Deferrer<T>.() -> T): T {
    val deferrer = Deferrer<T>()
    var result: T? = null
    try {
        result = deferrer.block()
        return result
    } catch (e: Exception) {
        throw e
    } finally {
        // 抛出异常则为null
        deferrer.done(result)
    }
}

/**
 * 安全，不抛
 */
inline fun <T> withDefersSafely(block: Deferrer<T>.() -> T): T? {
    val deferrer = Deferrer<T>()
    val result: T? = try {
        deferrer.block()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
    deferrer.done(result)
    return result
}