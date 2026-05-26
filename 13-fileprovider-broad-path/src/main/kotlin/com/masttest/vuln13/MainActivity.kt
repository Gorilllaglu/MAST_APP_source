package com.masttest.vuln13

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

/**
 * Шарит файл наружу через FileProvider. Сам этот код — обычный паттерн.
 * Реальная уязвимость в res/xml/file_paths.xml, который заявляет слишком
 * широкие корни.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Создаём какой-нибудь файл и шарим его через FileProvider.
        val f = File(filesDir, "report.txt").apply {
            writeText("hello, this is a shared report")
        }
        val uri: Uri = FileProvider.getUriForFile(
            this,
            "com.masttest.vuln13.fileprovider",
            f
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Действительно дёргаем chooser, чтобы у сканера с
        // reachability-анализом был полный data-flow до startActivity.
        // runCatching глотает ActivityNotFound на устройствах без
        // обработчиков ACTION_SEND.
        runCatching { startActivity(Intent.createChooser(share, "Share")) }
    }
}
