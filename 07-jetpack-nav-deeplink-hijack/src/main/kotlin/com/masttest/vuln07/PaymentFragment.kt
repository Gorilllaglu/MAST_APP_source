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
 * VULN: «защищённый» payment-экран с параметром `cardId`.
 * Deeplink `vuln07://app/payment/{cardId}` — параметр передаётся
 * прямо из URI, никакой авторизации владельца карты нет.
 *
 * Атака:
 *   adb shell am start -W -a android.intent.action.VIEW \
 *     -d "vuln07://app/payment/4242424242424242"
 *
 * → PaymentFragment откроется с cardId="4242…" в Bundle.
 */
class PaymentFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val cardId = arguments?.getString("cardId") ?: "0000"
        Log.w("Vuln07-Payment", "PaymentFragment entered cardId=$cardId (no owner-auth)")
        return TextView(requireContext()).apply {
            text = "PAYMENT SCREEN\n\nCard: $cardId\n(чувствительная операция, " +
                "должна требовать auth)"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
}
