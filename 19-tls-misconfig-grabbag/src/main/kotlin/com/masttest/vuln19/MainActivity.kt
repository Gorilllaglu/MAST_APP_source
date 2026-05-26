package com.masttest.vuln19

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Просто упоминаем, чтобы R8 не выкинул
        Log.d("Vuln19", "trustAll=${TlsClients.trustAllClient()}, plain=${TlsClients.noPinningClient()}, weak=${TlsClients.hostnameOnlyPinningClient()}")
    }
}
