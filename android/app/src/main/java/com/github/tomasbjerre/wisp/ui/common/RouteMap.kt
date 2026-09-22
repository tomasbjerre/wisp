package com.github.tomasbjerre.wisp.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.tomasbjerre.wisp.location.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline

private const val DEFAULT_ZOOM = 17.0
private val FALLBACK_CENTER = GeoPoint(0.0, 0.0)

/** Draws [route] on an OpenStreetMap tile view, following the latest point. */
@Composable
fun RouteMap(
    route: List<LatLon>,
    modifier: Modifier = Modifier,
) {
    val mapViewRef = remember { arrayOfNulls<MapView>(1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(DEFAULT_ZOOM)
                mapViewRef[0] = this
            }
        },
        update = { mapView ->
            mapView.overlays.clear()
            if (route.isNotEmpty()) {
                val points = route.map { GeoPoint(it.latitude, it.longitude) }
                mapView.overlays.add(Polyline().apply { setPoints(points) })
                mapView.controller.animateTo(points.last())
            } else {
                mapView.controller.setCenter(FALLBACK_CENTER)
            }
            mapView.invalidate()
        },
    )

    DisposableEffect(Unit) {
        onDispose { mapViewRef[0]?.onDetach() }
    }
}
