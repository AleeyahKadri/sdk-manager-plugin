package com.jakewharton.sdkmanager.internal

import java.io.File

interface Downloader {
    fun download(dest: File)

    class Real : Downloader {
        override fun download(dest: File) {
            SdkDownload.get().download(dest)
        }
    }
}
