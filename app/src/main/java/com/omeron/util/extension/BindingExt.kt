package com.omeron.util.extension

import androidx.core.view.isVisible
import com.omeron.R
import com.omeron.databinding.IncludePostMetricsBinding

fun IncludePostMetricsBinding.setRatio(ratio: Int) {
    ratio.takeUnless { it == -1 }?.let {
        textPostRatio.run {
            isVisible = true
            text = context.getString(R.string.post_ratio, it)
        }
    } ?: run {
        textPostRatio.isVisible = false
    }
}
