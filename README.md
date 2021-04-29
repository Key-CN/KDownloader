# 一个简单的、可靠的下载库
> 准备使用纯Kotlin实现，且尽量使用Jetpack全家桶，目的是：简单、可靠，而非是一个为了最快速度下载的库。在设计初期就没有考虑下载速度。

一个简陋的readme，目前处于实现初期

原本为了公司业务定制开发的，后来觉得可以抽出一部分公共的功能，满足后续其他项目一些简单的使用，或者可以简单的自定义扩展使用。



## 已实现功能

1. 核心下载功能
2. 启动、暂停
3. 任务栈，多任务同时执行
4. 同步调用
5. 异步回调（DSL？）
6. 多任务监听的模式
7.

## TODO LIST:

1. 断点续传，需要先确定第四个方案

2. 临时任务数据：sqlite 还是  DataStore？

3. 多线程（不影响上层业务，优先级最低）

   > 多线程，写成同步执行的话，用LiveData压缩成一个？

4. 临时配置文件BreakpointInfo的存储方案：待定

## Summary

* 只是下载其实几行代码就写完了，比如最核心的，把body输出到文件

  ```kotlin
  BufferedInputStream(body.byteStream(), DEFAULT_BUFFER_SIZE).use { bis ->
      val byteArray = ByteArray(DEFAULT_BUFFER_SIZE)
      var readLength: Int
      while (bis.read(byteArray).also { readLength = it } != -1) {
          writeTempFile.write(byteArray, 0, readLength)
      }
  }
  ```

* 但是要把下载使用在业务里，场景太多了，需要考虑的也太多了，而且我发现，我尽量的去设想各种各样的业务，但是似乎没有办法使用一个类来完成，因为很多功能是互斥的。所以我想尽量设计成高扩展性的。同时使用起来不要太复杂，需要重写或者继承或者实现太多自定义的东西，尽量把调用做到最简单。
* 设计的中心思想是下载的核心逻辑只处理下载，所有的问题都应该是业务层的问题。所以错误都要抛到业务层为止。逻辑层只记录状态
* 采用Object类也是这种为了简单的原因，Object类是懒加载，所以只要关心你最终使用（实现）的那一个即可，虽然可能重写出很多个，但并不会被加载进内存
* 准备在下一次修改时去掉ID字段，移动端业务不像前端业务，不需要持有ID，然后根据ID去查询，移动端其实一直持有着对象，所以ID字段没有意义，下次增加缓存时确认，如果缓存也不需要ID，则去掉


## 使用文档

* 队列下载方式

  ```kotlin
  LifecycleKDownloader.addTaskAndStart(task1)
  LifecycleKDownloader.addTask(task2)
  LifecycleKDownloader.startTaskQueue()
  ```

* 同步下载

  ```kotlin
  lifecycleScope.launch(Dispatchers.IO) {
      try {
          LifecycleKDownloader.syncDownloadTask(task1)
          // 下载完成的处理，成功执行之后一定存在name
          val file = File(task1.localPath, task1.name!!)
          file.delete()
      } catch (e: Exception) {
          e.printStackTrace()
          // 错误处理
      }
  }
  ```

* 异步下载

  ```kotlin
  lifecycleScope.download(task2) {
      onTerminate {
          println("download - onTerminate")
      }
      onStart {
          println("download - onStart, ${Thread.currentThread().name}")
      }
      onFail {
          println("download - onFail, e=$it")
      }
      onPause {
          println("download - onPause")
      }
      onProgress {
          println("download - onProgress - ${task.percentageProgress}")
      }
      onFinish {
          println("download - onFinish")
      }
  }
  ```

## 开发中的一些自我矛盾点以及最后的解决方式

> 一开始都在注释中，最终定稿的时候删除注释，贴出来，后期集思广益，也许有更好的解决方案

1. 因为设计来优先级这个概念，所以在单任务暂停的时候，出现了一个问题，队列中优先级最高的任务，被手动暂停后，再次开启队列（获取优先级最高的任务）还是这个任务？所以这个优先级的原则应该是要不要排除手动暂停的方式呢？这样的话难道优先级最高的反而要去队尾？这不科学
   - 所以最终我决定去掉单任务暂停，单个任务只留下取消的操作，暂停只能全部暂停
2. 这个`DownloadTask`每一次继承都要写很多构造参数，太不方便了。改
3. 整组的概念定义：怎么样算整组完成，状态需要全部finished还是包括了failed

