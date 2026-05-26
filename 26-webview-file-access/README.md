# 26 — WebView с включённым доступом к file:// и content://

## APK после сборки

```bash
./gradlew :micro:26-webview-file-access:assembleDebug
```

Файл: `micro/26-webview-file-access/build/outputs/apk/debug/26-webview-file-access-debug.apk`

## Что заложено в приложение

`WebViewActivity` (`exported="true"`) включает четыре опасных
WebSettings одновременно:

```kotlin
allowFileAccess = true
allowFileAccessFromFileURLs = true
allowUniversalAccessFromFileURLs = true
allowContentAccess = true
```

Плюс URL для загрузки берётся из `intent.getStringExtra("url")`
без allowlist'а (как в app 24). Это сочетание даёт классическую
атаку «file:// XSS → arbitrary local file read»:

1. Атакующее приложение скачивает HTML-payload в общедоступную
   директорию: `/sdcard/Download/grab.html`.
2. Запускает наш `WebViewActivity` с
   `putExtra("url", "file:///sdcard/Download/grab.html")`.
3. В нашем WebView грузится `file://`-страница. Из-за
   `allowUniversalAccessFromFileURLs=true` JS на этой странице
   может через XHR читать любой файл (`fetch("file:///data/data/com.masttest.vuln26/shared_prefs/secrets.xml")`)
   и слать его на свой backend.

Также `allowContentAccess=true` открывает доступ к `content://` —
WebView сможет грузить `content://com.attacker.provider/foo` и
выполнять там JS.

Расположение: `src/main/kotlin/com/masttest/vuln26/WebViewActivity.kt`,
блок `with(web.settings) { ... }`.

## Что должен найти сканер

Минимально достаточный отчёт — **четыре** finding'а на каждую
включённую опасную настройку:

1. `setAllowFileAccess(true)` / `allowFileAccess = true` — категория
   R051, R052, R159, R160.
2. `setAllowFileAccessFromFileURLs(true)` — категория R051, R102.
3. `setAllowUniversalAccessFromFileURLs(true)` — категория R051, R102
   (самое опасное из четырёх — file://-страница может XHR на любой
   origin).
4. `setAllowContentAccess(true)` — категория R051.

Допустимо если сканер сворачивает их в один общий
«WebView с небезопасным file/content access». Но обычно в правилах
эти четыре отделены — они лечатся индивидуально (некоторые
действительно нужны для специфических use-case, например
`allowFileAccess=true` для отображения локального HTML с диска).

Также сильный сканер видит сочетание этих настроек с **taint от
Intent extras** в `loadUrl(...)` (как в app 24) и помечает это как
особо критичное.

**Чего сканер не должен делать**:

- Не должен флажить `setJavaScriptEnabled(true)` сам по себе —
  это часто легитимно нужно.
- Не должен флажить `MainActivity` (лаунчер).
