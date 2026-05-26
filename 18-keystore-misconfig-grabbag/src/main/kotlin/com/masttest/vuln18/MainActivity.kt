package com.masttest.vuln18

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Реально вызываем все три проблемных генератора, чтобы у сканера
        // с reachability-анализом был полный call graph до уязвимостей.
        // try/catch вокруг — на устройствах без TEE/StrongBox AndroidKeyStore
        // может бросить exception (`java.security.KeyStoreException`), что
        // нам в тесте не нужно.
        runCatching { KeyManager.generateKeyWithoutAuth("alias-noauth") }
        runCatching { KeyManager.generateKeyWithoutHwBackingCheck("alias-nohwcheck") }
        runCatching { KeyManager.generateKeyWithoutBiometricInvalidation("alias-nobio") }
    }
}
