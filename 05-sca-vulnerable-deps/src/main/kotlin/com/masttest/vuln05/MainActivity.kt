package com.masttest.vuln05

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Touch на каждую уязвимую библиотеку — чтобы её классы реально
        // попали в DEX/APK (без вызова R8/DCE может выкинуть unused libs).
        VulnerableLibsTouch.touchAll()
    }
}
