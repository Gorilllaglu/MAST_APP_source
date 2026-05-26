# MAST_APP_source — исходники тестовых приложений для MAST-сканера

Стенд из **41 намеренно уязвимого** приложения для проверки
мобильного статического сканера (MAST).
38 — Android (Kotlin), 3 — iOS (Swift, pilot).

Каждое приложение лежит в **отдельной папке** и содержит:

- `README.md` — описание заложенных уязвимостей, MASVS/CWE/OWASP-маппинг, что должен найти сканер
- `src/` (Android) либо плоский набор Swift-файлов и `Info.plist` (iOS) — собственно исходный код

Здесь **только исходники** — без Gradle-обёртки, без `build.gradle`,
без собранных APK/IPA, без машинно-зависимых файлов. Полные
build-проекты (с Gradle/Xcode-обвязкой и `*.gradle.kts`/`project.yml`)
лежат **локально** и не входят в этот репозиторий.

## Состав

### Android (38 приложений)

Все имеют единый `applicationId = com.masttest.stingapp`, namespace
вида `com.masttest.vuln<NN>`.

| # | Slug | Класс уязвимости |
|---|---|---|
| 01 | `01-storage-plaintext-prefs` | SharedPreferences plaintext password/JWT |
| 02 | `02-keystore-services-write-weak` | 2 Service: AndroidKeyStore weak pwd + KeyChain |
| 03 | `03-keystore-services-write-and-read-weak` | 3 Service: keystore write + KeyChain read weak + HW KS read priv weak |
| 04 | `04-keystore-services-read-weak-mixed` | 3 Service: KS pub weak + KeyChain file + KS priv weak |
| 05 | `05-sca-vulnerable-deps` | SCA-фикстура: 6 устаревших библиотек с CVE (OkHttp 3.12.0, Gson 2.8.5, Jackson 2.9.10, …) |
| 06 | `06-network-cleartext-http` | `usesCleartextTraffic=true` + HTTP-запрос с Bearer |
| 07 | `07-jetpack-nav-deeplink-hijack` | Jetpack Navigation: 5 destinations, deeplinks на защищённые экраны минуя login |
| 09 | `09-ipc-exported-activity` | Exported Activity без permission |
| 10 | `10-manifest-flags-grabbag` | debuggable + allowBackup + hasFragileUserData + requestLegacyExternalStorage |
| 11 | `11-nsc-misconfig` | NSC: trust user CA + cleartext domain |
| 12 | `12-exported-components-grabbag` | Service + Receiver + Provider все exported |
| 13 | `13-fileprovider-broad-path` | FileProvider с `path="."` и `path="/"` |
| 14 | `14-zip-slip` | unzip без проверки `..` в entry name |
| 15 | `15-path-traversal-fileio` | File(filesDir, intent.getStringExtra("filename")) |
| 16 | `16-creds-and-keys-shipped` | BuildConfig + const + strings.xml + raw/*.pem + assets/*.bks |
| 17 | `17-crypto-misuse-grabbag` | hardcoded key + ECB + static IV + DES + MD5 + weak PBKDF |
| 18 | `18-keystore-misconfig-grabbag` | AndroidKeyStore без auth/HW-check/bio-invalidation |
| 19 | `19-tls-misconfig-grabbag` | trust-all + no-pinning + hostname-only "pinning" |
| 20 | `20-storage-grabbag` | SQLite + WORLD_READABLE + WebView cookies + external |
| 21 | `21-logging-grabbag` | Log.* + HttpLoggingInterceptor.BODY |
| 22 | `22-ipc-pii-leak-grabbag` | implicit Intent + setResult с токеном |
| 23 | `23-webview-jsbridge-no-origin` | `addJavascriptInterface` без origin-check |
| 24 | `24-webview-arbitrary-url` | `loadUrl(intent.getStringExtra("url"))` без allowlist |
| 25 | `25-webview-token-injection` | `loadUrl + evaluateJavascript("setToken('$jwt')")` |
| 26 | `26-webview-file-access` | `setAllowFileAccess(true)` + `setAllowUniversalAccessFromFileURLs(true)` |
| 27 | `27-cp-no-validation` | exported ContentProvider с raw SQL selection |
| 28 | `28-arbitrary-file-via-uri` | `contentResolver.openInputStream(intent.getParcelableExtra)` |
| 29 | `29-intent-redirection-and-rce` | `parseUri` + `startActivity(taint)` + `Class.forName` |
| 30 | `30-deeplink-and-nav-grabbag` | custom scheme + App Link без assetlinks |
| 31 | `31-auth-misconfig-grabbag` | Biometric без CryptoObject + OAuth без PKCE + backdoor |
| 32 | `32-no-protections-baseline` | «vanilla» app — нет root/emu/debugger/Frida/integrity/screen-lock checks |
| 33 | `33-sensitive-transit-grabbag` | HTTP GET `?token=` + WebSocket с JWT |
| 34 | `34-misc-misuse-grabbag` | MODE_WORLD_WRITEABLE + Random для секрета + IPC crash + EditText без secureEntry |
| 35 | `35-not-obfuscated` | release: `isMinifyEnabled = false` + читаемые имена |
| 36 | `36-android-api-inventory` | Discovery: 28+ чувствительных API + 42 permissions |
| 37 | `37-keys-and-certs-in-resources` | 5 PEM с разными заголовками + PKCS#12 + BKS + generic |
| 38 | `38-weak-rasp-checks` | 8 заведомо слабых RASP-проверок |
| 39 | `39-runtime-keystore-weak-password` | `KeyStore.store(...)` в writable со слабыми паролями |

### iOS (3 pilot)

Все имеют единый `CFBundleIdentifier = com.masttest.stingapp`.

| Slug | Уязвимость |
|---|---|
| `ipa-01-keychain-no-acl` | Keychain без access control (3 записи: AccessibleAlways, без AC, Synchronizable=true) |
| `ipa-02-nsuserdefaults-secrets` | Sensitive в `UserDefaults.standard` (token, password, API key, PIN, card dict) |
| `ipa-05-ats-misconfig` | ATS misconfig в Info.plist + URLSession `http://` с Bearer |

## Структура каждой папки

### Android (типичная)

```
<slug>/
├── README.md
└── src/
    └── main/
        ├── AndroidManifest.xml
        ├── kotlin/com/masttest/vuln<NN>/
        │   └── *.kt
        └── res/
            ├── layout/
            └── values/
```

### iOS (плоская)

```
<slug>/
├── README.md
├── Info.plist
├── AppDelegate.swift
├── ViewController.swift
└── <Vuln>Ops.swift
```

## Как использовать

Сами эти файлы **не собираются** — это только source-snapshot.
Чтобы собрать APK/IPA, нужно поместить каждый модуль в соответствующий
build-каркас (Gradle multi-module project для Android, Xcode workspace
для iOS) и выполнить `./gradlew assembleDebug` / `xcodebuild build`.

Полный build-проект (с Gradle wrapper, libs.versions.toml, project.yml
для iOS) хранится отдельно у автора стенда.

## Лицензия

Этот код предназначен **исключительно** для тестирования MAST-сканера.
Не использовать как пример "как делать" в production — он намеренно
содержит уязвимости.
