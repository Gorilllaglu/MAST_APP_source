# 24 — Произвольный URL в WebView

## APK после сборки

```bash
./gradlew :micro:24-webview-arbitrary-url:assembleDebug
```

Файл: `micro/24-webview-arbitrary-url/build/outputs/apk/debug/24-webview-arbitrary-url-debug.apk`

## Что заложено в приложение

`WebViewActivity` объявлена `exported="true"` в манифесте. В её
`onCreate`:

```kotlin
val url = intent.getStringExtra("url") ?: "about:blank"
web.loadUrl(url)
```

Никакого whitelist'а доменов, никакого `Uri.parse(url).host?.endsWith(...)`-фильтра,
никакой проверки на `file://`/`content://`/`javascript:` схемы. Любое
приложение на устройстве может вызвать:

```kotlin
val i = Intent().apply {
    component = ComponentName(
        "com.masttest.vuln24",
        "com.masttest.vuln24.WebViewActivity"
    )
    putExtra("url", "https://attacker.example/phish.html")
}
startActivity(i)
```

И увидит фишинговую страницу внутри нашего app: иконка приложения,
заголовок (если он установлен), системный chrome — пользователь
доверяет.

Это широкий паттерн. В вашем vault'е именно такой паттерн в
Urent (`/webView`+`/webViewSheet` deeplink), Magnit, WB и др.

Расположение: `src/main/kotlin/com/masttest/vuln24/WebViewActivity.kt`,
функция `onCreate`.

## Что должен найти сканер

Минимально достаточный отчёт — **один** finding:

- В `WebViewActivity.onCreate` строка из `intent.getStringExtra("url")`
  напрямую попадает в `WebView.loadUrl(...)` без какого-либо
  whitelisting'а или санитизации. Категория R100, R102, R108.

Сильный сканер обращает внимание на сочетание сигналов:

- источник: `intent.get*Extra`, `Uri.parse(...).getQueryParameter(...)`,
  `bundle.getString(...)`;
- sink: `WebView.loadUrl(...)`, `WebView.loadDataWithBaseURL(...)`,
  `WebView.postUrl(...)`;
- между ними нет проверки `host.endsWith("example.com")` /
  whitelist'а.

**Чего сканер не должен делать**:

- Не должен флажить `loadUrl("https://hardcoded.example.com/")` —
  это легитимный hardcoded URL, не taint.
- Не должен флажить `MainActivity` (лаунчер).
