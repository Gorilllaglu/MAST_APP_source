package com.masttest.vuln36

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Один touch-call на каждый sensitive Android API.
        // Все вызовы внутри ApiInventory обёрнуты в runCatching, поэтому
        // отсутствие permission или железа не валит app — сканеру важно
        // лишь увидеть signatures в DEX.
        ApiInventory.touchAll(this)
    }
}
