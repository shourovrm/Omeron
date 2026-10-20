package com.omeron.ui.you

import com.omeron.data.model.preferences.ProfileTab

/**
 * The pager position the profile screen opens on. The value comes from a navigation argument, so
 * an out-of-range number (a stale deep link, say) falls back to the nearest real tab instead of
 * crashing the pager.
 */
fun initialProfileTabPosition(requestedPosition: Int): Int {
    return requestedPosition.coerceIn(0, ProfileTab.values().lastIndex)
}
