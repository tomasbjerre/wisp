package com.github.tomasbjerre.wisp.ui.common

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import com.github.tomasbjerre.wisp.location.LatLon
import com.github.tomasbjerre.wisp.util.GeoUtils
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private const val DEFAULT_ZOOM = 17.0

// Before the route loads (a real gap: e.g. Detail's points load asynchronously after
// this screen first composes), there's nothing to center on yet. (0, 0) is open ocean,
// so zooming to street level there — as if it were a real position — flashed a
// full-bleed solid-color "ocean tile" for a frame or two. Zoomed all the way out, it
// reads as an unremarkable blank world map instead.
private const val FALLBACK_ZOOM = 2.0
private val FALLBACK_CENTER = GeoPoint(0.0, 0.0)

// See specs/ui-flows.md#2-tracking-active-recording: zooming out is capped so the map
// never shows the whole world, however far a pinch-out goes.
private const val MAX_ZOOM_OUT_WIDTH_METERS = 100_000.0

// See specs/accessibility.md#map-markers-and-route: a single line color isn't
// reliably visible against every map background, so the route gets a light
// halo behind a saturated line, and markers are solid dots with a white ring
// rather than relying on hue alone.
private const val ROUTE_COLOR = 0xFFD84315.toInt() // saturated deep orange
private val ROUTE_HALO_COLOR = Color.WHITE
private const val ROUTE_WIDTH_DP = 5f
private const val ROUTE_HALO_WIDTH_DP = 8f

private const val START_MARKER_COLOR = 0xFF2E7D32.toInt() // green
private const val END_MARKER_COLOR = 0xFFC62828.toInt() // red
private const val CURRENT_POSITION_COLOR = 0xFF1565C0.toInt() // blue
private const val MARKER_DIAMETER_DP = 18f
private const val MARKER_RING_DP = 2f

/**
 * Draws [route] on an OpenStreetMap tile view, with a start marker and an
 * end marker — the latter shown as a distinct "current position" marker
 * while [isLive] (see specs/ui-flows.md#2-tracking-active-recording).
 */
@Composable
fun RouteMap(
    route: List<LatLon>,
    isLive: Boolean = false,
    modifier: Modifier = Modifier,
    onMapViewReady: ((MapView) -> Unit)? = null,
) {
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }
    // Not Compose state on purpose (mirrors mapViewRef above) — flips once, the first
    // time real data arrives, and must never itself trigger a recomposition.
    val hasZoomedToRoute = remember { booleanArrayOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(FALLBACK_ZOOM)
                controller.setCenter(FALLBACK_CENTER)
                doOnLayout { setMinZoomLevel(GeoUtils.minZoomForWidthMeters(width, MAX_ZOOM_OUT_WIDTH_METERS)) }
                mapViewRef[0] = this
                onMapViewReady?.invoke(this)
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            if (route.isNotEmpty()) {
                val density = mapView.resources.displayMetrics.density
                val points = route.map { GeoPoint(it.latitude, it.longitude) }
                mapView.overlays.add(routeOverlay(points, ROUTE_HALO_COLOR, ROUTE_HALO_WIDTH_DP * density))
                mapView.overlays.add(routeOverlay(points, ROUTE_COLOR, ROUTE_WIDTH_DP * density))
                mapView.overlays.add(dotMarker(mapView, points.first(), START_MARKER_COLOR, density))
                val endColor = if (isLive) CURRENT_POSITION_COLOR else END_MARKER_COLOR
                mapView.overlays.add(dotMarker(mapView, points.last(), endColor, density))
                if (!hasZoomedToRoute[0]) {
                    // The very first time: jump straight there instead of animating — an
                    // animated pan from the fallback world view (still at street-level
                    // zoom by the time it starts) would fly across whatever ocean sits
                    // between (0, 0) and the real point, flashing ocean tiles the whole
                    // way. Later points are already nearby, so animate those as normal.
                    mapView.controller.setZoom(DEFAULT_ZOOM)
                    mapView.controller.setCenter(points.last())
                    hasZoomedToRoute[0] = true
                } else {
                    mapView.controller.animateTo(points.last())
                }
            }
            mapView.invalidate()
        },
    )

    DisposableEffect(Unit) {
        onDispose { mapViewRef[0]?.onDetach() }
    }
}

private fun routeOverlay(
    points: List<GeoPoint>,
    color: Int,
    widthPx: Float,
) = Polyline().apply {
    setPoints(points)
    outlinePaint.color = color
    outlinePaint.strokeWidth = widthPx
    outlinePaint.strokeCap = Paint.Cap.ROUND
}

private fun dotMarker(
    mapView: MapView,
    position: GeoPoint,
    color: Int,
    density: Float,
) = Marker(mapView).apply {
    setPosition(position)
    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    icon = dotDrawable(color, density)
    setInfoWindow(null)
}

private fun dotDrawable(
    fillColor: Int,
    density: Float,
): GradientDrawable {
    val diameterPx = (MARKER_DIAMETER_DP * density).toInt()
    val ringPx = (MARKER_RING_DP * density).toInt()
    return GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fillColor)
        setStroke(ringPx, Color.WHITE)
        setSize(diameterPx, diameterPx)
        setBounds(0, 0, diameterPx, diameterPx)
    }
}
