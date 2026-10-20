package com.omeron.ui.profile

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import com.omeron.R
import com.omeron.data.model.SavedItem

/** The id a saved row is selected by: the post id, or the comment name. */
val SavedItem.rowId: String
    get() = when (this) {
        is SavedItem.Post -> post.id
        is SavedItem.Comment -> comment.name
    }

/**
 * What a profile list adapter knows about selection: whether its tab is in selection mode, which
 * ids are selected, and how to turn a tap into a toggle. The state is owned by
 * [ProfileSelectionViewModel]; this class only mirrors it for the rows.
 *
 * [onToggleRow] is called with a row id, both to select a row first (long press) and to toggle it.
 */
class ProfileRowSelection(private val onToggleRow: (rowId: String) -> Unit) {

    var isActive: Boolean = false
        private set

    private var selectedIds: Set<String> = emptySet()

    /** Returns true when the state changed, so the adapter knows its rows need a new look. */
    fun update(isActive: Boolean, selectedIds: Set<String>): Boolean {
        if (this.isActive == isActive && this.selectedIds == selectedIds) return false
        this.isActive = isActive
        this.selectedIds = selectedIds
        return true
    }

    /** A tap toggles the row while selecting, and runs [normalAction] otherwise. */
    fun onTap(rowId: String, normalAction: () -> Unit) {
        if (isActive) onToggleRow(rowId) else normalAction()
    }

    /** A long press starts selecting with this row, or toggles it when already selecting. */
    fun onLongPress(rowId: String) {
        onToggleRow(rowId)
    }

    /** Gives [itemView] the look of a row in the current mode: a leading box and a tint. */
    fun style(itemView: View, rowId: String) {
        val baseStartPadding = itemView.getTag(R.id.tag_row_base_padding_start) as? Int
            ?: itemView.paddingStart.also { itemView.setTag(R.id.tag_row_base_padding_start, it) }

        val extraStartPadding = if (isActive) itemView.resources.getDimensionPixelSize(R.dimen.space_32) else 0
        ViewCompat.setPaddingRelative(
            itemView,
            baseStartPadding + extraStartPadding,
            itemView.paddingTop,
            itemView.paddingEnd,
            itemView.paddingBottom
        )

        itemView.foreground = if (isActive) {
            SelectionMarkDrawable(itemView.context, isSelected = rowId in selectedIds)
        } else {
            null
        }
    }
}

/**
 * Drawn over a row while selecting: a ticked or empty box at the start edge and, on a selected
 * row, a 2dp outline in the marker colour. Post rows are square, so the outline has square
 * corners. It is a foreground so the row layouts stay as they are.
 */
private class SelectionMarkDrawable(context: Context, isSelected: Boolean) : Drawable() {

    private val resources = context.resources

    private val boxSize = resources.getDimensionPixelSize(R.dimen.space_24)

    private val boxInset = resources.getDimensionPixelSize(R.dimen.space_8)

    private val outlineWidth = resources.getDimension(R.dimen.divider_thickness) * 2

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = outlineWidth
        color = if (isSelected) MaterialColors.getColor(context, R.attr.omeronMarker, 0) else 0
    }

    private val box: Drawable = checkNotNull(
        AppCompatResources.getDrawable(
            context,
            if (isSelected) R.drawable.ic_check_box else R.drawable.ic_check_box_outline_blank
        )
    ).mutate().also {
        val tintAttribute = if (isSelected) R.attr.omeronMarker else R.attr.colorOnSurfaceVariant
        DrawableCompat.setTint(it, MaterialColors.getColor(context, tintAttribute, 0))
    }

    override fun draw(canvas: Canvas) {
        val area = bounds
        if (outlinePaint.color != 0) {
            // Inset by half the stroke so the whole line falls inside the row.
            val outlineArea = RectF(area).apply { inset(outlineWidth / 2, outlineWidth / 2) }
            canvas.drawRect(outlineArea, outlinePaint)
        }

        val boxTop = area.centerY() - boxSize / 2
        box.setBounds(
            area.left + boxInset,
            boxTop,
            area.left + boxInset + boxSize,
            boxTop + boxSize
        )
        box.draw(canvas)
    }

    override fun setAlpha(alpha: Int) {
        // Always drawn at full strength.
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Colours come from the theme.
    }

    @Deprecated("Required by Drawable", ReplaceWith("PixelFormat.TRANSLUCENT"))
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
