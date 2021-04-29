package io.keyss.kdownloader.ui.main

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.jokar.permission.PermissionUtil
import io.keyss.kdownloader.GoodCourseDownloadTask
import io.keyss.kdownloader.GroupDownloadListenerImpl
import io.keyss.kdownloader.R
import io.keyss.kdownloader.ResourceType
import io.keyss.library.kdownloader.LifecycleKDownloader
import io.keyss.library.kdownloader.core.IDownloadListener
import io.keyss.library.kdownloader.core.MyTaskGroup
import io.keyss.library.kdownloader.utils.download
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    // length: 4372373
    val url1 = "https://media.w3.org/2010/05/sintel/trailer.mp4"

    // length: 68936214，这个网差，可以测试超时
    val url2 = "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4"

    // length: 20289333
    val url3 = "https://dqunying1.jb51.net/201209/tools/Robotica_1080.wmv"

    // fixme 无法读写，待查
    val localPath1 = "/mnt/media_rw/40C9-180F/test/"
    val localPath2 = "/sdcard/test/"

    val task1 = GoodCourseDownloadTask(1, url1, localPath2, null, true, 95, ResourceType.LAUNCHER_APK, 188)
    val task2 = GoodCourseDownloadTask(2, url2, localPath2, null, true, 922, ResourceType.LAUNCHER_APK, 123)
    val task3 = GoodCourseDownloadTask(3, url3, localPath2, null, true, 96, ResourceType.LAUNCHER_APK, 22)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        val tvTips = view.findViewById<AppCompatTextView>(R.id.tv_tips)
        PermissionUtil.Builder(this)
            .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .setDenied {
                //Toast.makeText(this, "存储授权被拒绝", Toast.LENGTH_SHORT).show()
                tvTips.text = "存储授权被拒绝"
            }
            .setGrant {
                //Toast.makeText(this, "存储授权通过", Toast.LENGTH_SHORT).show()
                tvTips.text = "存储授权通过"
            }
            .setNeverAskAgain {
                //Toast.makeText(this, "存储授权被永久拒绝", Toast.LENGTH_SHORT).show()
                tvTips.text = "存储授权被永久拒绝"
            }
            .request()

        val listener: IDownloadListener = GroupDownloadListenerImpl
        LifecycleKDownloader.lifecycleOwner = this
        LifecycleKDownloader.downloadListener = listener


        view.findViewById<AppCompatButton>(R.id.b_start_download).setOnClickListener {
            LifecycleKDownloader.startTaskQueue()
        }
        view.findViewById<AppCompatButton>(R.id.b_pause).setOnClickListener {
            // 全部暂停
            LifecycleKDownloader.stopTaskQueue()
        }
        view.findViewById<AppCompatButton>(R.id.b_pause_one).setOnClickListener {
            //task2.pause()
        }
        view.findViewById<AppCompatButton>(R.id.b_cancel).setOnClickListener {
            task2.cancel()
        }
        lifecycleScope.launchWhenResumed {
//            doDownload()
        }
        //callback()
        lifecycleScope.launch(Dispatchers.IO) {
            startGroup()
        }
    }

    fun startGroup(): Unit {
        //LifecycleKDownloader.createTaskGroupAndStart(listOf(task1, task2, task3))
        val myTaskGroup = MyTaskGroup(123, listOf(task1, task2, task3), "我是一个组")
        LifecycleKDownloader.addTaskGroupAndStart(myTaskGroup)
    }

    private fun doDownload() {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1_000)
            //LifecycleKDownloader.syncDownloadTask(task1)
            //LifecycleKDownloader.addTaskAndStart(task1)
            //LifecycleKDownloader.addTaskAndStart(task2)
            /*LifecycleKDownloader.addTask(task1)
            LifecycleKDownloader.addTask(task2)
            LifecycleKDownloader.addTask(task3)
            task2.priority = 999
            LifecycleKDownloader.startTaskQueue()*/
        }
    }

    /**
     * 回调的方式调用下载
     */
    private fun callback() {
        lifecycleScope.download(task2) {
            onTerminate {
                println("download - onComplete")
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
                println("download - onProgress - ${(task as GoodCourseDownloadTask).resourceId} - ${task.percentageProgress}%")
            }
            onFinish {
                println("download - onFinish")
            }
        }
    }
}