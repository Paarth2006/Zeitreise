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

/** Values per stored label rectangle, and the position of each edge within it. */
private const val RECT_STRIDE = 4
private const val LEFT = 0
private const val TOP = 1
private const val RIGHT = 2
private const val BOTTOM = 3

/** Space kept clear around each label, in pixels, so neighbouring names do not touch. */
private const val LABEL_GAP = 4f

/**
 * The names of a country's states, laid out once and placed on the globe every frame.
 *
 * Text is measured when the states load rather than per frame. Placement walks the states largest
 * first and skips any label that would overlap one already placed, so big states keep their names
 * and crowded small ones drop out until the user zooms in further. The selected state always
 * keeps its name.
 */
internal class SubdivisionLabels(
    private val subdivisions: List<Subdivision>,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    private val layouts: List<TextLayoutResult> = subdivisions.map { measurer.measure(it.name, style) }
    private val placed = FloatArray(subdivisions.size * RECT_STRIDE)

    /**
     * Draws every label that faces the viewer, fits on screen and does not overlap another. The
     * [selected] state's label is placed first, so it is never the one crowded out.
     */
    fun draw(scope: DrawScope, projection: GlobeProjection, alpha: Float, selected: Subdivision?) {
        var placedCount = 0
        val selectedIndex = subdivisions.indexOfFirst { it === selected }
        if (selectedIndex >= 0 && tryPlace(scope, projection, alpha, selectedIndex, placedCount)) placedCount++
        for (index in subdivisions.indices) {
            if (index != selectedIndex && tryPlace(scope, projection, alpha, index, placedCount)) placedCount++
        }
    }

    /** Draws label [index] if it faces the viewer, fits and overlaps none of the [placedCount] before it. */
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

/** Whether this rectangle lies wholly inside a canvas of [size]. */
private fun Rect.isInside(size: Size): Boolean = left >= 0f && top >= 0f && right <= size.width && bottom <= size.height
