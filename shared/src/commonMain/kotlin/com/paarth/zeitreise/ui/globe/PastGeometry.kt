package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.globe.GlobeProjection

/** Border widths for realms, in pixels. */
private const val REALM_BORDER_WIDTH = 0.9f
private const val SELECTED_REALM_BORDER_WIDTH = 1.8f

/** The projected geometry of one historical map, like [GlobeGeometryCache] for today's map. */
internal class PastGeometry(map: HistoricalMap) {
    /** Realms in draw order, largest first, so small ones are drawn on top. */
    val realms: List<TerritoryGeometry> = map.realms.map(::TerritoryGeometry)

    /** Unclaimed land, drawn below the realms. */
    val unclaimed: List<OutlineGeometry> = map.unclaimedLand.map(::OutlineGeometry)
}

/** Draws a historical map, with [selected] in gold. */
internal fun DrawScope.drawPast(
    geometry: PastGeometry,
    projection: GlobeProjection,
    selected: Realm?,
    quiz: QuizGlobe?,
    palette: GlobePalette,
) {
    geometry.unclaimed.forEach { outline ->
        if (outline.update(projection)) drawPath(path = outline.path, color = palette.unclaimedLand)
    }
    geometry.realms.forEach { realm ->
        val isSelected = realm.territory === selected
        // A quiz about the past colours the answer and a wrong guess like it does countries.
        val fill = when {
            realm.territory === quiz?.answer -> palette.quizRight
            realm.territory === quiz?.wrongGuess -> palette.quizWrong
            isSelected -> palette.landSelected
            else -> palette.pastLand[realm.shade.mod(palette.pastLand.size)]
        }
        val border = if (isSelected) palette.borderSelected else palette.border
        val width = if (isSelected) SELECTED_REALM_BORDER_WIDTH else REALM_BORDER_WIDTH
        realm.update(projection).forEach { path ->
            drawPath(path = path, color = fill)
            drawPath(path = path, color = border, style = Stroke(width = width))
        }
    }
}
