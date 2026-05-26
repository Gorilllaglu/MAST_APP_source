# 29 — Intent redirection и RCE через ClassLoader

## APK после сборки

```bash
./gradlew :micro:29-intent-redirection-and-rce:assembleDebug
```

Файл: `micro/29-intent-redirection-and-rce/build/outputs/apk/debug/29-intent-redirection-and-rce-debug.apk`

## Что заложено в приложение

`RouterActivity` объявлена `exported="true"` и в `onCreate`
сразу три разных способа использовать её для запуска чужого кода
от нашего имени.

1. **Intent.parseUri / intent:// scheme injection.**
   ```kotlin
   val parsed = Intent.parseUri(rawIntentUri, Intent.URI_INTENT_SCHEME)
   startActivity(parsed)
   ```
   `rawIntentUri` пришла как обычная строка в Intent extras от
   стороннего приложения. `Intent.URI_INTENT_SCHEME` парсит intent:// —
   формат, в котором можно зашифровать любой `Intent` с любыми
   extras, action'ом, component'ом, флагами. Ни проверки category,
   ни whitelist'а action нет. Категория R109.

2. **Intent redirection.**
   ```kotlin
   val nested: Intent? = intent.getParcelableExtra("forward_intent")
   if (nested != null) startActivity(nested)
   ```
   Слепо разворачиваем чужой Intent и запускаем его. Атакующий
   может вложить туда Intent с component, указывающим на наш же
   приватный компонент, — тот запустится с правами нашего
   приложения. Категория R104, R111, R112, R113.

3. **ClassLoader RCE.**
   ```kotlin
   val cls = Class.forName(className)
   val instance = cls.getDeclaredConstructor().newInstance()
   ```
   Атакующий передаёт имя класса; мы его рефлекшеном создаём.
   В сочетании с Java/Kotlin gadget-цепочками или динамической
   подгрузкой через DexClassLoader (если приложение её использует)
   это путь к выполнению произвольного кода. Категория R115.

Расположение: `src/main/kotlin/com/masttest/vuln29/RouterActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а:

1. `Intent.parseUri(rawIntentUri, Intent.URI_INTENT_SCHEME) → startActivity` —
   опасный intent:// scheme. Категория R109.
2. `intent.getParcelableExtra<Intent>("forward_intent") → startActivity` —
   intent redirection. Категория R104, R111-R113.
3. `Class.forName(className).newInstance()` где className пришёл
   из Intent extras — ClassLoader RCE. Категория R115.

Хороший сканер строит data-flow от `intent.get*Extra` до соответствующих
sink'ов и видит **отсутствие** allowlist'а / валидации между ними.
Слабый — флажит сами `parseUri`/`Class.forName`/`startActivity`
без учёта источника аргумента.

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на `Class.forName("com.hardcoded.Class")` —
  это легитимный паттерн загрузки fixed-класса.
