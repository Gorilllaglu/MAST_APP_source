# 12 — Service + Receiver + Provider, все экспортированы без защиты

## APK после сборки

```bash
./gradlew :micro:12-exported-components-grabbag:assembleDebug
```

Файл: `micro/12-exported-components-grabbag/build/outputs/apk/debug/12-exported-components-grabbag-debug.apk`

## Что заложено в приложение

В одном приложении сразу три IPC-компонента, каждый из которых
экспортирован с `exported="true"` и **без** `android:permission`:

1. **`ExportedService`** — `<service exported="true">` без permission.
   В `onStartCommand` читает `intent.getStringExtra("phone")` и
   `intent.getStringExtra("text")` и выполняет «отправку SMS»
   (имитируется через `Log.w`). Любое приложение, которое
   `bindService` или `startService` через явный `ComponentName`
   с этими extras, заставит наш сервис выполнить действие.
2. **`ExportedReceiver`** — `<receiver exported="true">` с custom-action
   `com.masttest.vuln12.ACTION_TRIGGER` и без permission. Любое
   приложение, которое сделает `sendBroadcast` с этим action и
   `putExtra("user_id", ...)`, заставит receiver «удалить аккаунт»
   указанного пользователя.
3. **`ExportedProvider`** — `<provider exported="true"
   grantUriPermissions="true">` без `android:readPermission` и
   `android:writePermission`. Реализует `query/insert/update/delete`
   и прокидывает `selection`/`selectionArgs` от вызывающего как есть.
   Любое приложение может через `contentResolver.query/insert/...`
   читать и менять данные.

Все три компонента **специально** реализуют осмысленную логику в
обработчиках (`onStartCommand`, `onReceive`, `query/insert/update/delete`),
а не пустые stub'ы — это нужно чтобы сканер с code-flow анализом
видел что компонент реально использует Intent extras / параметры
запроса (а не просто формально объявлен).

Лаунчер `MainActivity` тоже `exported="true"`, но это легитимно
(ему положено — у него `MAIN/LAUNCHER` intent-filter). Он не
уязвимость и не должен попасть в отчёт.

Расположение слабостей:

- `src/main/AndroidManifest.xml` — объявление трёх компонентов
- `src/main/kotlin/com/masttest/vuln12/ExportedService.kt`
- `src/main/kotlin/com/masttest/vuln12/ExportedReceiver.kt`
- `src/main/kotlin/com/masttest/vuln12/ExportedProvider.kt`

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на каждый
небезопасный компонент:

1. `<service android:name=".ExportedService" android:exported="true" />`
   — экспортированный сервис без permission, при этом обрабатывает
   данные из Intent extras. Категория — небезопасное использование IPC
   (R008).
2. `<receiver android:name=".ExportedReceiver" android:exported="true">`
   — экспортированный receiver без permission, реагирует на custom-action
   и обрабатывает данные из Intent extras. Категория — небезопасное
   использование IPC (R010).
3. `<provider ... android:exported="true">` без `readPermission` и
   `writePermission` — стороннее приложение может читать и менять
   данные через ContentProvider. Категория — небезопасное использование
   IPC (R009).

Сильный сканер сочетает два сигнала на каждый компонент: манифестный
(атрибут `exported="true"` без `android:permission`) и кодовый
(класс реально читает Intent extras / параметры запроса). Только
манифест — слишком грубо, будет много ложных срабатываний на
placeholder-классах.

**Чего сканер не должен делать**:

- Флажить `MainActivity` (это лаунчер).
- Флажить системные компоненты, добавляемые AndroidX в merged manifest
  (например `androidx.startup.InitializationProvider`) — это библиотеки.
