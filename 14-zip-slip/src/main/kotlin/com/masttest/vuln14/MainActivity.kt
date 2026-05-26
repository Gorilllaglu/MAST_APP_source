package com.masttest.vuln14

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Имитируем случай: приложение скачало zip и распаковывает его
        // в свой приватный каталог. Чтобы статический сканер с
        // reachability-анализом видел unzip() как реально достижимую,
        // безусловно вызываем его — пустой/несуществующий файл просто
        // приведёт к exception, который мы глотаем.
        val downloaded = File(cacheDir, "downloaded.zip")
        val targetDir = File(filesDir, "unpacked")
        runCatching { ZipUtil.unzip(downloaded, targetDir) }
    }
}
