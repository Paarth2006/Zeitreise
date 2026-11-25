package com.paarth.zeitreise.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp

/** A globe icon: a circle with the equator and one meridian, drawn with lines [strokeWidth] wide. */
internal fun DrawScope.drawGlobeGlyph(tint: Color, strokeWidth: Dp) {
    val stroke = Stroke(width = strokeWidth.toPx())
    val radius = size.minDimension / 2 - stroke.width
    drawCircle(tint, radius = radius, style = stroke)
    drawLine(tint, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), stroke.width)
    val meridian = Size(radius, radius * 2)
    drawOval(tint, topLeft = Offset(center.x - meridian.width / 2, center.y - radius), size = meridian, style = stroke)
}
