package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import com.paarth.zeitreise.domain.Subdivision
import com.paarth.zeitreise.globe.GlobeProjection

/** Values per stored label rectangle and the index of each edge. */
private const val RECT_STRIDE = 4
private const val LEFT = 0
private const val TOP = 1
private const val RIGHT = 2
private const val BOTTOM = 3

/** Padding around each label in pixels, so names don't touch. */
private const val LABEL_GAP = 4f

/**
 * The state names on the globe. Text is measured once when the states load. Labels are placed
 * largest state first and skipped if they would overlap, so small states only show when zoomed in.
 * The selected state always shows its name.
 */
internal class SubdivisionLabels(
    private val subdivisions: List<Subdivision>,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    private val layouts: List<TextLayoutResult> = subdivisions.map { measurer.measure(it.name, style) }
    private val placed = FloatArray(subdivisions.size * RECT_STRIDE)

    /** Draws every visible label that doesn't overlap another. [selected] is placed first. */
    fun draw(scope: DrawScope, projection: GlobeProjection, alpha: Float, selected: Subdivision?) {
        var placedCount = 0
        val selectedIndex = subdivisions.indexOfFirst { it === selected }
        if (selectedIndex >= 0 && tryPlace(scope, projection, alpha, selectedIndex, placedCount)) placedCount++
        for (index in subdivisions.indices) {
            if (index != selectedIndex && tryPlace(scope, projection, alpha, index, placedCount)) placedCount++
        }
    }

    /** Draws label [index] if it's visible and doesn't overlap the [placedCount] labels already placed. */
    private fun tryPlace(
        scope: DrawScope,
        projection: GlobeProjection,
        alpha: Float,
        index: Int,
        placedCount: Int,
    ): Boolean {
        val anchor = projection.project(subdivisions[index].labelPoint) ?: return false
        val layout = layouts[index]
        val bounds = Rect(
            offset = Offset(anchor.x.toFloat() - layout.size.width / 2f, anchor.y.toFloat() - layout.size.height / 2f),
            size = Size(layout.size.width.toFloat(), layout.size.height.toFloat()),
        )
        val fits = bounds.isInside(scope.size) && !overlapsPlaced(placedCount, bounds)
        if (fits) {
            val slot = placedCount * RECT_STRIDE
            placed[slot + LEFT] = bounds.left - LABEL_GAP
            placed[slot + TOP] = bounds.top - LABEL_GAP
            placed[slot + RIGHT] = bounds.right + LABEL_GAP
            placed[slot + BOTTOM] = bounds.bottom + LABEL_GAP
            scope.drawText(layout, topLeft = bounds.topLeft, alpha = alpha)
        }
        return fits
    }

    private fun overlapsPlaced(count: Int, bounds: Rect): Boolean {
        for (index in 0 until count) {
            val slot = index * RECT_STRIDE
            val separate = bounds.right < placed[slot + LEFT] ||
                bounds.left > placed[slot + RIGHT] ||
                bounds.bottom < placed[slot + TOP] ||
                bounds.top > placed[slot + BOTTOM]
            if (!separate) return true
        }
        return false
    }
}

/** Whether this rectangle is fully inside a canvas of [size]. */
private fun Rect.isInside(size: Size): Boolean = left >= 0f && top >= 0f && right <= size.width && bottom <= size.height
