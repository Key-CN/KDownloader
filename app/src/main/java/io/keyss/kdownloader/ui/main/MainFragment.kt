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
import io.keyss.kdownloader.R
import io.keyss.kdownloader.ResourceType
import io.keyss.library.kdownloader.LifecycleKDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    // length: 4372373
    val url1 = "https://media.w3.org/2010/05/sintel/trailer.mp4"

    // length: 68936214
    val url2 = "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4"
    // length: 20289333
    val url3 = "https://dqunying1.jb51.net/201209/tools/Robotica_1080.wmv"

    // fixme 无法读写，待查
    val localPath1 = "/mnt/media_rw/40C9-180F/test/"
    val localPath2 = "/sdcard/test/"

    val task1 = GoodCourseDownloadTask(1, url1, localPath2, null, true, 95, ResourceType.COURSEWARE_FILE, 188)
    val task2 = GoodCourseDownloadTask(2, url3, localPath2, null, true, 96, ResourceType.COURSEWARE_FILE, 22)


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
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

        val startButton = view.findViewById<AppCompatButton>(R.id.b_start_download)
        startButton.setOnClickListener {
            doDownload()

        }
        view.findViewById<AppCompatButton>(R.id.b_pause).setOnClickListener {
            //task.pause()
        }
        view.findViewById<AppCompatButton>(R.id.b_cancel).setOnClickListener {
            //task.cancel()
        }
        lifecycleScope.launchWhenResumed {
            startButton.performClick()
            //startButton.callOnClick()
        }
    }

    private fun doDownload() {
        lifecycleScope.launch(Dispatchers.IO) {
            //LifecycleKDownloader.syncDownloadTask(task1)
            //LifecycleKDownloader.addTaskAndStart(task1)
            //LifecycleKDownloader.addTaskAndStart(task2)
            LifecycleKDownloader.addTask(task1)
            LifecycleKDownloader.addTask(task2)
            task2.priority = 999
            LifecycleKDownloader.startTaskQueue()
        }
    }
}