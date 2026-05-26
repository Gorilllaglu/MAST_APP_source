package com.masttest.vuln38

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Каждый из weak-чеков реально вызывается из onCreate, чтобы
        // у сканера с reachability-анализом call-site был очевиден.
        val report = WeakChecks.runAll(this)
        Log.d("Vuln38", "weak checks report: $report")
    }
}
