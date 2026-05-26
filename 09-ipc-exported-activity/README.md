# 09 — Activity, экспортированная без проверки прав

## APK после сборки

```bash
./gradlew :micro:09-ipc-exported-activity:assembleDebug
```

Файл: `micro/09-ipc-exported-activity/build/outputs/apk/debug/09-ipc-exported-activity-debug.apk`

## Что заложено в приложение

В манифесте объявлены две Activity:

1. `MainActivity` — лаунчер с обычным `intent-filter MAIN/LAUNCHER`,
   `exported="true"`. Это **легитимный** случай: лаунчер всегда экспортирован.
2. `InternalDebugActivity` — Activity с `exported="true"`, **без**
   `intent-filter`, **без** атрибута `android:permission`. Внутри она
   читает значение из Intent extras (`intent.getStringExtra("user_id")`)
   и выполняет привилегированное действие: выводит данные «пользователя»
   в `Log.w` и в `TextView`. По смыслу это admin/debug экран, который
   в нормальной ситуации должен вызываться только из той же подписи
   (signature-permission) или вообще быть `exported="false"`.

Поскольку `InternalDebugActivity` экспортирована без защиты, любое
стороннее приложение на устройстве может вызвать её через явный
`ComponentName`-Intent и передать произвольный `user_id`:

```kotlin
val i = Intent().apply {
    component = ComponentName(
        "com.masttest.vuln09",
        "com.masttest.vuln09.InternalDebugActivity"
    )
    putExtra("user_id", "victim-123")
}
startActivity(i)
```

Расположение: `src/main/AndroidManifest.xml` (объявление `InternalDebugActivity`)
и `src/main/kotlin/com/masttest/vuln09/InternalDebugActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **один** finding на `InternalDebugActivity`:

- В манифесте Activity `InternalDebugActivity` имеет `exported="true"`,
  при этом у неё нет `intent-filter` (её нельзя открыть «просто так»
  из лаунчера) и нет `android:permission`. Тем не менее она читает
  пользовательские данные из Intent и совершает по ним действие.
  Категория — небезопасное использование IPC (MASVS-PLATFORM-3, CWE-926).

Хороший сканер сочетает два сигнала: манифестный (атрибут
`exported="true"` + отсутствие permission/intent-filter) и кодовый
(класс реально читает `intent.get*Extra(...)` и делает с этим что-то).
Только манифестный сигнал — слишком грубо: всегда будут лажные
срабатывания.

**Чего сканер делать не должен**:

- Не должен флажить `MainActivity` — это лаунчер, его экспорт обязателен.
  Если он срабатывает на `MainActivity` — это ложное срабатывание,
  и rule плохо отделяет лаунчеры от внутренних компонентов.
- Не должен флажить вспомогательные системные провайдеры/инициалайзеры
  AndroidX (если они окажутся в `merged manifest`) — это библиотечные
  компоненты, не код приложения.
