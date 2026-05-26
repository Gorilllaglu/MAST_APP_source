package com.masttest.vuln15

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Path Traversal через File I/O.
 *
 * Activity получает имя файла из Intent extras и читает его. Имя
 * не нормализуется (`canonicalPath`) и не проверяется по whitelist.
 *
 * Атака:
 *   val i = Intent().apply {
 *     component = ComponentName(
 *       "com.masttest.vuln15",
 *       "com.masttest.vuln15.FileReadActivity"
 *     )
 *     putExtra("filename", "../shared_prefs/secrets.xml")
 *   }
 *   startActivity(i)
 *
 * Безопасный паттерн:
 *   val baseDir = File(filesDir, "user_files").canonicalFile
 *   val target  = File(baseDir, name).canonicalFile
 *   require(target.path.startsWith(baseDir.path + File.separator))
 */
class FileReadActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val name = intent.getStringExtra("filename") ?: return
        // База — приватная папка filesDir/user_files. Идея безопасности:
        // ограничиться этой директорией. Реализована неправильно —
        // конкатенация путей без canonicalize.
        val base = File(filesDir, "user_files")
        val target = File(base, name)         // <-- VULN sink: name может содержать "../"
        if (target.exists()) {
            val text = target.readText()
            Log.w("FileRead", "Read ${target.absolutePath}: ${text.take(200)}")
            findViewById<TextView>(R.id.tvTitle).text = text.take(200)
        }
    }
}
