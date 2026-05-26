package com.masttest.vuln07

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Сам NavHostFragment объявлен в layout/activity_main.xml,
        // он автоматически читает nav_graph.xml и handles deeplinks
        // из incoming Intent.
    }
}
