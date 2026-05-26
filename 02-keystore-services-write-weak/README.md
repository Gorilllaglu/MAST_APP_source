# 02 — Два keystore-сервиса (HW Keystore weak pwd + KeyChain)

## APK после сборки

```bash
./gradlew :micro:02-keystore-services-write-weak:assembleDebug
```

## Что заложено в приложение

Два `Service` в одном APK, каждый со своим хранилищем ключей и
заложенной уязвимостью:

| # | Service | Хранилище | Уязвимость | Категория |
|---|---|---|---|---|
| 1 | `HardwareKeyStoreWriteWeakService` | Android Hardware-Backed Keystore (`AndroidKeyStore`) | Доступное на запись со слабым паролем (`setUserAuthenticationRequired` не вызван + hardcoded `WEAK_PASSWORD = "android"`) | **M9** Insecure Data Storage |
| 2 | `KeyChainAccessService` | Android KeyChain (системное **файловое** хранилище) | Использование файлового хранилища ключей — `KeyChain.getPrivateKey` по hardcoded alias `corp_client_cert` | **M9** Insecure Data Storage |

Оба сервиса стартуются из `MainActivity.onCreate` через `startService`
— реальный call-site в DEX.

### Service 1 — Android Hardware-Backed Keystore (запись + слабый пароль)

```kotlin
val ks = KeyStore.getInstance("AndroidKeyStore")
ks.load(null)
val spec = KeyGenParameterSpec.Builder(ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    // setUserAuthenticationRequired(true) НЕ вызван — VULN sink
    .build()
val gen = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
gen.init(spec); val key = gen.generateKey()

// hardcoded weak password literal в DEX:
val pp = KeyStore.PasswordProtection(WEAK_PASSWORD.toCharArray())  // "android"
ks.getEntry(ALIAS, pp)
```

Сигналы для сканера: `KeyStore.getInstance("AndroidKeyStore")`,
`KeyGenParameterSpec.Builder` без `setUserAuthenticationRequired(true)`,
литерал `"android"`.

### Service 2 — Android KeyChain (файловое)

```kotlin
val privateKey = KeyChain.getPrivateKey(this, "corp_client_cert")
val chain = KeyChain.getCertificateChain(this, "corp_client_cert")
```

Сигналы для сканера: импорт `android.security.KeyChain`, вызовы
`KeyChain.getPrivateKey` / `KeyChain.getCertificateChain`, hardcoded
литерал alias.

## Что должен найти сканер

Минимально достаточный отчёт — **два** finding'а:

1. `HardwareKeyStoreWriteWeakService` — AndroidKeyStore + запись без
   user-authentication + hardcoded weak password literal. Категории
   R017 / R033 / R136 + M9.
2. `KeyChainAccessService` — `KeyChain.getPrivateKey` по hardcoded alias
   из файлового системного keystore. Категория R025 / R042 + M9.

Сильный сканер дополнительно репортит:

- литерал `"android"` как known-weak/default password (R130)
- литерал `"corp_client_cert"` как hardcoded keystore alias (R031)
