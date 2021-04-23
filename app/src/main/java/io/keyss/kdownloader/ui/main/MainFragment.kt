package io.keyss.kdownloader.ui.main

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.jokar.permission.PermissionUtil
import io.keyss.kdownloader.R
import io.keyss.library.kdownloader.LifecycleKDownloader
import io.keyss.library.kdownloader.bean.DefaultTaskInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        view?.findViewById<TextView>(R.id.message)?.text = viewModel.getKDownloaderVersion()
        val url1 = "https://media.w3.org/2010/05/sintel/trailer.mp4"
        // length: 251078905
        val url2 = "http://mirror.aarnet.edu.au/pub/TED-talks/911Mothers_2010W-480p.mp4"

        val localPath1 = "/mnt/media_rw/40C9-180F/test/"
        val localPath2 = "/sdcard/test/"

        PermissionUtil.Builder(this)
            .setPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
            .setDenied {
                Toast.makeText(requireContext(), "Denied_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show()
            }
            .setGrant {
                Toast.makeText(requireContext(), "grant_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch(Dispatchers.IO) {
                    LifecycleKDownloader.syncDownloadTask(DefaultTaskInfo(1, url2, localPath2))
                }
            }
            .setNeverAskAgain {
                Toast.makeText(requireContext(), "NeverAskAgain_EXTERNAL_STORAGE", Toast.LENGTH_SHORT).show()
            }
            .request()
    }
}