package io.keyss.library.kdownloader.utils

/**
 * @author Key
 * Time: 2021/05/13 15:52
 * Description: 类似于go中的defer的功能
 */
class Deferrer {
    private val actions = mutableListOf<() -> Unit>()

    fun defer(f: () -> Unit) {
        actions.add(f)
    }

    fun done() {
        actions.reverse()
        for (action in actions) {
            action()
        }
    }
}

inline fun <T> withDefers(body: Deferrer.() -> T): T {
    val deferrer = Deferrer()
    val result = deferrer.body()
    deferrer.done()
    return result
}