# IPA-05 — Небезопасная конфигурация App Transport Security

## Что заложено в приложение

В `Info.plist` этого таргета установлен ключ `NSAppTransportSecurity` с
**максимальным набором ослаблений** ATS:

| Ключ | Значение | Что отключает |
|---|---|---|
| `NSAllowsArbitraryLoads` | `true` | Разрешает HTTP-запросы ко всем доменам |
| `NSAllowsArbitraryLoadsForMedia` | `true` | Разрешает HTTP для AVFoundation media |
| `NSAllowsArbitraryLoadsInWebContent` | `true` | Разрешает HTTP внутри `WKWebView` |
| `NSAllowsLocalNetworking` | `true` | Разрешает доступ к local network без promppt'а |
| `NSExceptionDomains → insecure.example.com → NSExceptionAllowsInsecureHTTPLoads` | `true` | HTTP для конкретного домена |
| `NSExceptionDomains → insecure.example.com → NSExceptionMinimumTLSVersion` | `TLSv1.0` | Понижает требование TLS до устаревшей 1.0 |
| `NSExceptionDomains → insecure.example.com → NSExceptionRequiresForwardSecrecy` | `false` | Отключает требование forward secrecy |

Плюс в коде `NetworkOps.fetchInsecure()` реально использует это
разрешение — `URLSession.shared.dataTask` на `http://insecure.example.com/v1/profile`
с `Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.fake.fake`.

Двойной сигнал — **manifest-уровень** (Info.plist ослабляет ATS) **+
code-уровень** (приложение реально делает HTTP-запрос с токеном).

## Что должен найти сканер

Минимально достаточный отчёт — **до семи** finding'ов из категории R012:

1. `NSAllowsArbitraryLoads = true` — самый широкий kill-switch.
2. `NSAllowsArbitraryLoadsForMedia = true` — отдельный, для media.
3. `NSAllowsArbitraryLoadsInWebContent = true` — отдельный, для WebView.
4. `NSAllowsLocalNetworking = true` — local-network access.
5. `NSExceptionAllowsInsecureHTTPLoads = true` для конкретного домена.
6. `NSExceptionMinimumTLSVersion = TLSv1.0` — понижение TLS.
7. `NSExceptionRequiresForwardSecrecy = false` — отключение forward secrecy.

Плюс **отдельный** finding на code-уровне:

8. `URLSession.shared.dataTask(with: URL("http://..."))` + Authorization
   header. Категория **R092** + **R087** (sensitive в HTTP-запросе).

Хороший сканер сочетает оба уровня — манифест + код. Слабый — флажит
только манифест, что не показывает реального impact (приложение могло
ослабить ATS «на всякий случай», но не использовать).

## Чего сканер НЕ должен делать

- Не должен ругаться на `LaunchScreen.storyboard`, `AppDelegate`.
- Не должен срабатывать на `URLSession.shared` сам по себе — только
  в комбинации с `http://` literal.
