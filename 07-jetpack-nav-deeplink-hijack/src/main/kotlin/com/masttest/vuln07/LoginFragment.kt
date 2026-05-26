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
 * LoginFragment — стартовая destination в nav_graph.
 * По задумке должна выполнять auth-проверку перед допуском
 * к admin/payment/secrets экранам. На практике никакой auth
 * не делает — но даже если бы делала, deeplink'и обходят её
 * полностью (см. README).
 */
class LoginFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        Log.d("Vuln07-Login", "LoginFragment entered (start destination)")
        return TextView(requireContext()).apply {
            text = "Login screen (start destination)"
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
    }
}
