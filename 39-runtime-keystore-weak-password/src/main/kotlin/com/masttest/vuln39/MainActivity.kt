package com.masttest.vuln39

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Все 5 сценариев реально вызываются — call-site остаётся в DEX,
        // даже если runtime'но keystore.store упадёт (нет BKS-провайдера
        // на устройстве, нет прав, и т.д.).
        KeystoreFileOps.runAll(this)
    }
}
