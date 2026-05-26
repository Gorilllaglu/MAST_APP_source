package com.masttest.vuln10

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Все уязвимости этого app живут в AndroidManifest.xml.
 * Сам код Activity намеренно тривиален — нужен только чтобы APK собрался
 * и был запускаем.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
