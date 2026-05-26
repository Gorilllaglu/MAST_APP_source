# 23 — WebView JS bridge без проверки origin

## APK после сборки

```bash
./gradlew :micro:23-webview-jsbridge-no-origin:assembleDebug
```

Файл: `micro/23-webview-jsbridge-no-origin/build/outputs/apk/debug/23-webview-jsbridge-no-origin-debug.apk`

## Что заложено в приложение

В `MainActivity.onCreate` создаётся WebView с включённым JavaScript
и регистрируется JS bridge:

```kotlin
web.settings.javaScriptEnabled = true
web.addJavascriptInterface(NativeBridge(this), "Native")
web.loadUrl("https://app.example.com/profile")
```

`NativeBridge` экспортирует **три** опасных метода с
`@JavascriptInterface`:

1. **`getAuthToken()`** — возвращает токен. Любой JavaScript-код,
   запущенный в этом WebView (включая код с любой третьей стороны,
   попавший туда через open-redirect, через iframe, через
   `loadUrl(arbitrary)` из intent extras), может вызвать
   `Native.getAuthToken()` и получить токен.
2. **`saveData(key, value)`** — пишет произвольный ключ-значение в
   SharedPreferences приложения. Любой JS может записать туда что
   угодно, в том числе перезаписать существующие настройки.
3. **`openExternal(url)`** — запускает что-то с произвольным URL.
   Путь к scheme/intent injection через bridge.

Никакой проверки origin (`webView.url?.startsWith("https://app.example.com/")`)
перед выполнением sensitive операций нет.

Расположение слабостей: `src/main/kotlin/com/masttest/vuln23/NativeBridge.kt`
(сами методы) и `src/main/kotlin/com/masttest/vuln23/MainActivity.kt`
(вызов `addJavascriptInterface`).

## Что должен найти сканер

Минимально достаточный отчёт — **один-три** finding'а:

1. Главный — `addJavascriptInterface(NativeBridge(...), "Native")`
   на WebView с включённым JS, при этом ни в `MainActivity`, ни в
   `NativeBridge` нет `webView.url`-проверки или whitelisting'а
   домена. Категория R051, R052, R159, R160.

Дополнительно сканер может срабатывать на:

2. Сам факт `setJavaScriptEnabled(true)` — это слабый сигнал, но
   часто пишется в правилах WebView.
3. Каждый `@JavascriptInterface`-метод как отдельный sink
   (`getAuthToken` возвращает sensitive, `saveData` пишет в
   SharedPreferences, `openExternal` принимает URL). Это вариант
   «более подробный» — допустимо.

Хороший сканер проверяет именно отсутствие origin-check рядом с
`addJavascriptInterface`. Слабый — флажит само
`addJavascriptInterface` независимо от того, как он защищён.

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на сам `WebView` без bridge.
