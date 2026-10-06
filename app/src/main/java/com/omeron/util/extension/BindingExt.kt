package com.omeron.util.extension

import androidx.core.view.isVisible
import com.omeron.R
import com.omeron.databinding.IncludePostMetricsBinding

fun IncludePostMetricsBinding.setSaved(saved: Boolean) {
    buttonSave.isChecked = saved
    buttonSave.contentDescription = buttonSave.context.getString(
        if (saved) R.string.post_unsave_description else R.string.post_save_description
    )
}

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
