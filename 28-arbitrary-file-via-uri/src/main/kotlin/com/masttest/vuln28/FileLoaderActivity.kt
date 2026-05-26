package com.masttest.vuln28

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.FileInputStream

/**
 * VULN-28: Activity получает Uri от вызывающего и через ContentResolver
 * (или прямым FileInputStream) читает по нему данные. Никакой проверки
 * scheme/host/path нет.
 *
 * Атака:
 *
 *   val payload = Uri.parse("content://com.masttest.vuln27.unsafe/../shared_prefs/secrets.xml")
 *   val i = Intent().apply {
 *     component = ComponentName(
 *       "com.masttest.vuln28",
 *       "com.masttest.vuln28.FileLoaderActivity"
 *     )
 *     putExtra("file_uri", payload)
 *   }
 *   startActivity(i)
 *
 * — наш FileLoaderActivity откроет content://-uri и прочитает
 * приватный файл стороннего приложения с нужными permission'ами.
 */
class FileLoaderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) Через getParcelableExtra<Uri> — content:// URI
        @Suppress("DEPRECATION")
        val uri = intent.getParcelableExtra<Uri>("file_uri")        // <-- VULN: source
        if (uri != null) {
            // <-- VULN sink: ContentResolver открывает любой URI
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            Log.w("FileLoader", "Read ${bytes?.size} bytes from $uri")
            findViewById<TextView>(R.id.tvTitle).text = "len=${bytes?.size}"
        }

        // (2) Через getStringExtra("path") — прямой FileInputStream
        val path = intent.getStringExtra("path")                     // <-- VULN: source
        if (path != null) {
            // <-- VULN sink: FileInputStream(String) без canonicalize
            val data = FileInputStream(path).use { it.readBytes() }
            Log.w("FileLoader", "Read ${data.size} bytes from path=$path")
        }
    }
}
