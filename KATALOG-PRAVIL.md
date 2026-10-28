# Каталог правил встраивания CerberSDK

71 правило: 38 Kotlin, 27 Java, 6 XML. Составлено 2026-10-28.

## Для кого и зачем

Правила читает безопасник конкретного приложения на этапе проверки исходного кода составом. Задача набора — показать, насколько корректно встроена библиотека безопасности: где её механизмы отключены, где обойдены, и где приложение открыло канал, которого библиотека не видит.

## Как читать критичность

| Уровень | Что означает |
|---|---|
| **Высокая** (`ERROR`) | приложение отключает или обходит защиту, которую SDK предоставляет, либо открывает дыру в идентификации. Встраивание сломано, исправлять обязательно |
| **Средняя** (`WARNING`) | приложение открывает канал вывода данных, которого SDK не видит, либо не реагирует на событие SDK. Нужна оценка человеком: идут ли через это место данные К2 |
| **Низкая** (`INFO`) | инвентарь: конструкция законная, но должна попасть в обзор |

Отдельно `confidence` — насколько достоверно правило устанавливает сам факт:

`HIGH` — факт наличия вызова или атрибута установлен точно. `MEDIUM` — якорь частично эвристический: имя класса, регекс по ресурсам, форма объявления. `LOW` — связать условия нечем, правило шумит по построению и годится только как подсказка к обзору.

Высокая критичность при низкой достоверности — не противоречие: дефект тяжёлый, но правило может указать не на него.

## Что важно знать про весь набор сразу

**Счёт находок не является мерой полноты.** Есть основания считать, что движок не выдаёт второе совпадение, если его текст в точности повторяет уже выданное. Одна находка означает **минимум** одно место, не ровно одно. Проверка идёт, подробности в `rulesTZ/ETAP-5-BACKLOG.md`, пункт 2.

**Тела геттеров свойств Kotlin движок не просматривает.** Любой проверяемый вызов, спрятанный в `get()`, не найдёт ни одно правило набора.

**Межфайловой и межфункциональной связи нет.** Правила с областью функции видят только её тело; вынос работы в хелпер скрывает её. Манифестные правила не связаны с классами обработчиков — это делает человек.

**Атрибут, заданный стилем или темой, непокрываем.** Текстовое содержимое элемента XML шаблоном не адресуется, проверено дважды.

**Шесть XML-правил лежат в котлиновском блоке набора.** В саставе набор один, но внутри развилка на два языковых блока. Приложение, которое проверяется джава-блоком, проверок манифеста и разметки сейчас не получит: их надо продублировать. Подробности и два открытых вопроса — в `rulesTZ/ETAP-5-BACKLOG.md`, пункт 1a.

---

# Kotlin

## Группа A. Интеграция делегата и Application

### `cerber-missing-sdk-application-interface` · высокая · HIGH

Класс наследует `Application`, но не реализует интерфейс `CerberSDKApplication`. Без этого SDK не инициализируется вовсе — то есть защиты нет ни одной, независимо от остальных настроек. Самая тяжёлая форма неправильного встраивания.

**Текст правила (en).** Application class extends Application but does not implement CerberSDKApplication. CerberSDK requires the host Application class to implement CerberSDKApplication interface for proper initialization.

**Перевод.** Класс приложения наследует `Application`, но не реализует `CerberSDKApplication`. Для корректной инициализации CerberSDK требует, чтобы класс `Application` host-приложения реализовывал интерфейс `CerberSDKApplication`.

**Особенности.** Ловит: класс, наследующий `Application` напрямую, с одним или двумя интерфейсами и без них. Пропуски: класс, наследующий не `Application`, а свою промежуточную базу (`class App : BaseApp()`), — правило требует литерала `Application` в супертипе. Ложные: класс с именем на `*Application*`, который наследует `Application` для технических целей и SDK ему не нужен; проверено клеткой `K12-PROBE-1`, правило на таком молчит, если он не наследует `Application`.

### `cerber-default-constructor-used` · высокая · HIGH

Делегат создан конструктором без аргументов. Дефолтный конструктор выставляет `secureInitialCheck=false`, `secureScreen=false`, `secureClipboard=false` — то есть все защиты выключены сразу, одной строкой.

**Текст правила (en).** CerberSDKApplicationDelegate() is called with no arguments. Default constructor sets secureInitialCheck=false, secureScreen=false, secureClipboard=false — all security features are disabled by default. Always use the parameterized constructor with explicit security settings.

**Перевод.** `CerberSDKApplicationDelegate()` вызван без аргументов. Дефолтный конструктор выставляет `secureInitialCheck=false`, `secureScreen=false`, `secureClipboard=false` — все защитные механизмы отключены по умолчанию. Всегда используйте конструктор с явно заданными параметрами безопасности.

**Особенности.** Ловит ровно одну форму и ловит её точно. Важная деталь реализации: пустые круглые скобки в шаблоне движок трактует как «любые аргументы», поэтому без исключения `pattern-not: CerberSDKApplicationDelegate($ARG, ...)` правило срабатывало на корректном вызове с десятью аргументами. Исключение внесено и проверено на этапе 1, клетка `K04-TN-1`. Ложных не наблюдалось. Пропуски: делегат, созданный фабрикой, — это зона правила `missing-delegate-creation`.

### `cerber-missing-delegate-creation` · средняя · MEDIUM

Класс реализует `CerberSDKApplication` делегированием, но делегат получен не свежим экземпляром, а из внешнего источника — поля, DI, геттера. Тогда параметры безопасности задаёт не класс приложения, и проверить их на месте невозможно.

**Текст правила (en).** Class implements CerberSDKApplication via delegation, but the delegate is not a fresh CerberSDKApplicationDelegate(...) instance. The delegate must be instantiated with explicit security parameters, not obtained from an external source (e.g., DI, field, getter).

**Перевод.** Класс реализует `CerberSDKApplication` через делегирование, но делегат не является свежим экземпляром `CerberSDKApplicationDelegate(...)`. Делегат должен создаваться с явными параметрами безопасности, а не получаться из внешнего источника — DI, поля, геттера.

**Особенности.** Ловит делегирование `by` с выражением, отличным от конструктора делегата. Ложные: внедрение делегата через DI — архитектурно законный приём, и если параметры заданы в модуле DI корректно, находка ложная. Исправить правилом нельзя: куда ведёт DI-граф, статика не знает. **Требует разбора эксперта**: проверить, где создаётся экземпляр и с какими параметрами. Пропуски: делегат, полученный геттером свойства, — тела геттеров движок не просматривает.

### `cerber-insecure-auth-mode` · высокая · HIGH

В конструктор делегата передан режим авторизации, отличный от `CERBER`. Другие режимы ослабляют или отключают авторизацию SDK.

**Текст правила (en).** CerberSDKApplicationDelegate is initialized with a non-CERBER AuthorizationMode. Non-CERBER authorization modes may disable or weaken SDK authorization. Use AuthorizationMode.CERBER in production code.

**Перевод.** `CerberSDKApplicationDelegate` инициализирован с режимом авторизации, отличным от `CERBER`. Режимы, отличные от `CERBER`, могут отключать или ослаблять авторизацию SDK. В продуктовом коде используйте `AuthorizationMode.CERBER`.

**Особенности.** Ловит и позиционный, и именованный аргумент — вторая форма добавлена на этапе 1, потому что именно ею в Kotlin вызывают конструктор с десятью параметрами, и без неё правило молчало на основной форме. Отсев `CERBER` сделан регексом с просмотром вперёд, который на этом движке работает только в начале выражения. Ложных не наблюдалось. Пропуски: режим, пришедший переменной, — это зона правила `insecure-auth-mode-variable`.

### `cerber-insecure-auth-mode-variable` · высокая · MEDIUM

Режим авторизации течёт через переменную, возврат функции или условное выражение. Статика не может проследить, какое значение дойдёт до конструктора, поэтому правило показывает все места, где режим задаётся не литералом `CERBER`.

**Текст правила (en).** AuthorizationMode is assigned to a variable, returned from a function, or used in a conditional expression. Variable-based AuthorizationMode values may propagate to CerberSDKApplicationDelegate. Ensure only AuthorizationMode.CERBER is reachable in production code.

**Перевод.** `AuthorizationMode` присвоен переменной, возвращён из функции или использован в условном выражении. Значения режима, идущие через переменную, могут попасть в `CerberSDKApplicationDelegate`. Убедитесь, что в продуктовой сборке достижим только `AuthorizationMode.CERBER`.

**Особенности.** Ловит объявление переменной и поля, присваивание, возврат, тернарное условие, а также связку «объявление и сборка делегата в одном классе». Ложные: режим, выбираемый по сборке так, что в продуктовой ветке он всегда `CERBER`, — находка формально верна, дефекта нет. **Требует разбора эксперта**: проследить, какое значение достижимо в продуктовой сборке. Пропуски: режим, пришедший параметром функции извне, — ловить нечего, клетка `K07-PROBE-1` это подтверждает.

## Группа B. Конфигурация защит делегата

Четыре правила этой группы — позиционные: они опознают аргумент по его месту в конструкторе. Порядок параметров снят с SberApps 3.6.2 двумя независимыми путями и равен: `authorizationMode`, `buildType`, `isolated`, `secureInitialCheck`, `secureScreen`, `secureClipboard`, `enableTestServerPinning`, `sberMagOptions`, `whiteListOptions`, `disablePasswordlessAuth`. В Kotlin эти правила работают именованным аргументом и к порядку не чувствительны; в Java — позиционно, и там рассогласование порядка однажды дало дефект.

### `cerber-secure-initial-check-disabled` · высокая · HIGH

Делегат создан с `secureInitialCheck=false`. Это выключает детект root, эмулятора и подмены на старте приложения.

**Текст правила (en).** CerberSDKApplicationDelegate created with secureInitialCheck=false. This disables root/emulator/jailbreak detection at startup. Attackers on rooted devices can tamper with the application. Set secureInitialCheck=true.

**Перевод.** `CerberSDKApplicationDelegate` создан с `secureInitialCheck=false`. Это отключает детект root, эмулятора и jailbreak при запуске. На устройстве с root приложение можно модифицировать. Установите `secureInitialCheck=true`.

**Особенности.** Ловит литерал `false` и переменную в этой позиции. Ложных не наблюдалось. Пропуски: значение, вычисленное выражением, — из разбора SberApps известно, что так и бывает: там `secureInitialCheck` вычисляется как `"promDef".contains("prom")`, и одно переименование флейвора выключает RASP. Такую форму правило не увидит, и это существенный пропуск.

### `cerber-insecure-screen-disabled` · средняя · HIGH

Делегат создан с `secureScreen=false`. Снимается `FLAG_SECURE` со всех экранов: разрешены скриншоты и запись экрана.

**Текст правила (en).** CerberSDKApplicationDelegate created with secureScreen=false. This allows screenshots and screen recording of the application, potentially exposing sensitive corporate data. Set secureScreen=true to apply FLAG_SECURE on all activities.

**Перевод.** `CerberSDKApplicationDelegate` создан с `secureScreen=false`. Это разрешает скриншоты и запись экрана приложения, что может раскрыть чувствительные корпоративные данные. Установите `secureScreen=true`, чтобы `FLAG_SECURE` ставился на все активности.

**Особенности.** Ловит литерал и переменную в позиции пятого аргумента. Ложных не наблюдалось. Важно знать при триаже: даже при `secureScreen=true` флаг снимается серверной политикой `screenshotsForbidden`, а также файловым обходом `DebugBypassChecker`. То есть правило проверяет настройку приложения, а не фактическое состояние защиты.

### `cerber-insecure-clipboard-disabled` · средняя · HIGH

Делегат создан с `secureClipboard=false`. Защита буфера обмена отключена, скопированное из приложения читает любое другое приложение.

**Текст правила (en).** CerberSDKApplicationDelegate created with secureClipboard=false. This allows copy-paste of sensitive data from the application. Set secureClipboard=true to enable encrypted clipboard protection.

**Перевод.** `CerberSDKApplicationDelegate` создан с `secureClipboard=false`. Это разрешает копирование чувствительных данных из приложения. Установите `secureClipboard=true`, чтобы включить защиту буфера обмена.

**Особенности.** Ловит литерал и переменную в позиции шестого аргумента. В джава-версии этого правила позиция была указана неверно — третьим аргументом, то есть на `isolated`, — и правило одновременно срабатывало на нормальном значении любой интеграции и не видело настоящий дефект. Исправлено 2026-10-27. Котлиновская версия работает именованным аргументом и этой ошибки не имела.

### `cerber-passwordless-auth-disabled` · средняя · MEDIUM

Делегат создан с `disablePasswordlessAuth=true`. Отключается вход по одноразовому коду через SberStore, пользователя возвращают к менее надёжным способам.

**Текст правила (en).** CerberSDKApplicationDelegate created with disablePasswordlessAuth=true. This disables OTP-based passwordless authentication via SberStore, forcing users to use less secure auth methods.

**Перевод.** `CerberSDKApplicationDelegate` создан с `disablePasswordlessAuth=true`. Это отключает беспарольную аутентификацию по одноразовому коду через SberStore и заставляет пользователей применять менее надёжные способы входа.

**Особенности.** Ловит литерал и переменную в последней позиции конструктора. Ложные: отключение может быть осознанным решением по продукту, если беспарольный вход в этом приложении не предусмотрен. **Требует разбора эксперта**: сверить с требованиями к приложению.

### `cerber-non-production-build-type` · средняя · HIGH

В конструктор делегата передан непродуктовый тип сборки — `DEV1`, `DEV2`, `IFT`, `PSI`. Это другие адреса API, ослабленный пиннинг и короткие интервалы таймбомбы.

**Текст правила (en).** Non-production BuildType detected in CerberSDKApplicationDelegate constructor. Dev/test build types use different API endpoints, weaker SSL pinning, and shorter timebomb intervals (10 min vs 14 days). Ensure production builds use BuildType.PROM.

**Перевод.** В конструкторе `CerberSDKApplicationDelegate` обнаружен непродуктовый `BuildType`. Отладочные и тестовые типы сборки используют другие адреса API, ослабленный SSL-пиннинг и укороченные интервалы таймбомбы (10 минут против 14 дней). Убедитесь, что продуктовые сборки используют `BuildType.PROM`.

**Особенности.** Ловит все четыре непродуктовых литерала. Ложные: правило не различает продуктовую и отладочную сборку — в отладочном флейворе непродуктовый тип законен. **Требует разбора эксперта**: проверить, в какой вариант сборки попадает эта строка. Пропуски: тип, пришедший переменной, — зона правила `non-production-build-type-variable`.

### `cerber-non-production-build-type-variable` · средняя · MEDIUM

Непродуктовый тип сборки течёт через переменную, возврат или условное выражение.

**Текст правила (en).** Non-production BuildType (DEV1/DEV2/IFT/PSI) is assigned to a variable, returned from a function, or used in a conditional expression. This value may propagate to CerberSDKApplicationDelegate. In production builds, only BuildType.PROM must be reachable.

**Перевод.** Непродуктовый `BuildType` (`DEV1`, `DEV2`, `IFT`, `PSI`) присвоен переменной, возвращён из функции или использован в условном выражении. Это значение может попасть в `CerberSDKApplicationDelegate`. В продуктовой сборке должен быть достижим только `BuildType.PROM`.

**Особенности.** В отличие от близнеца по режиму авторизации, это правило перечисляет плохие значения положительно, а не отсеивает хорошее просмотром вперёд, — поэтому ему не потребовалась правка, которую пришлось делать для `insecure-auth-mode-variable`. Ложные те же: выбор типа по флейвору. **Требует разбора эксперта.** Пропуски: тип, пришедший параметром извне, клетка `K14-PROBE-1`.

## Группа C. RASP и реакция на детект

### `cerber-debug-bypass-checker-present` · высокая · HIGH

В коде приложения есть обращение к `DebugBypassChecker` — отладочному механизму SDK, который полностью отключает детект root и эмулятора. Из разбора SberApps: механизм срабатывает по наличию файла с именем-хешем в `filesDir` и заодно снимает `FLAG_SECURE`. В SberApps он спит, потому что сборка `PROM`, но в любом приложении с непродуктовым типом сборки активен.

**Текст правила (en).** A call to CerberSDK DebugBypassChecker is present in host-app code. This debug mechanism completely disables root and emulator detection. If this code path is reachable in production, an attacker can bypass all security checks by triggering the debug condition. Remove all debug bypass code from production builds. KNOWN LIMITATIONS, measured on sastav 2026-10-22: a call placed inside a Kotlin property getter is not reported at all, which makes wrapping the call in a property a complete bypass of this rule; a method reference and a reflective call by method name are not reported either.

**Перевод.** В коде host-приложения присутствует обращение к `DebugBypassChecker` CerberSDK. Этот отладочный механизм полностью отключает детект root и эмулятора. Если этот путь достижим в продуктовой сборке, атакующий может обойти все проверки безопасности, выполнив отладочное условие. Удалите весь код отладочного обхода из продуктовых сборок. Известные ограничения, измерены на составе 2026-10-22: вызов внутри геттера свойства Kotlin не попадает в находки вовсе, то есть обёртка вызова в свойство полностью обходит правило; ссылка на метод и вызов рефлексией по имени тоже не находятся.

**Особенности.** Единственное правило набора, в сообщении которого ограничения указаны прямо — и это сделано сознательно: обход правила тут тривиален, и безопасник должен знать, что молчание правила ничего не доказывает. Пропуски измерены на клетках `R1-GAP-1`…`GAP-5`. Ложных не наблюдалось.

### `cerber-root-detected-no-termination` · высокая · MEDIUM

`onRootDetected()` реализован, но процесс не завершается. Экран SDK `DeviceNotSecureActivity` обходится через Frida перехватом `startActivity`, поэтому коллбэк приложения — последняя линия обороны.

**Текст правила (en).** onRootDetected() has an implementation but does not terminate the app. CerberSDK's DeviceNotSecureActivity can be bypassed via Frida by hooking ContextWrapper.startActivity(). The host app's onRootDetected() callback is the last line of defense — it must call exitProcess(), Process.killProcess(), or at minimum finish() all activities to prevent continued use on rooted device.

**Перевод.** `onRootDetected()` реализован, но не завершает приложение. Экран `DeviceNotSecureActivity` из CerberSDK обходится через Frida перехватом `ContextWrapper.startActivity()`. Коллбэк `onRootDetected()` host-приложения — последняя линия обороны: он должен вызвать `exitProcess()`, `Process.killProcess()` или как минимум `finish()` всех активностей, чтобы работа на устройстве с root не продолжилась.

**Особенности.** Исключения перечисляют шесть форм завершения: `System.exit`, `exitProcess`, `Process.killProcess`, `finish`, `finishAffinity`, `finishAndRemoveTask`. Все шесть проверены на этапе 2 на джава-близнеце. Ложные: завершение, вынесенное в хелпер (`terminateApp()`), — правило видит только тело коллбэка. **Требует разбора эксперта**: пройти по вызову и убедиться, что он ведёт к завершению. Это основной источник ложных у правила.

### `cerber-root-detection-bypass-attempt` · высокая · MEDIUM

`onRootDetected()` с пустой реализацией: событие детекта получено и выброшено.

**Текст правила (en).** CerberSDK root detection callback onRootDetected() has an empty implementation. Root detection events must be handled properly (e.g., terminate the application or restrict functionality).

**Перевод.** Коллбэк детекта root `onRootDetected()` из CerberSDK имеет пустую реализацию. События детекта root должны обрабатываться — как минимум завершением приложения или ограничением функциональности.

**Особенности.** Пересекается с предыдущим правилом: на пустом теле срабатывают оба, и это не дефект, а два разных вывода об одной форме. Неточность формулировки, отложенная на доработку: сообщение говорит про «empty implementation», но пустые `{ }` в шаблоне движок трактует как любое тело, значит правило способно сработать и на непустой реализации.

### `cerber-secure-check-wrong-state-comparison` · высокая · HIGH

Результат `SecureChecker.performChecks()` сверяется с одним небезопасным состоянием вместо эталонного `DEVICE_COMPLIANT`. Тогда все остальные небезопасные состояния проходят проверку: сверка с `ROOT_DETECTED` пропускает и эмулятор, и заблокированное устройство.

**Текст правила (en).** SecureChecker.performChecks() result is compared against a single unsafe state instead of DEVICE_COMPLIANT. Every other unsafe state passes this check: comparing against ROOT_DETECTED alone lets EMULATOR_DETECTED and DEVICE_LOCKED through. Compare against DEVICE_COMPLIANT and treat everything else as non-compliant.

**Перевод.** Результат `SecureChecker.performChecks()` сверяется с одним небезопасным состоянием вместо `DEVICE_COMPLIANT`. Любое другое небезопасное состояние эту проверку проходит: сверка только с `ROOT_DETECTED` пропускает `EMULATOR_DETECTED` и `DEVICE_LOCKED`. Сверяйтесь с `DEVICE_COMPLIANT` и считайте всё остальное несоответствием.

**Особенности.** Единственная из четырёх форм дефекта «результат проверки не валидирован», которая на этом движке реализуется полностью. Три остальные — результат отброшен, результат ушёл в журнал, результат сохранён и не сверен — упираются в ограничения движка, подробности в `OGRANICHENIYA-SASTAV.md`. Ложных не наблюдалось.

### `cerber-flag-secure-cleared` · высокая · HIGH

`FLAG_SECURE` снимается явно. SDK ставит его на все активности при `secureScreen=true`, и снятие обходит политику защиты экрана.

**Текст правила (en).** FLAG_SECURE is explicitly cleared. CerberSDK sets FLAG_SECURE on all activities when secureScreen=true. Clearing this flag allows screenshots and screen recording, bypassing the SDK's screen protection policy.

**Перевод.** `FLAG_SECURE` снимается явно. CerberSDK ставит `FLAG_SECURE` на все активности при `secureScreen=true`. Снятие этого флага разрешает скриншоты и запись экрана, обходя политику защиты экрана SDK.

**Особенности.** Ловит `clearFlags` и побитовое снятие. Ложные: снятие флага на экране, где его быть и не должно, — например на экране заглушки или сплэше без данных. **Требует разбора эксперта**: посмотреть, какой экран и что на нём показано. Пропуски: побитовые арифметические выражения в шаблонах движок не поддерживает, поэтому форма `flags &= ~FLAG_SECURE` не находится; это измеренное ограничение, пункт 4 в `OGRANICHENIYA-SASTAV.md`.

## Группа D. Авторизация на экранах

### `cerber-activity-missing-auth-onresume` · высокая · HIGH

Экран переопределяет `onResume()`, но не запрашивает авторизацию SDK. Пользователь, вернувшийся на экран после переключения приложений, получает доступ без проверки.

**Текст правила (en).** Screen '$ACT' overrides onResume() but does not call requestCerberAuth(this). Every Activity or Fragment must verify CerberSDK authorization in onResume() to prevent unauthorized access when the user returns to the screen (e.g., after switching apps).

**Перевод.** Экран `$ACT` переопределяет `onResume()`, но не вызывает `requestCerberAuth(this)`. Каждая `Activity` и `Fragment` должны проверять авторизацию CerberSDK в `onResume()`, чтобы исключить неавторизованный доступ при возврате пользователя на экран — например после переключения приложений.

**Особенности.** Текст сообщения требует уточнения, и это отложено: правило говорит про переопределённый `onResume`, а находит и экраны без него. Подтверждено клеткой `K01-PROBE-1`. Ложные: экран без чувствительных данных — сплэш, экран ошибки, онбординг — авторизации не требует. **Требует разбора эксперта**: решить, нужна ли проверка на этом экране. Это самый массовый источник ложных в наборе, потому что правило срабатывает на каждом экране без вызова.

### `cerber-activity-no-lifecycle-override` · средняя · MEDIUM

Экран вовсе не переопределяет `onResume()`, значит проверить авторизацию при возврате негде.

**Текст правила (en).** Activity '$ACT' does not override onResume(). Without an onResume() override, CerberSDK authorization cannot be checked when the user returns to this screen. Add onResume() with requestCerberAuth(this).

**Перевод.** Активность `$ACT` не переопределяет `onResume()`. Без переопределения `onResume()` авторизацию CerberSDK нельзя проверить при возврате пользователя на этот экран. Добавьте `onResume()` с вызовом `requestCerberAuth(this)`.

**Особенности.** Дополняет предыдущее правило: то требует наличия `onResume`, это — его отсутствия, поэтому на одном классе они одновременно не срабатывают. Ложные, измеренные на джава-близнеце: собственный `BaseActivity`, у которого `onResume` переопределяют наследники. Исправить не удалось — попытка закрыть через пустое тело класса убила правило целиком, потому что пустые `{ }` движок трактует как любое тело. **Требует разбора эксперта**: отличить базовый класс от экрана.

### `cerber-auth-requested-but-not-observed` · средняя · MEDIUM

`requestCerberAuth()` вызван, но `observeAuthState()` в том же классе нет. Приложение запросило авторизацию и не слушает, что с ней стало.

**Текст правила (en).** requestCerberAuth() is called but observeAuthState() is never used in the same class. Without observing auth state, the host app cannot react to authorization changes (Authorized→Pin, timeout, logout). Subscribe to observeAuthState() to handle state transitions properly.

**Перевод.** `requestCerberAuth()` вызван, но `observeAuthState()` в этом же классе не используется. Без наблюдения за состоянием авторизации host-приложение не может реагировать на её изменения — переход в запрос пина, таймаут, выход. Подпишитесь на `observeAuthState()`, чтобы обрабатывать переходы состояний.

**Особенности.** Ложные: наблюдение вынесено в отдельный класс — распространённая и правильная архитектура, при ней правило срабатывает на корректной интеграции. Клетка `K02-PROBE-1` ставилась именно под это; на Java аналогичная клетка неожиданно промолчала, механизм не выяснен. **Требует разбора эксперта**: найти, где наблюдается состояние.

### `cerber-no-auth-detected-ignored` · высокая · MEDIUM

`onNoAuthDetected()` с пустой реализацией: SDK сообщил об отсутствии авторизации, приложение проигнорировало.

**Текст правила (en).** onNoAuthDetected() callback has an empty implementation. When CerberSDK reports no authorization, the app must handle this event — typically by redirecting to login or restricting access to sensitive screens.

**Перевод.** Коллбэк `onNoAuthDetected()` имеет пустую реализацию. Когда CerberSDK сообщает об отсутствии авторизации, приложение обязано обработать это событие — как правило, переходом на вход или ограничением доступа к чувствительным экранам.

**Особенности.** Ловит пустое тело, тело-выражение `= Unit` и форму без `override`. Последняя добавлена для случая, когда коллбэк объявлен не переопределением; на джава-близнеце именно она дала ложную сработку на абстрактном объявлении в интерфейсе, закрытую исключением `pattern-not-inside: interface $I { ... }`. Котлиновская версия на фикстурах не прогонялась — предсказуемо нужно то же исключение.

## Группа E. Коллбэки SDK

Три правила одной формы: событие от SDK получено и выброшено. Все три ловят пустое тело, `= Unit` и форму без `override`.

### `cerber-logout-callback-empty` · средняя · MEDIUM

**Текст правила (en).** onLogout() callback has an empty implementation. The app must handle logout events from CerberSDK by clearing sensitive data from memory and navigating to a safe screen.

**Перевод.** Коллбэк `onLogout()` имеет пустую реализацию. Приложение обязано обрабатывать событие выхода из CerberSDK: очищать чувствительные данные из памяти и переходить на безопасный экран.

**Особенности.** Ложные: очистка вынесена в другой коллбэк или в наблюдателя состояния. **Требует разбора эксперта.** Отдельно отложено: правило не проверяет остановку foreground-сервиса приложения — связать «сервис есть» и «не остановлен» одним правилом нельзя, межфайловой связи у движка нет.

### `cerber-user-changed-callback-empty` · средняя · MEDIUM

**Текст правила (en).** onUserChanged() callback has an empty implementation. When CerberSDK detects a user change, the app must clear cached user-specific data and reset state to prevent data leakage between accounts.

**Перевод.** Коллбэк `onUserChanged()` имеет пустую реализацию. При смене пользователя, обнаруженной CerberSDK, приложение обязано очистить кешированные данные прежнего пользователя и сбросить состояние, чтобы данные не перетекли между учётными записями.

**Особенности.** Самое прямое последствие пустой реализации — данные одного сотрудника остаются видны другому на том же устройстве. Ложные: сброс делается наблюдателем состояния авторизации. **Требует разбора эксперта.**

### `cerber-file-received-callback-empty` · средняя · INFO

**Текст правила (en).** onFileReceived() callback has an empty implementation. CerberSDK's secure file transfer sends files through CerberTransferActivity and notifies the host app via this callback. An empty implementation means received files are silently dropped.

**Перевод.** Коллбэк `onFileReceived()` имеет пустую реализацию. Защищённая передача файлов CerberSDK отправляет файлы через `CerberTransferActivity` и уведомляет host-приложение этим коллбэком. Пустая реализация означает, что полученные файлы молча теряются.

**Особенности.** `confidence: INFO` стоит осознанно: это не дефект безопасности, а неработающая функциональность. Из разбора SberApps: приёмника там нет вовсе, примитив построен и не используется. Полезно как инвентарь — показывает, задействована ли передача файлов.

## Группа F. Буфер обмена

### `cerber-host-clipboard-bypass` · высокая · HIGH

Приложение обращается к платформенному `ClipboardManager` напрямую. SDK даёт `CerberCopyPasteBuffer` с лимитом длины, результатом записи и подменой буфера на заглушку при сворачивании приложения — прямое обращение обходит всё это целиком.

**Текст правила (en).** The app uses the platform ClipboardManager directly. CerberSDK provides CerberCopyPasteBuffer, which enforces a size limit, returns a WriteResult so truncation and failure can be handled, and replaces the clipboard with a stub when the app goes to background while copying is forbidden. A direct ClipboardManager call bypasses all of that: copied data stays readable by every other application on the device, including after the app is backgrounded. Route clipboard writes through CerberCopyPasteBuffer.write() and check its result.

**Перевод.** Приложение использует платформенный `ClipboardManager` напрямую. CerberSDK предоставляет `CerberCopyPasteBuffer`, который ограничивает длину, возвращает `WriteResult` для обработки усечения и отказа и подменяет буфер заглушкой при сворачивании приложения, когда копирование запрещено. Прямой вызов `ClipboardManager` обходит всё это: скопированные данные остаются доступны любому другому приложению на устройстве, в том числе после сворачивания. Направьте запись в буфер через `CerberCopyPasteBuffer.write()` и проверяйте результат.

**Особенности.** Ловит получение менеджера по строковой константе службы, запись `setPrimaryClip` и все четыре формы сборки `ClipData`. Ложные, отложенные на доработку: шаблон получения менеджера срабатывает и когда приложение только **читает** буфер, а чтение — не та утечка, про которую правило. Пропуски: получение менеджера литералом класса — шаблон убран 2026-10-28, потому что литерал класса в шаблоне на этом движке матчит любой литерал класса и давал ложные сработки соседнему правилу. Потеря небольшая: в живом коде такое получение всегда сопровождается записью, которую правило ловит.

### `cerber-clipboard-write-result-ignored` · средняя · MEDIUM

Результат `CerberCopyPasteBuffer.write()` не проверен. Данные свыше 8192 байт молча усекаются, и приложение об этом не узнаёт.

**Текст правила (en).** CerberCopyPasteBuffer.write() returns WriteResult (SUCCESS, TRUNCATED, FAILURE) but the result is not checked. Data exceeding 8192 bytes will be silently truncated. Always check WriteResult to handle truncation and failure scenarios.

**Перевод.** `CerberCopyPasteBuffer.write()` возвращает `WriteResult` (`SUCCESS`, `TRUNCATED`, `FAILURE`), но результат не проверяется. Данные объёмом свыше 8192 байт будут молча усечены. Всегда проверяйте `WriteResult`, чтобы обрабатывать усечение и отказ.

**Особенности.** Самое объёмное правило набора: 45 исключений, перечисляющих все формы сверки результата — через `if`, через `when`, в `return`, с `==` и `!=`. Исключения подтверждены изоляцией 7 из 7. Известная особенность, отложенная на доработку: одна клетка молчит в общем файле и срабатывает в одиночном, до и после заворачивания исключений в область функции. Вероятно это проявление общего вопроса про текстовую идентичность совпадения, пункт 2 бэклога. Ложные: сверка, вынесенная в хелпер. **Требует разбора эксперта.**

## Группа G. Передача файлов

### `cerber-file-received-no-caller-check` · высокая · MEDIUM

`onFileReceived()` не сверяет параметр `callingPackage`. SDK сообщает приложению, кто прислал файл, — и если приложение это не проверяет, файл может подсунуть любое приложение через схему `cerber://transfer/`.

**Текст правила (en).** onFileReceived() does not verify the callingPackage parameter. CerberSDK provides the calling package identity — the host app must validate it against a whitelist to prevent unauthorized apps from injecting files via the cerber://transfer/ scheme.

**Перевод.** `onFileReceived()` не проверяет параметр `callingPackage`. CerberSDK передаёт имя вызывающего пакета — host-приложение обязано сверить его с белым списком, чтобы сторонние приложения не могли подсунуть файл через схему `cerber://transfer/`.

**Особенности.** Исключения перечисляют сверку через равенство, неравенство, `equals`, `contains` и вхождение `in`. Формы с отрицанием добавлены на этапе 1: без них правило срабатывало на корректной сверке с ранним выходом. Ложные: сверка вынесена в хелпер. **Требует разбора эксперта.** Важно знать при триаже: `CerberTransferActivity` из SDK экспортирована штатно с `mimeType */*`, то есть канал открыт по дизайну, и сверка на стороне приложения — единственная защита.

### `cerber-received-file-not-copied` · средняя · MEDIUM

`onFileReceived()` работает по URI отправителя, не скопировав файл в свою песочницу. SDK проверяет схему URI, отбивает path traversal и сверяет подпись содержимого, но сам файл остаётся во владении отправителя — и тот может подменить содержимое после того, как проверки прошли.

**Текст правила (en).** onFileReceived() works with the sender's URI without copying the file into the app sandbox first. CerberSDK validates the URI scheme, rejects path traversal and checks the content signature before handing the URI over, but the file itself still belongs to the sending application, which can replace its content after those checks have passed. Copy the stream into the app private storage and work with the copy.

**Перевод.** `onFileReceived()` работает с URI отправителя, не скопировав файл в песочницу приложения. CerberSDK проверяет схему URI, отбивает обход пути и сверяет подпись содержимого перед передачей URI, но сам файл по-прежнему принадлежит отправляющему приложению, которое может подменить содержимое уже после этих проверок. Скопируйте поток в приватное хранилище приложения и работайте с копией.

**Особенности.** Классический TOCTOU. Исключения перечисляют пять форм копирования: `copyTo`, `openInputStream`, `FileOutputStream`, `readBytes`, `writeBytes`. Ложные: копирование вынесено в хелпер, которого правило не видит. **Требует разбора эксперта.** Пропуски: тело-выражение `override fun onFileReceived(...) = handle(uri)` — шаблон требует блочного тела, это измеренная слепая зона движка.

### `cerber-transfer-temp-file-not-deleted` · низкая · LOW

Файл выдан наружу через `FileProvider` и в той же функции не удалён. Wipe от SDK кеш приложения не чистит, значит забытый временный файл переживёт выход из системы.

**Текст правила (en).** A file is handed out through FileProvider but is not deleted in the same function. Files prepared for transfer live in the app cache directory and are the app responsibility: a CerberSDK wipe does not clear them, so a forgotten temp file outlives logout and remains readable to anything that can reach the app private storage. Delete the file once the transfer completes.

**Перевод.** Файл выдаётся через `FileProvider`, но в этой же функции не удаляется. Файлы, подготовленные к передаче, лежат в кеше приложения и находятся в его ответственности: wipe от CerberSDK их не удаляет, поэтому забытый временный файл переживает выход из системы и остаётся доступен всему, что дотягивается до приватного хранилища приложения. Удаляйте файл после завершения передачи.

**Особенности.** `INFO` и `LOW` стоят осознанно: правило видит только тело одной функции, а удаление в живом коде часто делается в коллбэке, в корутине, в `finally` или периодической чисткой кеша. Это подсказка к обзору, а не утверждение дефекта. **Требует разбора эксперта** практически всегда. Пропуски: свой `ContentProvider` вместо `FileProvider`; файл, оставленный в кеше и наружу не выданный вовсе.

## Группа H. Каналы вывода данных

Четыре правила, построенные на развороте формулировки. Проверки методики сформулированы как «на чувствительных экранах защита включена», а статика не знает, что чувствительно. Но каждый из этих каналов открывается **действием приложения**: в Compose текст по умолчанию не выделяется, `ACTION_SEND` сам не появляется, печать и запись во внешнее хранилище тоже. Поэтому правила ищут не отсутствие защиты, а присутствие включателя, и оценку риска оставляют человеку — это сказано прямо в тексте каждого.

### `cerber-text-selection-enabled` · средняя · HIGH

**Текст правила (en).** The app explicitly enables text selection. In Compose text is NOT selectable by default, and the system drag-and-drop of text starts only from a selection, so this one action opens both the copy channel and the drag channel at once. A custom TextToolbar can also re-add a Copy item where the platform menu would not offer one. Check that no K2 data is shown inside this scope. If it is, remove SelectionContainer, set textIsSelectable to false, and do not override the text toolbar.

**Перевод.** Приложение явно включает выделение текста. В Compose текст по умолчанию **не** выделяется, а системное перетаскивание текста запускается только из выделения — значит одно это действие открывает сразу и канал копирования, и канал перетаскивания. Своя реализация `TextToolbar` может вернуть пункт «копировать» там, где платформенное меню его не даёт. Проверьте, что в этой области не показываются данные К2. Если показываются — уберите `SelectionContainer`, выставьте `textIsSelectable` в `false` и не переопределяйте панель инструментов текста.

**Особенности.** Ловит `SelectionContainer`, свойство и сеттер на View, запуск перетаскивания, переопределение панели. Ложные по построению: выделение на справочном тексте, который пользователю и надо скопировать, — телефон поддержки, код ошибки. Это не дефект правила: решение о чувствительности принимает человек, и сообщение об этом просит прямо. Ложные-дефект, отложено: шаблон на переопределение панели срабатывает и когда оно **убирает** «копировать», то есть защитная мера попадает в находки как риск. Пропуски: текст в `WebView`, где выделение включено по умолчанию и ни один якорь правила не появляется, — самый значимый пропуск; выделение через тему или `Modifier`.

### `cerber-system-share-channel` · средняя · HIGH

**Текст правила (en).** The app sends data into the system share dialog. The recipient of ACTION_SEND is chosen by the user from every application installed on the device, and CerberSDK has no control over that channel: once the payload leaves through the chooser it is outside the corporate perimeter. Check that no K2 data or files can reach this call. For corporate file exchange use the SDK transfer mechanism instead, which verifies the receiving package.

**Перевод.** Приложение отправляет данные в системный диалог «Поделиться». Получателя `ACTION_SEND` выбирает пользователь из всех установленных на устройстве приложений, и CerberSDK этот канал не контролирует: уйдя через диалог выбора, содержимое оказывается вне корпоративного периметра. Проверьте, что в этот вызов не могут попасть данные или файлы К2. Для корпоративного обмена файлами используйте механизм передачи SDK — он сверяет принимающий пакет.

**Особенности.** Ловит конструктор с `ACTION_SEND` и `ACTION_SEND_MULTIPLE`, диалог выбора, присвоение действия свойством и сеттером, вложение через `EXTRA_STREAM`, а также идиому androidx `ShareCompat.IntentBuilder` — она добавлена 2026-10-28 с привязкой к точке входа цепочки, чтобы на один шаринг приходила одна находка, а не три. Ложные по построению: шаринг публичной ссылки, контакта поддержки. Пропуски: действие, пришедшее переменной вместо литерала.

### `cerber-external-storage-export` · средняя · HIGH

**Текст правила (en).** The app writes to external storage or to the shared media collections. Anything placed there leaves the application sandbox: it survives app uninstall, is readable by other applications holding storage permissions, and is not removed by a CerberSDK wipe. Check that no K2 data or files can reach this call. Files prepared for SDK transfer belong in the private cache directory, not in external storage.

**Перевод.** Приложение пишет во внешнее хранилище или в общие медиа-коллекции. Всё, что там оказалось, покидает песочницу приложения: переживает его удаление, доступно другим приложениям с разрешением на хранилище и не удаляется wipe'ом CerberSDK. Проверьте, что в этот вызов не могут попасть данные или файлы К2. Файлы, подготовленные к передаче через SDK, должны лежать в приватном кеше, а не во внешнем хранилище.

**Особенности.** Шире всех в группе: медиа-коллекции изображений, загрузок, аудио, видео; внешние каталоги приложения в единственном и множественном числе; корень и публичный каталог хранилища; Storage Access Framework через `ACTION_CREATE_DOCUMENT` и контракт результата; путь во внешнее хранилище, собранный строкой. Важно при триаже: **сработки неравноценны.** `getExternalFilesDir` — каталог приложения, удаляется при деинсталляции. `Environment.getExternalStorageDirectory()` и медиа-коллекции — общее хранилище, читаемое другими приложениями. Смотреть, какой якорь сработал. Намеренно не добавлен `ACTION_OPEN_DOCUMENT_TREE`: это входящий API, и правило срабатывало бы на каждой функции «прикрепить документ».

### `cerber-print-channel` · средняя · HIGH

**Текст правила (en).** The app exposes a print path. Printing renders screen or document content outside the window, which is how it escapes FLAG_SECURE: CerberSDK sets FLAG_SECURE on every activity through an ActivityLifecycleCallbacks hook, and that flag stops screenshots and screen recording but not the print pipeline. WebView.createPrintDocumentAdapter() is the same channel for web content. Check that no K2 data can reach this call.

**Перевод.** Приложение открывает путь печати. Печать выводит содержимое экрана или документа за пределы окна, и именно так она уходит из-под `FLAG_SECURE`: CerberSDK ставит `FLAG_SECURE` на каждую активность через перехват жизненного цикла, и этот флаг останавливает скриншоты и запись экрана, но не конвейер печати. `WebView.createPrintDocumentAdapter()` — тот же канал для веб-содержимого. Проверьте, что в этот вызов не могут попасть данные К2.

**Особенности.** Ловит `PrintHelper.printBitmap`, печать из WebView и получение службы печати по строковой константе. Ложные по построению: печать документа, которую требует бизнес-процесс. Пропуски: `PrintManager.print(...)`, вызванный на поле или переменной, — шаблон `$PM.print(...)` убран сознательно, он матчил любой `.print()`. Шаблон с литералом класса тоже убран 2026-10-28: именно он дал ложную сработку на получении менеджера буфера обмена, потому что литерал класса в шаблоне на этом движке тип не ограничивает.

## Группа I. Сеть

### `cerber-direct-okhttp-client` · средняя · MEDIUM

Клиент OkHttp создаётся в коде приложения напрямую, минуя фабрики SDK с корпоративным trust manager и перехватчиком пиннинга.

**Текст правила (en).** OkHttpClient is created directly in host-app code. CerberSDK provides pre-configured clients via NetworkUtils.getDefaultOkHttpClient(), getAuthorizedOtpOkHttpClient() and getSberMagOkHttpClient(), with the corporate trust manager and the pinning interceptor already attached. Triage this finding by what the same function does with pinning. (Далее текст перечисляет пять форм и разбор по каждой; полностью — в самом правиле.)

**Перевод.** `OkHttpClient` создаётся напрямую в коде host-приложения. CerberSDK предоставляет готовые клиенты через `NetworkUtils.getDefaultOkHttpClient()`, `getAuthorizedOtpOkHttpClient()` и `getSberMagOkHttpClient()`, в которых корпоративный trust manager и перехватчик пиннинга уже подключены. Разбирайте находку по тому, что та же функция делает с пиннингом. Пиннинга нет вовсе: корпоративный трафик защищён только доверием платформы — самый тяжёлый случай. `addInterceptor` с перехватчиком пиннинга SDK: пиннинг работает прикладным перехватчиком, то есть сертификат сверяется **после** `chain.proceed()`, когда рукопожатие завершено, а тело запроса, клиентский сертификат и корпоративные заголовки уже отправлены — перенесите в `addNetworkInterceptor`. Свой `CertificatePinner`: технически строже механизма SDK, потому что нативный пиннер обрывает соединение до отправки запроса, но пины придётся вращать руками, тогда как SDK скачивает наборы в рантайме — зафиксируйте владельца ротации или переходите на SDK. Свой trust manager или константный `HostnameVerifier` в той же цепочке: проверка TLS отключена, и это обесценивает и пиннинг, и корпоративные корни независимо от всего остального.

**Особенности.** Сообщение триажное по замыслу: отличить «пиннинга нет» от «пиннинг не на своём месте» статика может, а оценить риск — нет. Логика исключений проверена котлиновской матрицей изоляции 18 из 18, ложных не было ни в одном прогоне. **Но позитивный шаблон матчится ненадёжно: 8 клеток из 12 в Kotlin, 12 из 16 в Java.** Три синтаксические гипотезы проверены измерением и опровергнуты. Поэтому правило **не годится как блокирующий гейт** — молчание ничего не означает. Как информационная находка годится: каждая сработка настоящая. Подробности в бэклоге, пункт 3.

## Группа J. Своё вместо механизмов SDK

### `cerber-host-pin-screen` · средняя · MEDIUM

**Текст правила (en).** The app appears to implement its own PIN entry. The whole PIN flow belongs to CerberSDK: entry screen, PIN policy (five digits, not all equal, not a run), the attempt counter and the wipe on exhaustion, and the key material derived from it. A second PIN surface in the host app means PIN handling outside the SDK crypto and outside those limits, and the SDK cannot see or rate-limit it. Use the SDK authorization flow instead. If this screen is a certified crypto container mandated separately, record that as an accepted exception for this application.

**Перевод.** Похоже, приложение реализует свой ввод PIN. Весь поток PIN принадлежит CerberSDK: экран ввода, политика пина (пять цифр, не одинаковые, не по возрастанию), счётчик попыток и wipe при их исчерпании, а также ключевой материал, выводимый из пина. Второй экран PIN в host-приложении означает обработку пина вне крипто SDK и вне этих ограничений, причём SDK его не видит и ограничить частоту попыток не может. Используйте поток авторизации SDK. Если этот экран — сертифицированный криптоконтейнер, предписанный отдельно, зафиксируйте это как согласованное исключение для данного приложения.

**Особенности.** Ловит клавиатуру числового пароля в Compose, маскирование ввода и `inputType` на View. Ложные по построению, широкие: `PasswordVisualTransformation()` используется для **любого** маскированного поля — пароль, CVV, одноразовый код; то же с `NumberPassword`. **Требует разбора эксперта** почти всегда. Главный пропуск, признанный границей правила: настоящая PIN-клавиатура — это сетка из десяти кнопок, без `inputType` и без `KeyboardType` вообще. Правило ловит ленивую реализацию через поле ввода, а не ту, которую пишут на самом деле. Переделывать бессмысленно: десять кнопок на экране статикой от калькулятора не отличаются.

### `cerber-own-certificate-expiry-logic` · средняя · MEDIUM

**Текст правила (en).** The app reads or computes certificate validity itself. Certificate lifetime and reissue are owned by CerberSDK together with the server: the SDK holds the client certificate, decides when it must be renewed and performs the renewal. Any parallel expiry logic in the host app will drift from that decision, and the usual outcome is an app that considers the certificate valid after the SDK has already invalidated it, or the reverse. Remove the local calculation and rely on the SDK authorization state.

**Перевод.** Приложение само читает или вычисляет срок действия сертификата. Срок жизни и перевыпуск сертификата — зона CerberSDK вместе с сервером: SDK хранит клиентский сертификат, решает, когда его надо обновить, и выполняет обновление. Любая параллельная логика срока в host-приложении разойдётся с этим решением, и обычный исход — приложение считает сертификат действительным после того, как SDK его уже отозвал, либо наоборот. Уберите локальный расчёт и опирайтесь на состояние авторизации SDK.

**Особенности.** Ловит чтение `notAfter`, `notBefore` и вызов `checkValidity`. Ложные, отложенные на доработку: чтение `notAfter`, чтобы **показать** дату пользователю, — это интерфейс, а не параллельная логика, и правило их не различает. То же с `checkValidity()` внутри своего `TrustManager`, делающего законный пиннинг. **Требует разбора эксперта.** Пропуск хуже самого дефекта: жёстко заданный интервал перевыпуска, который к объекту сертификата не обращается вовсе — «обновлять каждые 90 дней от установки». Правило построено на API сертификата и такую логику не увидит, хотя расходится она с серверным решением сильнее всего.

## Группа K. Идентификация на своём IPC

Оба правила применимы только к приложениям со своим межпроцессным интерфейсом, и условность получается бесплатно: якорем служит сама поверхность IPC, поэтому на приложениях без неё правила молчат сами. Открыт ли этот интерфейс наружу, отвечает манифестное правило `cerber-multiprocess-ipc-exported`.

### `cerber-ipc-identity-fallback-open` · высокая · MEDIUM

**Текст правила (en).** The caller identity check is wrapped in a try/catch whose handler does not reject the request. When the UID cannot be resolved or the signing certificate is unavailable, swallowing the exception means the request continues as if the caller had been verified — the failure mode is fail-open, and an attacker only needs to make the check throw. The handler must reject: throw a SecurityException, or return null or false, before any payload is processed.

**Перевод.** Проверка идентичности вызывающего обёрнута в `try`/`catch`, обработчик которого не отвергает запрос. Когда UID не удаётся разрешить или подписной сертификат недоступен, проглоченное исключение означает, что запрос продолжается как если бы вызывающий был проверен: режим отказа — fail-open, и атакующему достаточно добиться, чтобы проверка бросила исключение. Обработчик обязан отвергать: бросать `SecurityException` либо возвращать `null` или `false`, до любой обработки содержимого запроса.

**Особенности.** Ловит `try`/`catch` вокруг трёх источников идентичности и четыре формы `runCatching` с подстановкой `true` при неудаче — последние добавлены 2026-10-28, потому что в Kotlin fail-open чаще пишут именно так, без ключевых слов `try` и `catch`. Форма с подстановкой `false` правилом не ловится намеренно: это отказ, а не пропуск, и клетка `E03-TN-3` это подтвердила. Ложные: отказ записан формой, которой нет в исключениях — `return emptyList()`, `finish()`, свой `Result.Error`. **Требует разбора эксперта**: посмотреть, что делает обработчик.

### `cerber-ipc-identity-from-payload` · высокая · LOW

**Текст правила (en).** The caller identity appears to be taken from the request payload. A value carried inside an Intent extra or a Bundle is supplied by the caller and can be set to anything, so it identifies nobody. Only values the system fills in can be trusted: Binder.getCallingUid(), ContentProvider.getCallingPackage() and Activity.getCallingPackage(). Resolve the caller through one of those and compare it against a fingerprint whitelist.

**Перевод.** Похоже, идентичность вызывающего берётся из содержимого запроса. Значение, лежащее в extra интента или в `Bundle`, передаёт сам вызывающий, и установить он может что угодно — такое значение не идентифицирует никого. Доверять можно только тому, что подставляет система: `Binder.getCallingUid()`, `ContentProvider.getCallingPackage()` и `Activity.getCallingPackage()`. Разрешите вызывающего через один из них и сверьте с белым списком отпечатков.

**Особенности.** `confidence: LOW` отражает суть правила: оно опознаёт идентификацию по **имени ключа**, а не по использованию значения. Ложные по построению и широкие: имя ключа совпало, а значение используется для аналитики или как «с какого экрана пришли». **Требует разбора эксперта** всегда. Пропуск измерен прямо: клетка `E04-PROBE-1` с ключом `x_origin` промолчала — любое нейтральное имя уводит проверку из-под правила. То есть правило ловит только тех, кто назвал ключ очевидно.

## Группа L. Инвентарь

### `cerber-trusted-components-usage-marker` · низкая · MEDIUM

**Текст правила (en).** Cerber trustedComponents API is used outside CerberSDK library code. Host-app must call delegate.trustedComponents.getFromCache() or delegate.trustedComponents.download() to use Cerber trusted components / SSL pinning integration. If this marker rule has zero findings in host-app sources, trusted components integration is missing.

**Перевод.** API доверенных компонентов Cerber используется вне кода библиотеки CerberSDK. Host-приложение должно вызывать `delegate.trustedComponents.getFromCache()` или `delegate.trustedComponents.download()`, чтобы задействовать интеграцию доверенных компонентов и SSL-пиннинга. Если у этого правила-маркера ноль находок в исходниках host-приложения, интеграция доверенных компонентов отсутствует.

**Особенности.** Единственное правило набора, у которого **ценность в молчании**: ноль находок означает, что интеграция доверенных компонентов не сделана. Это обратная логика по сравнению со всем остальным набором, и в отчёте её надо подавать отдельно, иначе отсутствие строки прочитают как «всё в порядке». Важная оговорка на фоне общего вопроса из вводной части: если движок действительно не выдаёт повторы одинакового текста, то для этого правила одна находка по-прежнему означает «интеграция есть», и вывод не ломается.

---

# Java

27 правил. У каждого есть котлиновский близнец, и **смысл, критичность и ложные у них те же** — поэтому ниже не повторяется описание, а указывается близнец и то, чем джава-версия от него отличается.

Из 27 сообщений **17 совпадают с котлиновскими побайтово**, у остальных десяти расхождения перечислены в их пунктах. Перевод смотреть в пункте близнеца, кроме тех случаев, где расхождение существенное — там перевод дан отдельно.

Одиннадцать котлиновских правил джава-версии пока не имеют: это все правила этапа 4, кроме XML. Список — в `rulesTZ/ETAP-5-BACKLOG.md`, пункт 1.

## Высокая критичность

### `cerber-java-missing-sdk-application-interface` · высокая · HIGH
Близнец `cerber-missing-sdk-application-interface`. Сообщение побайтово то же. Отличие шаблона: `class $APP extends Application` вместо котлиновского супертипа с конструктором. Прогон этапа 2: сработало, ложных нет.

### `cerber-java-default-constructor-used` · высокая · HIGH
Близнец `cerber-default-constructor-used`. Сообщение то же плюс `new` перед конструктором. Правка этапа 2: добавлено исключение `pattern-not: new CerberSDKApplicationDelegate($ARG, ...)` — без него правило срабатывало на корректном вызове с десятью аргументами, потому что пустые круглые скобки движок трактует как любые аргументы. Свойство движка, от языка не зависит. Подтверждено клеткой `J05-TN-1`.

### `cerber-java-insecure-auth-mode` · высокая · HIGH
Близнец `cerber-insecure-auth-mode`. Сообщение побайтово то же. **Правка котлиновского близнеца сюда не переносилась**: там добавляли альтернативу с именованным аргументом, а в Java именованных аргументов нет, зато форма `new $CLASS(..., AuthorizationMode.$MODE, ...)` в правиле была изначально. Джава-версия для своего языка полная.

### `cerber-java-insecure-auth-mode-variable` · высокая · MEDIUM
Близнец `cerber-insecure-auth-mode-variable`. Сообщение отличается одним словом: `returned from a method` вместо `from a function`. Правка этапа 2, самая крупная в партии: режим привязан метапеременной `$MODE` напрямую, прежний регекс по широкому `$EXPR` не отсеивал `CERBER` вообще и правило срабатывало на корректном коде по построению. Перечисление модификаторов поля убрано: прогон показал, что движок поглощает их сам. Ветвь прямой передачи режима в конструктор **сохранена**, в отличие от Kotlin, где её удалили: в Java конструктор позиционный и `authorizationMode` объявлен первым параметром, поэтому исключение по первому аргументу здесь работает. Все восемь TP и все четыре TN подтверждены.

### `cerber-java-insecure-clipboard-disabled` · высокая · HIGH
Близнец `cerber-insecure-clipboard-disabled`, но **сообщение переписано** и критичность выше: у котлиновского `WARNING`, здесь `ERROR`.

**Текст правила (en).** CerberSDKApplicationDelegate is created with secureClipboard=false. Clipboard protection is disabled: data copied from the app is readable by any other application on the device. Set secureClipboard=true.

**Перевод.** `CerberSDKApplicationDelegate` создан с `secureClipboard=false`. Защита буфера обмена отключена: скопированное из приложения доступно любому другому приложению на устройстве. Установите `secureClipboard=true`.

**Отличие важное.** В этом правиле был найден дефект, которого у котлиновского близнеца быть не могло: оно искало `false` **третьим** аргументом конструктора, а третий параметр — `isolated`, не `secureClipboard`. Последствия были двусторонние: правило срабатывало на `isolated=false`, то есть на нормальном значении любой интеграции, и при этом не видело настоящий `secureClipboard=false`. Котлиновская версия работает именованным аргументом и к порядку не чувствительна. Исправлено 2026-10-27, позиция шестая, подтверждено клетками `J10-TP-1`, `J10-TN-2`, `J10-TN-3`.

### `cerber-java-secure-initial-check-disabled` · высокая · HIGH
Близнец `cerber-secure-initial-check-disabled`. Сообщение побайтово то же. Позиционное правило, проверяет четвёртый аргумент — позиция верна, сверена с реальной подписью конструктора.

### `cerber-java-file-received-no-caller-check` · высокая · MEDIUM
Близнец `cerber-file-received-no-caller-check`. Сообщение побайтово то же. Правка этапа 2: добавлены четыре исключения на ранний выход через отрицание, включая отрицаемый `equals` — для `String` в Java это основная форма сверки, и без исключения правило срабатывало на корректном коде. Все пять TN подтверждены.

### `cerber-java-no-auth-detected-ignored` · высокая · MEDIUM
Близнец `cerber-no-auth-detected-ignored`. Сообщение побайтово то же. Правка этапа 4: добавлено `pattern-not-inside: interface $I { ... }` — движок считает абстрактное объявление `void onNoAuthDetected();` совпадением с шаблоном пустого тела, и правило срабатывало на объявлении интерфейса. Подтверждено прогоном 2026-10-27.

### `cerber-java-root-detected-no-termination` · высокая · MEDIUM
Близнец `cerber-root-detected-no-termination`. Отличие сообщения: вместо котлиновского `exitProcess()` перечислены `System.exit()` и `Runtime.getRuntime().exit()`. Исключения перечисляют шесть форм завершения, все подтверждены клетками `J19-TN-1`…`TN-3`. Правка этапа 4: добавлено исключение на объявление в интерфейсе.

### `cerber-java-root-detection-bypass-attempt` · высокая · MEDIUM
Близнец `cerber-root-detection-bypass-attempt`. Сообщение побайтово то же. Правка этапа 4: исключение на объявление в интерфейсе. Та же неточность формулировки, что у близнеца: сообщение говорит про пустую реализацию, а пустые `{ }` в шаблоне движок трактует как любое тело.

### `cerber-secure-check-wrong-state-comparison-java` · высокая · HIGH
Близнец `cerber-secure-check-wrong-state-comparison`. Сообщение побайтово то же. **Покрытие здесь выше котлиновского**: сверка результата через `equals()` — это вызов метода, а не бинарное выражение, поэтому она сопоставляется, и правило ловит три формы дефекта вместо одной. Это измеренное отличие в пользу Java, записано в `OGRANICHENIYA-SASTAV.md`.

### `cerber-java-activity-missing-auth-onresume` · высокая · HIGH
Близнец `cerber-activity-missing-auth-onresume`. Сообщение побайтово то же. Та же неточность формулировки и тот же массовый источник ложных: экран без чувствительных данных. **Требует разбора эксперта.** Клетка `J01-PROBE-1` подтвердила: без переопределения `onResume` правило молчит, а срабатывает `activity-no-lifecycle-override`.

### `cerber-debug-bypass-checker-present-java` · высокая · HIGH
Близнец `cerber-debug-bypass-checker-present`. **Ограничения в сообщении другие, и это содержательное отличие.**

**Текст правила, отличающаяся часть (en).** KNOWN LIMITATIONS, measured on sastav 2026-10-23: a method reference (DebugBypassChecker::shouldBypassRootEmulatorCheck) is not reported, because there is no call expression to match; a call made through reflection by method name is not reported either. A call made through a static import IS reported.

**Перевод отличающейся части.** Известные ограничения, измерены на составе 2026-10-23: ссылка на метод (`DebugBypassChecker::shouldBypassRootEmulatorCheck`) в находки не попадает, потому что сопоставлять нечего — выражения вызова нет; вызов рефлексией по имени метода тоже не попадает. Вызов через статический импорт, напротив, находится.

**Отличие в пользу Java.** У котлиновской версии главный пропуск — вызов внутри геттера свойства, обёртка в свойство полностью обходит правило. В Java геттер это обычный метод, и он обходится, так что этого пропуска здесь нет. Остаётся ссылка на метод: достаточно передать `DebugBypassChecker::shouldBypassRootEmulatorCheck` как `Supplier`, и правило молчит. Обход рабочий, записан в `OGRANICHENIYA-SASTAV.md`.

### `cerber-flag-secure-cleared-java` · высокая · HIGH
Близнец `cerber-flag-secure-cleared`. **В сообщении добавлено ограничение**, которого нет у котлиновского.

**Текст правила, добавленная часть (en).** KNOWN LIMITATION: removal of the flag through a bitmask assignment (flags &= ~FLAG_SECURE) is not reported, the engine does not support binary expressions in patterns.

**Перевод.** Известное ограничение: снятие флага через присваивание с битовой маской (`flags &= ~FLAG_SECURE`) в находки не попадает — движок не поддерживает бинарные выражения в шаблонах.

**Особенность.** Ограничение одинаково действует в обоих языках, просто в джава-версии оно честно вписано в сообщение. Котлиновской версии такую же оговорку стоит добавить — это в бэклоге, пункт 7.

## Средняя критичность

### `cerber-java-activity-no-lifecycle-override` · средняя · MEDIUM
Близнец `cerber-activity-no-lifecycle-override`. Сообщение побайтово то же. **Здесь измерена ложная сработка, которой у близнеца пока не видели**: правило сработало на базовом классе с пустым телом. Попытка закрыть это исключением на пустое тело класса **убила правило целиком** — пустые `{ }` движок трактует как любое тело, и правило дало ноль находок вместо трёх. Исключения откачены 2026-10-28, ложная оставлена как известная. **Требует разбора эксперта**: отличить базовый класс от экрана.

### `cerber-java-auth-requested-but-not-observed` · средняя · MEDIUM
Близнец `cerber-auth-requested-but-not-observed`. Сообщение побайтово то же. Неожиданность прогона: клетка `J03-PROBE-1` с наблюдением в отдельном классе **промолчала**, хотя должна была дать ложную сработку. Для нас это хорошо, но механизм не выяснен, и возможно классовое исключение шире, чем написано в правиле — тогда правило недобирает. В бэклоге как открытый вопрос.

### `cerber-java-missing-delegate-creation` · средняя · MEDIUM
Близнец `cerber-missing-delegate-creation`. Отличие сообщения: `via a delegate field` вместо `via delegation` — в Java делегирования языком нет, делегат лежит полем. Те же ложные на DI. **Требует разбора эксперта.**

### `cerber-java-insecure-screen-disabled` · средняя · HIGH
Близнец `cerber-insecure-screen-disabled`. Сообщение побайтово то же. Позиционное правило, проверяет пятый аргумент — позиция верна.

### `cerber-java-passwordless-auth-disabled` · средняя · MEDIUM
Близнец `cerber-passwordless-auth-disabled`. Сообщение побайтово то же. Позиционное, проверяет последний аргумент — позиция верна.

### `cerber-java-non-production-build-type` · средняя · HIGH
Близнец `cerber-non-production-build-type`. Сообщение побайтово то же. Те же ложные: правило не различает варианты сборки. **Требует разбора эксперта.**

### `cerber-java-non-production-build-type-variable` · средняя · MEDIUM
Близнец `cerber-non-production-build-type-variable`. Отличие сообщения: `method` вместо `function`. Правки не потребовалось: правило перечисляет плохие значения положительно и просмотром вперёд не пользуется, в отличие от близнеца по режиму авторизации.

### `cerber-java-clipboard-write-result-ignored` · средняя · MEDIUM
Близнец `cerber-clipboard-write-result-ignored`. Сообщение побайтово то же. Правка этапа 2: добавлены двенадцать исключений на сверку результата в `return`, включая формы через `equals` — для enum в Java пишут и так, и так. Правка этапа 3: исключения завёрнуты в область метода, потому что голое многоточие на нулевом уровне сшивало объявление из одного метода со сверкой из другого ниже по файлу. Утечка подтверждена изоляцией, завёртка симптом не сняла — открытый вопрос, пункт 3 бэклога.

### `cerber-java-logout-callback-empty` · средняя · MEDIUM
Близнец `cerber-logout-callback-empty`. Сообщение побайтово то же. Правка этапа 4: исключение на объявление в интерфейсе. Это одно из двух правил группы, которым я добавил **только** это исключение, и именно поэтому их сработки на реальном пустом теле выжили, когда у четырёх других пропали. Так и выяснилось, какое из двух исключений работает.

### `cerber-java-user-changed-callback-empty` · средняя · MEDIUM
Близнец `cerber-user-changed-callback-empty`. Сообщение побайтово то же. Правка этапа 4: исключение на объявление в интерфейсе, добавленное после откатa лишнего.

### `cerber-java-file-received-callback-empty` · средняя · INFO
Близнец `cerber-file-received-callback-empty`. Сообщение побайтово то же. Отдельная история: в этом файле при разборе ТЗ остался посторонний текст, из-за которого файл не парсился, а одно невалидное правило обрывает весь прогон. Это и была «ошибка скана» этапа 2. Правило при этом не менялось ни на букву.

### `cerber-java-direct-okhttp-client` · средняя · MEDIUM
Близнец `cerber-direct-okhttp-client`. Отличие сообщения: `method` вместо `function`, и **нет оговорки про геттер свойства** — в Java геттер это обычный метод, и он обходится, что подтверждено клеткой `OJ-PROBE-1`. То есть покрытие джава-версии выше котлиновской.

Второе отличие в конструкции: исключение по области метода здесь **надёжно**, а в Kotlin было ненадёжно. В Kotlin сборка клиента почти всегда пишется телом-выражением, к которому шаблон с блочным телом не подходит, и это дало ложные сработки в матрице изоляции. В Java тел-выражений нет.

Та же проблема с позитивным шаблоном: 12 клеток из 16. **Как блокирующий гейт не годится.** Пункт 3 бэклога.

## Низкая критичность

### `cerber-trusted-components-usage-marker-java` · низкая · MEDIUM
Близнец `cerber-trusted-components-usage-marker`. Отличие сообщения: вызовы указаны без префикса `delegate.`, потому что в Java обращение к полю делегата пишется иначе. Та же обратная логика: **ценность в молчании**, ноль находок означает отсутствие интеграции доверенных компонентов.

---

# XML: манифест и ресурсы

Шесть правил. Они читают то, чего в коде не существует вовсе: нельзя узнать из Kotlin, экспортирована ли активность, — это строка в манифесте. То же с процессом, разрешением, строковыми ресурсами и разметкой.

**Важно про размещение.** В саставе набор один, но внутри развилка на два языковых блока. Эти шесть правил лежат в котлиновском блоке, то есть приложение, которое проверяется джава-блоком, проверок манифеста и разметки **не получит**. Их надо продублировать — подробности и два открытых вопроса в `rulesTZ/ETAP-5-BACKLOG.md`, пункт 1a.

**Контроль, что XML вообще попал в область скана.** На любом реальном приложении `cerber-exported-component-no-permission` обязано дать хотя бы одну находку: launcher-активность всегда экспортирована и permission у неё быть не может. Если правило молчит на реальном коде — манифест в скан не попал, и молчание остальных пяти ничего не значит.

## `cerber-exported-component-no-permission` · высокая · HIGH

Компонент экспортирован без `android:permission`. Дотянуться до него может любое приложение на устройстве, и платформа никакой проверки не делает.

**Текст правила (en).** The component is exported without android:permission. Any application on the device can reach it, with no identity check performed by the platform. CerberSDK cannot protect a surface it does not know about: the signature verification the SDK performs covers its own channels, not the host app components. Either set android:exported="false" if the component is only used inside the app, or declare android:permission and verify the caller in the handler through Binder.getCallingUid() against a fingerprint whitelist.

**Перевод.** Компонент экспортирован без `android:permission`. Дотянуться до него может любое приложение на устройстве, причём платформа никакой проверки вызывающего не выполняет. CerberSDK не может защитить поверхность, о которой не знает: сверка подписи, которую делает SDK, покрывает его собственные каналы, а не компоненты host-приложения. Либо выставьте `android:exported="false"`, если компонент используется только внутри приложения, либо объявите `android:permission` и проверяйте вызывающего в обработчике через `Binder.getCallingUid()` по белому списку отпечатков.

**Особенности.** **Единственное правило этапа 4 с доказанным истинным срабатыванием на реальном приложении**: в SberApps 3.6.2 `CerberStoreProvider` объявлен `exported="true"` без permission, да ещё на ненеймспейснутом authority `ru.sberbank.cerbersdk`. Исключения: все четыре вида permission, включая раздельные на чтение и запись у провайдера, плюс `CerberTransferActivity` из SDK — она экспортирована штатно, с `action cerber://transfer/<bundleId>` и `mimeType */*`.

**Ложные, и это главное к доработке.** Launcher-активность обязана быть экспортирована, и permission у неё быть не может — правило сработает на ней **в каждом приложении**. Нужно исключение на `<intent-filter>` с `MAIN` и `LAUNCHER`, это первый пункт работ этапа 5: гарантированная находка в каждом отчёте обесценивает правило. Туда же компоненты сторонних библиотек, приходящие в манифест при сборке.

**Пропуск, существенный.** `android:permission` с `protectionLevel="normal"`: правило видит атрибут и молчит, а разрешение нормального уровня выдаётся любому запросившему без согласия пользователя. Это не пропуск перечисления, а смена логики — нужно второе правило на объявление `<permission>`.

## `cerber-multiprocess-ipc-exported` · высокая · HIGH

Компонент работает в приватном процессе этого приложения и при этом экспортирован. Ведущее двоеточие в `android:process` — гарантия платформы, что процесс принадлежит этому же приложению, значит это собственный межпроцессный интерфейс, открытый наружу.

**Текст правила (en).** A component that runs in a private process of this application is also exported. The leading colon in android:process means the process belongs to this application only, so this is the app own inter-process surface — and exporting it opens that surface to every application on the device. This is the case where caller identity must be verified in code, because signature-level permissions cannot be used across corporate applications signed with different keys. Review the handlers of this component: the caller must be resolved through Binder.getCallingUid() and matched against a fingerprint whitelist, before any payload is parsed. If the component is only needed inside the app, set android:exported="false" and the question disappears.

**Перевод.** Компонент, работающий в приватном процессе этого приложения, при этом экспортирован. Ведущее двоеточие в `android:process` означает, что процесс принадлежит только этому приложению, то есть это его собственная межпроцессная поверхность — и экспорт открывает её любому приложению на устройстве. Именно в этом случае идентичность вызывающего надо проверять в коде, потому что разрешения уровня подписи между корпоративными приложениями, подписанными разными ключами, применить нельзя. Проверьте обработчики этого компонента: вызывающий должен разрешаться через `Binder.getCallingUid()` и сверяться с белым списком отпечатков до разбора содержимого запроса. Если компонент нужен только внутри приложения, выставьте `android:exported="false"` — и вопрос снимается.

**Особенности.** Правило не проверяет содержимое обработчиков, а **задаёт область применимости** проверок идентификации: отвечает на вопрос, есть ли у приложения свой межпроцессный интерфейс и открыт ли он наружу. Это лучше, чем просили в методике, где условие «применимо только к приложениям со своим IPC» человек должен был проверять сам.

Нюанс, который правило учитывает: если компонент с приватным процессом объявлен `exported="false"`, дотянуться до него может только своё приложение с тем же UID, и `Binder.getCallingUid()` вернёт собственный UID — проверять нечего. Поэтому правило требует **обоих** атрибутов, а не одного.

Живые примеры из SberApps 3.6.2, оба в правильной позиции: `ClickstreamServer` — `exported="false"` с `android:process=":clickstream.appserver"`, `IsolatedService` — `isolatedProcess="true"`, `useAppZygote="true"`, `exported="false"`. Правило на них молчит, и это верно.

**Ложные:** узкие. Компонент, сознательно опубликованный как внешний API с разрешением уровня `dangerous`, — правило на permission не смотрит намеренно, потому что подписные разрешения в этой экосистеме не работают. **Пропуски:** `android:process` из манифеста библиотеки; поверхность, открытая не компонентом, а `Binder`, переданным через результат активности. **Связать манифест с классом обработчика правило не может** — межфайловой связи у движка нет, это делает человек.

## `cerber-isolated-service-not-isolated` · высокая · HIGH

Служба `IsolatedService` из SDK объявлена без `android:isolatedProcess="true"`. Изолированный процесс выполняет детект root отдельно от основного, и Frida, присоединённая к основному процессу, его не перехватит.

**Текст правила (en).** CerberSDK IsolatedService is declared without android:isolatedProcess="true". The isolated process runs root detection in a separate process that cannot be hooked by Frida attached to the main app process. Without isolation, an attacker can hook native detection functions from the main process.

**Перевод.** Служба `IsolatedService` из CerberSDK объявлена без `android:isolatedProcess="true"`. Изолированный процесс выполняет детект root отдельно, и его нельзя перехватить Frida, присоединённой к основному процессу приложения. Без изоляции атакующий может перехватить нативные функции детекта из основного процесса.

**Особенности.** Первое XML-правило набора и образец идиомы: самозакрывающийся элемент с атрибутами плюс `metavariable-regex` по имени и `pattern-not` на проверяемый атрибут. Отработало с первого прогона этапа 1 без правок. Единственное правило набора, которое **одно на оба языка** по смыслу и не требует близнеца.

Неточность формулировки, отложенная: правило ловит и форму, где изоляция задана через `android:process` с отдельным именем вместо `isolatedProcess` — это не эквивалент, защита слабее, но в сообщении об этом не сказано. Подтверждено клеткой `K10-PROBE-1`.

## `cerber-text-selection-enabled-layout` · средняя · HIGH

**Текст правила (en).** The layout enables text selection on a view. Selection is what opens both the copy channel and the system text drag-and-drop, and CerberSDK does not see either of them: the clipboard protections apply to CerberCopyPasteBuffer, not to a selection the platform handles itself. Check that no K2 data is displayed in this view. If it is, remove android:textIsSelectable or set it to false.

**Перевод.** Разметка включает выделение текста на элементе. Выделение открывает сразу и канал копирования, и системное перетаскивание текста, причём CerberSDK не видит ни того, ни другого: защита буфера действует на `CerberCopyPasteBuffer`, а не на выделение, которое платформа обрабатывает сама. Проверьте, что в этом элементе не показываются данные К2. Если показываются — уберите `android:textIsSelectable` или выставьте его в `false`.

**Особенности.** Разметочная половина правила `cerber-text-selection-enabled`. Нужна потому, что в крупных и старых корпоративных приложениях большая часть экранов — View-based XML, а не Compose, и котлиновское правило там ничего не найдёт. Ловит атрибут на `TextView`, `EditText` и `AppCompatTextView`. Ложные по построению те же, что у котлиновского.

**Пропуск, признанный непокрываемым.** Атрибут, заданный стилем или темой: `<item name="android:textIsSelectable">true</item>` внутри `<style>` действует точно так же, но не находится. Проверено дважды отрицательно — ни плоским шаблоном, ни вложенным в `<style>`. Значение, лежащее текстовым узлом внутри элемента, а не атрибутом, шаблоном не адресуется. Нерабочий шаблон из правила убран 2026-10-28, чтобы не создавать ложного впечатления покрытия. Записано пунктом 7 в `OGRANICHENIYA-SASTAV.md`.

## `cerber-host-pin-screen-layout` · средняя · MEDIUM

**Текст правила (en).** The layout declares a numeric password field, which suggests the app implements its own PIN entry. The whole PIN flow belongs to CerberSDK: entry screen, PIN policy, the attempt counter and the wipe on exhaustion, and the key material derived from the PIN. A second PIN surface in the host app means PIN handling outside the SDK crypto and outside those limits. Use the SDK authorization flow instead. If this screen is a certified crypto container mandated separately, record that as an accepted exception for this application.

**Перевод.** Разметка объявляет поле числового пароля, что указывает на собственный ввод PIN в приложении. Весь поток PIN принадлежит CerberSDK: экран ввода, политика пина, счётчик попыток и wipe при их исчерпании, а также ключевой материал, выводимый из пина. Второй экран PIN в host-приложении означает обработку пина вне крипто SDK и вне этих ограничений. Используйте поток авторизации SDK. Если этот экран — сертифицированный криптоконтейнер, предписанный отдельно, зафиксируйте это как согласованное исключение для данного приложения.

**Особенности.** Разметочная половина правила `cerber-host-pin-screen`. Ловит `android:inputType="numberPassword"` на `EditText`, `AppCompatEditText` и `TextInputEditText`. Ложные те же и такие же широкие: так объявляется поле CVV и поле одноразового кода. **Требует разбора эксперта** почти всегда. Тот же непокрываемый пропуск через стиль, и та же граница правила: настоящая PIN-клавиатура — сетка кнопок, а не поле ввода.

## `cerber-signature-whitelist-plaintext` · средняя · MEDIUM

**Текст правила (en).** A certificate fingerprint is stored as a plain string resource. Values in res/values are not protected by anything: they sit in the APK in clear text and are trivial to read and to patch. A whitelist an attacker can rewrite is not a whitelist. Keep signing fingerprints in code, or in a configuration that is itself signed and whose integrity is verified before use.

**Перевод.** Отпечаток сертификата хранится обычной строкой в ресурсах. Значения в `res/values` ничем не защищены: они лежат в APK открытым текстом, их легко прочитать и легко подменить. Белый список, который атакующий может переписать, белым списком не является. Храните отпечатки подписи в коде либо в конфигурации, которая сама подписана и целостность которой проверяется перед использованием.

**Особенности.** Опознаёт отпечаток **по форме значения**, а не по смыслу: 64 hex-символа, 32 пары через двоеточие, либо base64 из 44 символов с завершающим знаком равенства. Ложные отсюда и следуют: 64 hex-символа — это не только отпечаток, но и API-ключ, build id, хеш ассета. **Требует разбора эксперта**: посмотреть, как значение используется. Пропуски: base64 без выравнивания (43 символа без знака равенства); отпечаток в `res/raw`, в ассете или в `BuildConfig` через gradle — это не XML, правило там не смотрит; отпечаток, разбитый на несколько строк.
