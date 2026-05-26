# 10 — Манифестные флаги (4 в одном)

## APK после сборки

```bash
./gradlew :micro:10-manifest-flags-grabbag:assembleDebug
```

Файл: `micro/10-manifest-flags-grabbag/build/outputs/apk/debug/10-manifest-flags-grabbag-debug.apk`

## Что заложено в приложение

В `<application>` манифеста одновременно выставлены **четыре** опасных флага:

1. `android:debuggable="true"` — приложение можно подключить отладчиком,
   снять дамп памяти, поставить точку останова и т.д. В release-сборке
   это критично.
2. `android:allowBackup="true"` без сопровождающего `<full-backup-content>`
   с правилами exclude — Android Auto-Backup безусловно унесёт
   приватные файлы приложения (включая `shared_prefs/`, `databases/`,
   `files/`) в Google Drive пользователя.
3. `android:hasFragileUserData="true"` — при удалении приложения
   пользователю предложат сохранить его данные. Для приложения,
   обрабатывающего секреты, это утечка: пользователь по ошибке
   соглашается, и потом эти данные восстанавливаются на следующем устройстве.
4. `android:requestLegacyExternalStorage="true"` — приложение продолжает
   пользоваться legacy file access вместо Scoped Storage, имея при этом
   широкий доступ к `/sdcard/`.

Сам Kotlin-код приложения тривиален и не имеет уязвимостей: вся
проблема — конфигурационная, в манифесте.

Расположение: `src/main/AndroidManifest.xml`, тег `<application>`.

## Что должен найти сканер

Минимально достаточный отчёт — **четыре** finding'а, по одному на каждый
из перечисленных атрибутов:

1. `android:debuggable="true"` — в release-приложении быть не должен.
   Категория — небезопасные настройки сборки (R002).
2. `android:allowBackup="true"` — без exclude-правил утекают приватные
   данные. Категория — backup-конфигурация (R006).
3. `android:hasFragileUserData="true"` — рискованная настройка
   удержания пользовательских данных (R004).
4. `android:requestLegacyExternalStorage="true"` — обход Scoped Storage,
   широкий доступ к внешнему хранилищу (R003).

Хороший сканер должен показать все четыре отдельно, чтобы было понятно,
какие именно настройки исправлять. Если он сворачивает их в один
общий «Insecure manifest configuration» — это допустимо, но менее
полезно.

**Чего сканер не должен делать**: флажить `MainActivity` как
`exported="true"` — это лаунчер, его экспорт обязателен.
