package io.keyss.library.kdownloader.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Key
 * Time: 2022/04/13 13:52
 * Description: 历史任务，内存
 */
class MemoryCacheHistoryTask {
    private val tasks = ConcurrentHashMap<Int, AbstractKDownloadTask>()

    // 内存的话每次启动都会从0开始
    private var mIdCounter: AtomicInteger = AtomicInteger(0)

    private fun generateId(): Int {
        return mIdCounter.getAndIncrement()
    }

    fun getLastTaskID(): Int {
        return mIdCounter.get() - 1
    }

    fun getLastTask(): AbstractKDownloadTask? {
        // ConcurrentHashMap是无序的，不能这样用
        //return tasks.values.lastOrNull()
        val id = getLastTaskID()
        if (id < 0) {
            return null
        }
        return get(id)
    }

    /**
     * 如果相同则不存，但是如果值相同，类型不同呢？类型不同如果存储地址相同则也应视为相同，所以不用区分任务类型，只需要判断内部值即可
     */
    fun add(task: AbstractKDownloadTask): Boolean {
        if (task._id != null) {
            // 已存在
            return false
        }
        val taskID = generateId()
        task._id = taskID
        return try {
            tasks.put(taskID, task) == task
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun get(id: Int): AbstractKDownloadTask? {
        return try {
            tasks[id]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 范型的转换不应影响搜索到到值，所以类型转换应放到外部业务处理的地方
     */
    fun find(task: AbstractKDownloadTask): AbstractKDownloadTask? {
        return if (task._id != null) {
            get(task._id!!)
        } else {
            tasks.firstNotNullOfOrNull { entry ->
                entry.value.takeIf { it == task }
            }
        }
    }

    fun delete(id: Int): AbstractKDownloadTask? {
        return tasks.remove(id)
    }
}