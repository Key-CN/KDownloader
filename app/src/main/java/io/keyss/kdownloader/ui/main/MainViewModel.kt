package io.keyss.kdownloader.ui.main

import androidx.lifecycle.ViewModel
import io.keyss.library.kdownloader.AbstractKDownloader

class MainViewModel : ViewModel() {
    fun getKDownloaderVersion(): String = "123"
}