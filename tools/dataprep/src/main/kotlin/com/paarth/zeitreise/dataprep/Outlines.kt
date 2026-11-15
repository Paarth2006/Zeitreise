package com.paarth.zeitreise.dataprep

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.hypot

/** Fewest positions a closed ring needs: three corners and the repeated first one. */
private const val MIN_RING_POSITIONS = 4

/**
 * Simplifies and rounds every ring of a `Polygon` or `MultiPolygon`, returning it as a
 * `MultiPolygon`, or `null` when nothing of it survives even unsimplified.
 *
 * A place so small that simplifying would erase it entirely, such as Vatican City, is kept at full
 * detail instead; within a larger place, rings that collapse are tiny islets and are dropped.
 *
 * @param tolerance greatest distance, in degrees, a dropped vertex may stray from the kept line.
 * @param decimals decimal places kept per coordinate.
 */
internal fun simplifiedGeometry(geometry: JsonObject, tolerance: Double, decimals: Int): JsonObject? {
    val source = polygonsOf(geometry)
    val polygons = simplifiedPolygons(source, tolerance, decimals).ifEmpty { simplifiedPolygons(source, 0.0, decimals) }
    if (polygons.isEmpty()) return null
    return buildJsonObject {
        put("type", JsonPrimitive("MultiPolygon"))
        put("coordinates", buildJsonArray { polygons.forEach { add(JsonArray(it)) } })
    }
}

private fun simplifiedPolygons(
    polygons: List<List<List<DoubleArray>>>,
    tolerance: Double,
    decimals: Int,
): List<List<JsonArray>> = polygons
    .map { polygon -> polygon.mapNotNull { ring -> simplify(ring, tolerance, decimals) } }
    .filter { it.isNotEmpty() }

/** Every polygon of a `Polygon` or `MultiPolygon`, each a list of rings of positions. */
internal fun polygonsOf(geometry: JsonObject): List<List<List<DoubleArray>>> {
    val coordinates = geometry.getValue("coordinates").jsonArray
    val polygons = if (geometry.text("type") == "Polygon") listOf(coordinates) else coordinates.map { it.jsonArray }
    return polygons.map { polygon ->
        polygon.map { ring -> ring.jsonArray.map { position -> position.jsonArray.toPosition() } }
    }
}

internal fun positions(geometry: JsonObject): List<DoubleArray> = polygonsOf(geometry).flatten().flatten()

private fun JsonArray.toPosition(): DoubleArray =
    doubleArrayOf(this[0].jsonPrimitive.double, this[1].jsonPrimitive.double)

/**
 * Simplifies one ring with Ramer-Douglas-Peucker and rounds what is left, or returns `null` when
 * the ring collapses below a triangle.
 */
private fun simplify(ring: List<DoubleArray>, tolerance: Double, decimals: Int): JsonArray? {
    val keep = BooleanArray(ring.size)
    keep[0] = true
    keep[ring.lastIndex] = true
    val pending = ArrayDeque(listOf(0 to ring.lastIndex))
    while (pending.isNotEmpty()) {
        val (first, last) = pending.removeLast()
        val furthest = (first + 1 until last).maxByOrNull { distanceToSegment(ring[it], ring[first], ring[last]) }
        if (furthest != null && distanceToSegment(ring[furthest], ring[first], ring[last]) > tolerance) {
            keep[furthest] = true
            pending.addLast(first to furthest)
            pending.addLast(furthest to last)
        }
    }
    val kept = ring.filterIndexed { index, _ -> keep[index] }
    if (kept.size < MIN_RING_POSITIONS) return null
    return buildJsonArray {
        kept.forEach { position ->
            add(
                buildJsonArray {
                    add(JsonPrimitive(round(position[0], decimals)))
                    add(JsonPrimitive(round(position[1], decimals)))
                },
            )
        }
    }
}

/** Distance from [point] to the segment between [start] and [end], in degrees. */
private fun distanceToSegment(point: DoubleArray, start: DoubleArray, end: DoubleArray): Double {
    val dx = end[0] - start[0]
    val dy = end[1] - start[1]
    val lengthSquared = dx * dx + dy * dy
    val along = if (lengthSquared == 0.0) {
        0.0
    } else {
        (((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    }
    return hypot(point[0] - start[0] - along * dx, point[1] - start[1] - along * dy)
}
