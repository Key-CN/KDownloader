package io.keyss.library.kdownloader.core

import java.util.concurrent.CopyOnWriteArrayList

/**
 * @author Key
 * Time: 2021/05/18 17:29
 * Description:
 * @param sizeObserver 内容发生变化的通知
 */
class TaskList<T>(private val sizeObserver: (Int) -> Unit) : CopyOnWriteArrayList<T>() {
    override fun addIfAbsent(element: T): Boolean {
        val isAdded = super.addIfAbsent(element)
        if (isAdded) {
            sizeObserver.invoke(size)
        }
        return isAdded
    }

    override fun remove(element: T?): Boolean {
        val isRemoved = super.remove(element)
        if (isRemoved) {
            sizeObserver.invoke(size)
        }
        return isRemoved
    }

    override fun addAll(elements: Collection<T>): Boolean {
        val isAddAll = super.addAll(elements)
        if (isAddAll) {
            sizeObserver.invoke(size)
        }
        return isAddAll
    }

    /** 只添加不存在的 */
    override fun addAllAbsent(c: MutableCollection<out T>): Int {
        val addAllAbsent = super.addAllAbsent(c)
        sizeObserver.invoke(size)
        return addAllAbsent
    }
}