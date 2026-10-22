package ru.corp.app.security

import ru.sberbank.cerbersdk.sdk_framework.DebugBypassChecker

/**
 * Изоляция разрыва, найденного прогоном: вызов внутри геттера свойства
 * во вложенном object (маркер R1-TN-5) правило не нашло. Причина может быть
 * в геттере с телом-выражением, в блочном геттере или во вложенности object.
 * Эти четыре маркера разводят причины по одной.
 */

/** R1-GAP-1. Геттер с телом-выражением, верхний уровень файла. */
val topLevelExpressionGetter: Boolean
    get() = DebugBypassChecker.shouldBypassRootEmulatorCheck()

/** R1-GAP-2. Геттер с блочным телом, верхний уровень файла. */
val topLevelBlockGetter: Boolean
    get() {
        return DebugBypassChecker.shouldBypassRootEmulatorCheck()
    }

/** R1-GAP-3. Обычный метод во вложенном object: проверяет саму вложенность. */
object OuterHolder {
    object InnerHolder {
        fun resolve(): Boolean {
            return DebugBypassChecker.shouldBypassRootEmulatorCheck()
        }
    }
}

/** R1-GAP-4. Геттер с телом-выражением внутри class, не object. */
class GateHolder {
    val disabled: Boolean
        get() = DebugBypassChecker.shouldBypassRootEmulatorCheck()
}

/** R1-GAP-5. Функция с телом-выражением: форма без блока, но не свойство. */
fun expressionBodyFun(): Boolean = DebugBypassChecker.shouldBypassRootEmulatorCheck()
