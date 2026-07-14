package com.ccep.swiftpdf

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Needed once so the search engine (PDFBox) can load its fonts/resources
        PDFBoxResourceLoader.init(applicationContext)
    }
}
