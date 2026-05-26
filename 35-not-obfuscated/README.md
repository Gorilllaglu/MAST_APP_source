# 35 — Release-сборка без обфускации

## APK после сборки

```bash
./gradlew :micro:35-not-obfuscated:assembleRelease
```

(Заметьте: для этого app смотрим именно `assembleRelease`, а не
`assembleDebug` — потому что слабость в том, что **release-сборка**
не minified.)

Файл: `micro/35-not-obfuscated/build/outputs/apk/release/35-not-obfuscated-release-unsigned.apk`

## Что заложено в приложение

В `build.gradle.kts` для `release`-buildType явно выставлено:

```kotlin
isMinifyEnabled = false
isShrinkResources = false
```

Это значит:

- R8/ProGuard НЕ обфусцирует имена классов и методов — все
  identifier'ы в финальном APK остаются как в исходнике.
- Мёртвый код не выкидывается — все ветки логики, все строки,
  все приватные методы сохраняются.
- Ресурсы не сжимаются — strings.xml целиком в APK.

В `MainActivity` намеренно используется класс с говорящим именем —
`AntiFraudInternal` с методом `checkRiskScoreAndDecideIfBlocked()`
и константой `INTERNAL_FRAUD_THRESHOLD = 0.85`. В release-сборке
с включённой обфускацией всё это превратилось бы в `a.a()`,
`a.b`, числа были бы заинлайнены. Без minification всё видно
как есть в jadx за минуту.

Расположение: `build.gradle.kts`, блок `buildTypes { release { ... } }`.

## Что должен найти сканер

Минимально достаточный отчёт — **один** finding:

- В `build.gradle.kts` для release-buildType стоит
  `isMinifyEnabled = false` (или отсутствует вызов `setMinifyEnabled(true)`).
  Сборка не обфусцируется. Категория R014.

Сильный сканер дополнительно может сообщать про:

- `isShrinkResources = false` — отдельная (но связанная) проблема:
  ресурсы не оптимизируются.
- Пустой `proguard-rules.pro` — индикация, что разработчик не
  настроил правила для своих библиотек (что часто становится
  причиной выключения minify обратно при первом крэше).

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на `isMinifyEnabled = false` для **debug**-buildType
  (debug никогда не минифицируется, это нормально).
