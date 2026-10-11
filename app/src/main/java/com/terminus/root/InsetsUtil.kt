package com.terminus.root

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

object InsetsUtil {
    private const val MIN_BOTTOM_DP = 24

    fun apply(activity: Activity) {
        val root: View = activity.findViewById(android.R.id.content) ?: return
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottom =
                max(bars.bottom, (MIN_BOTTOM_DP * view.resources.displayMetrics.density).toInt())
            view.setPadding(0, bars.top, 0, bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}