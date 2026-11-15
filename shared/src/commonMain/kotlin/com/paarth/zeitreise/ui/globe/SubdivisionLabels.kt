package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.geometry.Offset
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
 * and crowded small ones drop out until the user zooms in further.
 */
internal class SubdivisionLabels(
    private val subdivisions: List<Subdivision>,
    measurer: TextMeasurer,
    style: TextStyle,
) {
    private val layouts: List<TextLayoutResult> = subdivisions.map { measurer.measure(it.name, style) }
    private val placed = FloatArray(subdivisions.size * RECT_STRIDE)

    /** Draws every label that faces the viewer, fits on screen and does not overlap another. */
    fun draw(scope: DrawScope, projection: GlobeProjection, alpha: Float) {
        var placedCount = 0
        subdivisions.forEachIndexed { index, subdivision ->
            val anchor = projection.project(subdivision.labelPoint) ?: return@forEachIndexed
            val layout = layouts[index]
            val left = anchor.x.toFloat() - layout.size.width / 2f
            val top = anchor.y.toFloat() - layout.size.height / 2f
            val right = left + layout.size.width
            val bottom = top + layout.size.height
            val onScreen = left >= 0f && top >= 0f && right <= scope.size.width && bottom <= scope.size.height
            if (!onScreen || overlapsPlaced(placedCount, left, top, right, bottom)) return@forEachIndexed

            val slot = placedCount * RECT_STRIDE
            placed[slot + LEFT] = left - LABEL_GAP
            placed[slot + TOP] = top - LABEL_GAP
            placed[slot + RIGHT] = right + LABEL_GAP
            placed[slot + BOTTOM] = bottom + LABEL_GAP
            placedCount++
            scope.drawText(layout, topLeft = Offset(left, top), alpha = alpha)
        }
    }

    private fun overlapsPlaced(count: Int, left: Float, top: Float, right: Float, bottom: Float): Boolean {
        for (index in 0 until count) {
            val slot = index * RECT_STRIDE
            val separate = right < placed[slot + LEFT] ||
                left > placed[slot + RIGHT] ||
                bottom < placed[slot + TOP] ||
                top > placed[slot + BOTTOM]
            if (!separate) return true
        }
        return false
    }
}
