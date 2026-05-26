package com.masttest.vuln17

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Дёргаем все методы CryptoUtil, чтобы R8 их не выкинул
        // (важно для статического анализа release-билда).
        Log.d("Vuln17", "ecb=${CryptoUtil.aesEcb("hello").size}")
        Log.d("Vuln17", "iv =${CryptoUtil.aesWithStaticIv("hello").size}")
        Log.d("Vuln17", "des=${CryptoUtil.weakDes("hello").size}")
        Log.d("Vuln17", "md5=${CryptoUtil.md5Hash("password")}")
        Log.d("Vuln17", "sha1=${CryptoUtil.sha1Hash("password")}")
        Log.d("Vuln17", "pbkdf=${CryptoUtil.weakPbkdf("password").size}")
    }
}
