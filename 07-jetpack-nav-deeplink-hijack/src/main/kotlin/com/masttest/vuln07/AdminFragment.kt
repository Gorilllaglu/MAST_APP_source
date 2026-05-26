package com.masttest.vuln07

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

/**
 * VULN: «защищённый» admin-экран. В нормальной архитектуре он
 * должен быть доступен только после успешного login. Здесь же
 * — deeplink `vuln07://app/admin` в nav_graph даёт прямой
 * доступ извне.
 *
 * Атака:
 *   adb shell am start -W -a android.intent.action.VIEW \
 *     -d "vuln07://app/admin"
 *
 * → NavHostFragment автоматически переходит на AdminFragment
 *   минуя LoginFragment.
 */
class AdminFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.w("Vuln07-Admin", "AdminFragment entered — exposing admin features without auth")
        // Эмуляция чувствительной операции: «выгружаем» список пользователей
        val sensitive = listOf("alice (admin)", "bob (admin)", "carol (admin)")
        return TextView(requireContext()).apply {
            text = "ADMIN PANEL\n\nUsers:\n${sensitive.joinToString("\n")}"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
}
