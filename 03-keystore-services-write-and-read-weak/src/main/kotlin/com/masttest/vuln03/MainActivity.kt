package com.masttest.vuln03

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        runCatching { startService(Intent(this, HardwareKeyStoreWriteService::class.java)) }
        runCatching { startService(Intent(this, KeyChainReadablePrivateWeakService::class.java)) }
        runCatching { startService(Intent(this, HardwareKeyStoreReadablePrivateWeakService::class.java)) }
    }
}
