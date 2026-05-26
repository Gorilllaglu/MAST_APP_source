package com.masttest.vuln37

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Все 4 категории key-material'а реально загружаются — чтобы у
        // сканера с reachability/data-flow анализом не было повода
        // считать их «мёртвым» ресурсом.
        KeyMaterialUsage.loadAll(this)
    }
}
