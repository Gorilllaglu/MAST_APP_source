# 13 — FileProvider со слишком широкими путями

## APK после сборки

```bash
./gradlew :micro:13-fileprovider-broad-path:assembleDebug
```

Файл: `micro/13-fileprovider-broad-path/build/outputs/apk/debug/13-fileprovider-broad-path-debug.apk`

## Что заложено в приложение

В манифесте объявлен `androidx.core.content.FileProvider` со
ссылкой `android:resource="@xml/file_paths"`. Сам этот ресурс
(`src/main/res/xml/file_paths.xml`) содержит сразу три проблемных
объявления корней:

1. `<root-path name="root" path="." />` — отдаёт всё, что лежит выше
   стандартных путей. На практике это означает доступ к
   `/data/data/com.masttest.vuln13/`, включая `shared_prefs/`,
   `databases/`, `files/`. Любое приложение, которому будет передан
   content:// URI с подходящим relative-path, сможет прочитать
   произвольный приватный файл.
2. `<files-path name="files" path="../" />` — относительный путь с
   `..` буквально просит выйти за пределы `files/` в родительскую
   директорию приложения. Эквивалент «доступ ко всему `/data/data/<pkg>/`».
3. `<external-path name="ext" path="/" />` — отдаёт весь корень
   внешнего хранилища (`/sdcard/`).

Эти конфигурации часто встречаются в реальных приложениях:
разработчик хотел «пошарить картинку», скопировал из StackOverflow
конфиг, оставил `path="."`. В вашем vault'е это отдельный паттерн
(WB Drive, WB Courier, Magnit и др.).

Расположение слабостей: `src/main/res/xml/file_paths.xml`.
Фактический FileProvider в манифесте: `src/main/AndroidManifest.xml`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на каждый
небезопасный `<*-path>` в file_paths.xml:

1. `<root-path path="." />` — отдаёт корень приватной директории.
   Категория — небезопасный FileProvider (R009 / vault-pattern
   FileProvider path=".").
2. `<files-path path="../" />` — relative path с traversal-сегментом,
   эквивалент пп.1.
3. `<external-path path="/" />` — отдаёт весь external storage.

Хороший сканер должен показать все три отдельно, потому что они
лечатся одинаково (заменить на узкие пути, например `images/` или
`shared/`), но это три разных XML-узла. Допустимо если он сворачивает
в один общий «Insecure FileProvider configuration» с перечислением
проблемных путей.

**Чего сканер не должен делать**:

- Флажить сам `<provider android:name="androidx.core.content.FileProvider"
  android:exported="false">` — `exported="false"` тут правильно.
  Уязвимость в file_paths.xml, не в `<provider>`.
- Флажить `MainActivity` (это лаунчер, легитимный экспорт).
