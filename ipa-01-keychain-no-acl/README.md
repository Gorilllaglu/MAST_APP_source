# IPA-01 — Keychain item без access control

## Сборка

```bash
xcodegen generate
xcodebuild \
  -project MAST_IPA.xcodeproj \
  -scheme ipa-01-keychain-no-acl \
  -sdk iphonesimulator \
  -configuration Debug \
  -derivedDataPath build/ \
  CODE_SIGNING_ALLOWED=NO \
  build
```

Артефакт: `build/Build/Products/Debug-iphonesimulator/ipa-01-keychain-no-acl.app`

## Что заложено в приложение

`KeychainOps.runAll()` вызывается из `AppDelegate` и пишет в Keychain
**три** записи с заведомо слабыми access-атрибутами:

1. **`auth_token`** — `kSecAttrAccessible = kSecAttrAccessibleAlways`.
   Самый плохой класс access: токен читается из Keychain даже когда
   устройство залочено. Любой фоновый процесс с тем же
   keychain-access-group получит его.
2. **`api_password`** — `kSecAttrAccessibleAfterFirstUnlock` **без**
   `kSecAttrAccessControl`. Читается background-процессами после первой
   разблокировки устройства, не требует биометрии/passcode для каждого
   доступа.
3. **`session_token`** — `kSecAttrAccessibleWhenUnlocked` +
   `kSecAttrSynchronizable = true`. Токен **синхронизируется через
   iCloud Keychain** на все устройства под этим Apple ID — что для
   серверных bearer-токенов почти никогда не нужно и нежелательно.

Все три значения — hardcoded литералы в `KeychainOps.swift`, видны в
скомпилированном Mach-O binary через `strings`:

- `eyJhbGciOiJIUzI1NiJ9.fake.fake` — JWT-shaped токен
- `P@ssw0rd-2024` — admin password
- `sess_live_4eC39HqLyjWDarjtT1zdp7dc` — Stripe-like session

Расположение слабостей: `KeychainOps.swift`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а:

1. `SecItemAdd` с `kSecAttrAccessible: kSecAttrAccessibleAlways` —
   худший accessibility-класс. Категория **R151**.
2. `SecItemAdd` с `kSecAttrAccessibleAfterFirstUnlock`, без
   `kSecAttrAccessControl` (нет user-presence/biometric требований).
   Категория **R151**.
3. `SecItemAdd` с `kSecAttrSynchronizable = true` — синхронизация в
   iCloud Keychain для bearer-токена. Категория **R151** (вариант) /
   R031 (sensitive в коде + неправильное хранение).

Дополнительные сигналы (бонус):

- Три hardcoded литерала (`AUTH_TOKEN`, `API_PASSWORD`, `SESSION_TOKEN`)
  попадают в Mach-O strings — категория **R031** + **R032** (sensitive
  в коде + в исполняемом файле).

## Чего сканер НЕ должен делать

- Не должен флажить как уязвимость **сам факт** использования Keychain —
  это правильный API. Сигнал — конкретный `kSecAttrAccessible*` и
  отсутствие access-control.
- Не должен срабатывать на `LaunchScreen.storyboard` или
  `AppDelegate.swift`.
