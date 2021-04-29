package io.keyss.library.kdownloader.core

/**
 * @author Key
 * Time: 2021/04/29 16:52
 * Description:
 */
class MyTaskGroup(groupId: Int, tasks: Collection<AbstractKDownloadTask>, val markName:String) : TaskGroup(groupId, tasks){

}
