# 16 — Секреты и ключи, поставленные вместе с APK

## APK после сборки

```bash
./gradlew :micro:16-creds-and-keys-shipped:assembleDebug
```

Файл: `micro/16-creds-and-keys-shipped/build/outputs/apk/debug/16-creds-and-keys-shipped-debug.apk`

## Что заложено в приложение

В одном приложении сразу пять разных каналов утечки секретов через APK:

1. **`BuildConfig`-поля.** В `build.gradle.kts` через `buildConfigField`
   объявлены константы `API_KEY` и `BACKEND_HMAC_KEY`, которые
   попадают в скомпилированный `BuildConfig.class` и становятся
   обычными строковыми константами в DEX.
2. **Kotlin `const val` в `SecretsHolder.kt`.** JWT-подписной ключ,
   AWS-подобные access key id / secret access key, admin Bearer
   token. Все четыре константы читаются из `MainActivity.onCreate`,
   чтобы DCE/R8 их точно не выкинул (важно для статического анализа
   release-сборки в реальном проекте).
3. **`strings.xml`.** Два `<string>` ресурса с говорящими именами:
   `api_secret_token` и `hardcoded_admin_password`. Видны через
   `aapt2 dump strings` или просто разархивированием apk.
4. **PEM-файл в `res/raw/server_cert.pem`.** Имитирует приватный
   ключ + сертификат (содержимое — placeholder, но формат правильный
   и сигнатуры `-----BEGIN PRIVATE KEY-----` / `-----BEGIN CERTIFICATE-----`
   на месте).
5. **Текстовый файл с креденшалами в `res/raw/api_credentials.txt`.**
   `api_user`, `api_password`, `api_token` — в прямом виде.
6. **BKS-keystore в `assets/client.bks`.** Файл-placeholder с
   сигнатурой `BKS-1`, имитирующий keystore со слабым паролем
   `android` (вшито в имя/комментарий файла). Реально валидным BKS
   быть не обязан — статический сканер ловит само наличие файла
   с расширением `.bks` в assets.

Все эти материалы переживают сборку `assembleDebug` и будут лежать
в финальной apk без каких-либо обёрток.

Расположение слабостей:

- `build.gradle.kts` — `buildConfigField "API_KEY"`, `BACKEND_HMAC_KEY`
- `src/main/kotlin/com/masttest/vuln16/SecretsHolder.kt` — все `const val`
- `src/main/res/values/strings.xml` — `api_secret_token`, `hardcoded_admin_password`
- `src/main/res/raw/server_cert.pem`
- `src/main/res/raw/api_credentials.txt`
- `src/main/assets/client.bks`

## Что должен найти сканер

Минимально достаточный отчёт — **шесть** finding'ов, по одному на
каждый из перечисленных артефактов:

1. `BuildConfig.API_KEY` — hardcoded API ключ (R030, R031).
2. `BuildConfig.BACKEND_HMAC_KEY` — hardcoded HMAC ключ.
3. `SecretsHolder.JWT_SIGNING_KEY` — hardcoded JWT secret в Kotlin
   константе. Также `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`,
   `ADMIN_BEARER_TOKEN` — отдельные срабатывания с разными rule_hint
   (AWS keys, JWT, generic bearer). Допустимо если сворачиваются
   в одно общее «secrets in source».
4. `strings.xml` → `api_secret_token`, `hardcoded_admin_password` —
   секреты в строковых ресурсах. Категория R048 (entropy-based) либо
   keyword-based детект по именам ключей.
5. `res/raw/server_cert.pem` — приватный ключ и сертификат в ресурсах
   приложения (R026, R027, R028, R029).
6. `res/raw/api_credentials.txt` — креденшалы во внешнем файле.
7. `assets/client.bks` — keystore-файл в assets (R017–R042 family,
   и отдельно «слабый пароль»: имя файла подсказывает `android`).

Хорошо, если сканер группирует находки по типам канала (в коде / в
ресурсах / в файлах / в манифесте) — это удобно для отчёта. Плохо,
если он находит только что-то одно (например `BuildConfig`) и
пропускает PEM/BKS — это означает, что у него не работают правила
для отдельных классов артефактов.

**Чего сканер не должен делать**:

- Не должен флажить сами `BuildConfig.DEBUG`/`VERSION_CODE` — это
  легитимные константы.
- Не должен срабатывать на стандартные ресурсы (`app_name`).
