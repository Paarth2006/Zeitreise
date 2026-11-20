package com.paarth.zeitreise.ui.globe

import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.paarth.zeitreise.domain.HistoricalMap
import com.paarth.zeitreise.domain.Realm
import com.paarth.zeitreise.globe.GlobeProjection

/** Width of a realm's border, and of the selected realm's, in pixels. */
private const val REALM_BORDER_WIDTH = 0.9f
private const val SELECTED_REALM_BORDER_WIDTH = 1.8f

/**
 * The projected geometry of one historical map, held across frames the way [GlobeGeometryCache]
 * holds today's countries.
 */
internal class PastGeometry(map: HistoricalMap) {
    /** Realms in draw order, largest first, so small neighbours end up on top. */
    val realms: List<TerritoryGeometry> = map.realms.map(::TerritoryGeometry)

    /** Land no realm held, drawn beneath the realms. */
    val unclaimed: List<OutlineGeometry> = map.unclaimedLand.map(::OutlineGeometry)
}

/** Draws a historical map: unclaimed land first, then every realm, with [selected] in gold. */
internal fun DrawScope.drawPast(
    geometry: PastGeometry,
    projection: GlobeProjection,
    selected: Realm?,
    palette: GlobePalette,
) {
    geometry.unclaimed.forEach { outline ->
        if (outline.update(projection)) drawPath(path = outline.path, color = palette.unclaimedLand)
    }
    geometry.realms.forEach { realm ->
        val isSelected = realm.territory === selected
        val fill = if (isSelected) palette.landSelected else palette.pastLand[realm.shade.mod(palette.pastLand.size)]
        val border = if (isSelected) palette.borderSelected else palette.border
        val width = if (isSelected) SELECTED_REALM_BORDER_WIDTH else REALM_BORDER_WIDTH
        realm.update(projection).forEach { path ->
            drawPath(path = path, color = fill)
            drawPath(path = path, color = border, style = Stroke(width = width))
        }
    }
}
