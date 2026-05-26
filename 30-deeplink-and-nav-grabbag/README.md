# 30 — Deeplink hijack + App Link без assetlinks.json

## APK после сборки

```bash
./gradlew :micro:30-deeplink-and-nav-grabbag:assembleDebug
```

Файл: `micro/30-deeplink-and-nav-grabbag/build/outputs/apk/debug/30-deeplink-and-nav-grabbag-debug.apk`

## Что заложено в приложение

В одном app две связанных слабости вокруг deeplink/navigation.

1. **Custom URI scheme deeplink + token-based auto-login.**
   Activity `LoginByDeeplinkActivity` принимает intent-filter
   `vuln30://login` (`scheme="vuln30"`, `host="login"`). Любое
   стороннее приложение на устройстве может объявить **тот же
   intent-filter** в своём манифесте — тогда Android покажет
   chooser, и пользователь может случайно выбрать приложение
   атакующего, передав ему токен.

   Дополнительно код Activity сам по себе небезопасен:

   ```kotlin
   val token = intent.data?.getQueryParameter("token") ?: return
   prefs.edit().putString("auth_token", token).putBoolean("logged_in", true).apply()
   ```

   Любой источник (push-уведомление, SMS со ссылкой, страница в
   браузере, другое приложение) может прислать `vuln30://login?token=ATTACKER_TOKEN`,
   и приложение **сразу авторизует пользователя** под этим токеном.
   Это token relay attack: жертве пихают токен атакующего, она по
   ссылке открывает наш app и теперь действует от его имени.

2. **App Link с autoVerify=true БЕЗ assetlinks.json.**
   Activity `ProfileViaAppLinkActivity` имеет
   `<intent-filter android:autoVerify="true">` для домена
   `myapp.example.com`. При этом по адресу
   `https://myapp.example.com/.well-known/assetlinks.json` нужного
   JSON-файла нет (мы не публикуем), и `assetlinks.json` нет в
   проекте на сборку. На Android 12+ autoVerify тихо отключится
   (verification fails), и App Link **снижается до уровня обычного
   browsable-link**: любое приложение с этим же intent-filter сможет
   перехватить https-ссылку через chooser.

Расположение:

- `src/main/AndroidManifest.xml` — оба intent-filter'а.
- `src/main/kotlin/com/masttest/vuln30/LoginByDeeplinkActivity.kt` —
  код auto-login.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а:

1. Custom URI scheme deeplink без верификации источника. Категория
   R102 (вариант), vault-pattern «deeplink hijacking через custom URI
   schemes». Сканер должен видеть intent-filter с `scheme="vuln30"` +
   `BROWSABLE` + код, который читает `intent.data.getQueryParameter`
   и сразу пишет токен в SharedPreferences.
2. Token-based auto-login через deeplink без проверки источника.
   Это уже sink: чувствительный setter (`putString("auth_token", ...)`)
   получает данные из не-доверенного источника (`intent.data`).
3. App Link с `autoVerify="true"` для домена, для которого в
   проекте нет соответствующего `assetlinks.json` (нет в `assets/`
   и не описан в `<meta-data android:name="asset_statements">`).
   Сильный сканер должен распарсить манифест и проверить, что под
   каждый App Link с `autoVerify="true"` есть assetlinks.

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на сам факт `BROWSABLE` intent-filter — это
  легитимная конфигурация, главное — что внутри Activity.
