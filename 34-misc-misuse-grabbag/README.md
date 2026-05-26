# 34 — Сборная солянка прочих ошибок

## APK после сборки

```bash
./gradlew :micro:34-misc-misuse-grabbag:assembleDebug
```

Файл: `micro/34-misc-misuse-grabbag/build/outputs/apk/debug/34-misc-misuse-grabbag-debug.apk`

## Что заложено в приложение

В одном app четыре разнокалиберные ошибки из «оставшегося хвоста»
правил.

1. **`Context.MODE_WORLD_WRITEABLE`** для SharedPreferences. Атрибут
   объявлен deprecated, но компилируется и попадает в bytecode.
   Любое приложение на устройстве сможет писать в наши
   shared_prefs. Категория R139, R061.
2. **`java.util.Random()` для генерации session-token'а.**
   `java.util.Random` — линейный конгруэнтный генератор. После
   нескольких observed-samples можно предсказать следующие. Для
   секретов нужен `SecureRandom`. Категория R128.
3. **IPC crash на невалидных данных.** `CrashOnBadDataActivity`
   объявлена `exported="true"`, читает `intent.getStringExtra("amount")`
   и сразу делает `.toInt()` — без `try/catch`. Любое стороннее
   приложение вызывает Activity с amount=`"abc"` и приложение падает
   с `NumberFormatException`. Категория R150.
4. **EditText для пароля без `inputType="textPassword"`.** В layout
   `activity_main.xml` есть `EditText` с `android:hint="Password"`
   но без `android:inputType="textPassword"`. Из-за этого:
   - Android keyboard учится на введённом значении и предлагает его
     в подсказках другим приложениям;
   - в скриншотах пароль виден текстом;
   - в `LogCat`-сессиях accessibility-сервисы могут его перехватить.
   Категория R074.

Расположение:

- `src/main/kotlin/com/masttest/vuln34/MainActivity.kt` — пп. 1, 2.
- `src/main/kotlin/com/masttest/vuln34/CrashOnBadDataActivity.kt` — п. 3.
- `src/main/res/layout/activity_main.xml` — п. 4 (EditText без inputType).

## Что должен найти сканер

Минимально достаточный отчёт — **четыре** finding'а:

1. `Context.MODE_WORLD_WRITEABLE` (или `MODE_WORLD_READABLE`) в
   вызовах `getSharedPreferences` / `openFileOutput`. Категория
   R139, R061.
2. `java.util.Random()` для генерации значения, использующегося в
   security-контексте (имена переменных `*token*`, `*secret*`,
   `*nonce*`, `*salt*`, `*iv*`). Категория R128.
3. `intent.get*Extra(...).toInt()` (или `.toLong()`, `.toDouble()`,
   `.toFloat()`, `parseInt(...)` в Java) без обёртки `try/catch` в
   `exported=true`-Activity. Категория R150.
4. `<EditText>` в layout с `android:hint="Password"`, `password`,
   `pin` или `cvv` (или с `id="*password*"`) и **без**
   `android:inputType="textPassword"`. Категория R074.

Допустимо если сканер выдаёт более общие сообщения, объединяющие
кейсы. Главное — все четыре проблемы должны быть видимы в отчёте.

**Чего сканер не должен делать**:

- Не должен флажить `Random` сам по себе — например, для
  `random.nextInt()` в неsecurity-контексте (анимации) это
  легитимно.
- Не должен флажить `EditText` для cardNumber c
  `android:inputType="number"` — это правильная конфигурация для
  не-секретного числового поля.
- Не должен флажить `MainActivity` (лаунчер).
