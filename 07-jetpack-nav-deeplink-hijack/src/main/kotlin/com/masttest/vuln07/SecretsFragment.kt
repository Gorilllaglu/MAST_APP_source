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
 * VULN: «секретный» экран доступный через https-deeplink
 * `https://app.example.com/internal/secrets`. autoVerify не настроен
 * → любая web-страница или приложение может задеплинклинкать сюда.
 */
class SecretsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.w("Vuln07-Secrets", "SecretsFragment entered via https deeplink — no assetlinks check")
        return TextView(requireContext()).apply {
            text = "INTERNAL SECRETS\n\napi_key=sk_live_abc\njwt_signing=verysecret"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
}
