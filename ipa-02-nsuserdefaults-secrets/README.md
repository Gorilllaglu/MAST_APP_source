# IPA-02 — Чувствительная информация в NSUserDefaults

## Что заложено в приложение

`UserDefaultsOps.runAll()` вызывается из `AppDelegate.application(_:didFinishLaunchingWithOptions:)`
и пишет в `UserDefaults.standard` (то есть в
`~/Library/Preferences/com.masttest.stingapp.plist`) пять разных
чувствительных значений:

| Ключ | Значение | Класс данных |
|---|---|---|
| `auth_token` | `eyJhbGciOiJIUzI1NiJ9.userdefaults.fake` | bearer / JWT |
| `user_password` | `hunter2-prod` | password |
| `google_api_key` | `AIzaSyA_INTENTIONALLY_FAKE_FOR_TESTING` | API key (формат `AIzaSy…`) |
| `unlock_pin` | `1234` | PIN |
| `saved_card` | dict `{pan: 4242…, cvv: 123, exp: 12/29}` | PII платёжной карты |

NSUserDefaults — это plain plist, **не шифруется**, попадает в iTunes
backup, читается через physical access к device file system. Это
самый частый класс утечки на iOS.

Дополнительно — вызов deprecated `synchronize()` (старый API, всё
ещё встречается в legacy-проектах).

Все литералы — `private static let` в `UserDefaultsOps.swift`,
попадают в Mach-O binary как plaintext strings.

Расположение слабостей: `UserDefaultsOps.swift`.

## Что должен найти сканер

Минимально достаточный отчёт — **пять** finding'ов на `set(_:forKey:)`
с чувствительным значением + ключом, имя которого совпадает с
паттернами `auth_token | password | pin | api_key | card | cvv`:

1. `set(AUTH_TOKEN, forKey: "auth_token")` — bearer token. **R075**.
2. `set(PASSWORD, forKey: "user_password")` — password. **R075**.
3. `set(API_KEY, forKey: "google_api_key")` — third-party API key. **R075 + R030**.
4. `set(PIN_CODE, forKey: "unlock_pin")` — PIN. **R075**.
5. `set(card, forKey: "saved_card")` — PII платёжной карты в словаре. **R075**.

Хороший сканер обращает внимание на сочетание сигналов:

- источник — переменная с подозрительным именем (`*token*`/`*pass*`/`*pin*`/`*card*`/`*cvv*`/`*key*`)
- sink — `UserDefaults.set(_:forKey:)` (любой вариант перегрузки)
- значение — литерал в коде (доп. сигнал R031)

## Чего сканер НЕ должен делать

- Не должен флажить `UserDefaults` сам по себе. Это легитимный API для
  user preferences (например, темы, выбранного языка).
- Не должен флажить `LaunchScreen.storyboard` или AppDelegate.
