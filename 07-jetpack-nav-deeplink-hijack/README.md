# 07 — Jetpack Navigation deeplink hijack

## APK после сборки

```bash
./gradlew :micro:07-jetpack-nav-deeplink-hijack:assembleDebug
```

## Что заложено в приложение

Это полноценное приложение на **Jetpack Navigation Component**
(fragment-based). В `res/navigation/nav_graph.xml` объявлено
**5 destinations**, из которых **3 защищённых** (admin, payment,
secrets) имеют **прямые `<deepLink>`** — это и есть уязвимость.

| Destination | Деплинк | Класс | Защищённый? |
|---|---|---|---|
| `loginFragment` (start) | — | `LoginFragment` | ❌ публичный |
| `homeFragment` | — | `HomeFragment` | ❌ |
| **`adminFragment`** | `vuln07://app/admin` | `AdminFragment` | ✅ **должен требовать auth** |
| **`paymentFragment`** | `vuln07://app/payment/{cardId}` | `PaymentFragment` | ✅ |
| **`secretsFragment`** | `https://app.example.com/internal/secrets` | `SecretsFragment` | ✅ |

### Почему это уязвимость

В `AndroidManifest.xml` у `MainActivity` стоит:

```xml
<nav-graph android:value="@navigation/nav_graph" />
```

Это **специальный manifest-тег**: при сборке AGP читает указанный
nav-graph и **автоматически генерирует** `<intent-filter>` для
**каждого** `<deepLink>` в графе. То есть `MainActivity` начинает
принимать VIEW-интенты с любым из URI:

- `vuln07://app/admin`
- `vuln07://app/payment/<любое значение>`
- `https://app.example.com/internal/secrets` (без `autoVerify` →
  обходится через chooser)

`NavHostFragment` при получении такого интента **прыгает напрямую**
к соответствующему destination, **минуя `loginFragment`**.

### Атаки

```bash
# 1. Прямой переход на admin-экран минуя login
adb shell am start -W -a android.intent.action.VIEW \
    -d "vuln07://app/admin"

# 2. Прямой переход на payment с подложенным cardId
adb shell am start -W -a android.intent.action.VIEW \
    -d "vuln07://app/payment/4242424242424242"

# 3. Через https-схему (web-link → app)
adb shell am start -W -a android.intent.action.VIEW \
    -d "https://app.example.com/internal/secrets"
```

В каждом случае авторизация (`LoginFragment`) обойдена. Это
проблема **архитектуры навигации**: deeplinks объявлены без
backstack-восстановления и без обязательного прохождения через
auth-destination.

Конкретно для **R143** (xlsx — «Приложение использует уязвимую
навигацию»), **R145** (xlsx — «Доступ к произвольному Фрагменту с
помощью Интента»).

## Что должен найти сканер

Минимально достаточный отчёт — **3-4 finding'а**:

1. `<nav-graph>` в манифесте подключает все deeplinks из nav_graph.xml
   как точки входа в Activity (`MainActivity` становится приёмником
   3 разных URI). Категория **R143**.
2. `<fragment android:id="@+id/adminFragment">` с `<deepLink>` — прямой
   доступ к фрагменту через Intent в обход start-destination. Категория
   **R145**.
3. То же для `paymentFragment` (с параметром `{cardId}` без
   sanitization). Категория **R145** + связка с R100 (передача
   user-controlled через Intent).
4. То же для `secretsFragment` с `https://`-схемой без `autoVerify` —
   web-link можно перехватить (R143 + связка с App Link без
   assetlinks.json).

Хороший сканер дополнительно:

- читает структуру nav_graph и помечает, какие destinations
  «защищённые» (по имени/наличию sensitive-литералов внутри
  fragment-класса);
- проверяет, есть ли в коде проверка auth-состояния до перехода
  на эти destinations (например, `NavController.OnDestinationChangedListener`
  с redirect на login). Здесь её нет — это сигнал слабости.

## Чего сканер НЕ должен делать

- Не должен флажить `loginFragment` или `homeFragment` — у них нет
  deeplinks.
- Не должен срабатывать на сам `<nav-graph>` без deeplinks — это
  легитимная конструкция.
- Не должен флажить `NavHostFragment` в layout — это правильное
  использование API.

## Что НЕ покрыто этим app (для полноты картины)

- **R144 / R146 (Compose Navigation)** — здесь fragment-based
  Navigation. Compose Navigation (`androidx.navigation:navigation-compose`)
  имеет ту же модель deeplinks через
  `composable("admin", deepLinks = listOf(navDeepLink { uriPattern = "..." }))`,
  но синтаксически другая. Под Compose нужен отдельный модуль
  либо расширить этот.
