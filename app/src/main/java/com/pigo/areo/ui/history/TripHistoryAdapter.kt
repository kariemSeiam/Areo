package com.pigo.areo.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.pigo.areo.R
import com.pigo.areo.data.model.CustomLatLng
import com.pigo.areo.data.model.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class TripHistoryAdapter(val trips: MutableList<Trip>, private val deleteTrip: (String) -> Unit) :
    RecyclerView.Adapter<TripHistoryAdapter.TripViewHolder>() {

    inner class TripViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val startTime: TextView = view.findViewById(R.id.startTime)
        val endTime: TextView = view.findViewById(R.id.endTime)
        val distance: TextView = view.findViewById(R.id.distance)
        val mapView: MapView = view.findViewById(R.id.mapView)

        init {
            mapView.onCreate(null)
        }

        fun bind(trip: Trip) {
            val matrixTrip = calculateTripMetrics(trip).toFormattedString()
            startTime.text = "Start: ${formatTime(trip.startTime)}"
            endTime.text = "End: ${formatTime(trip.endTime)}"
            distance.text = matrixTrip

            mapView.getMapAsync { googleMap ->
                googleMap.clear()
                googleMap.setMapStyle(
                    MapStyleOptions.loadRawResourceStyle(
                        itemView.context, R.raw.map_style
                    )
                )


                val polylineOptions = PolylineOptions()
                val boundsBuilder = LatLngBounds.Builder()

                if (trip.coordinates.isNotEmpty()) {
                    for (i in trip.coordinates.indices) {
                        val color = Color.parseColor("#FF6750A4")
                        val adjustedColor =
                            if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                                adjustColorForDarkMode(color)
                            } else {
                                color
                            }
                        val latLng = trip.coordinates[i].toLatLng()
                        polylineOptions.add(latLng).color(adjustedColor)
                        boundsBuilder.include(latLng)
                    }

                    googleMap.addPolyline(polylineOptions)

                    googleMap.addMarker(
                        MarkerOptions().position(trip.coordinates.first().toLatLng()).title("Start")
                    )
                    googleMap.addMarker(
                        MarkerOptions().position(trip.coordinates.last().toLatLng()).title("End")
                    )

                    val bounds = boundsBuilder.build()
                    val padding = 80
                    val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                    googleMap.moveCamera(cameraUpdate)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TripViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.recycler_trip_history_item, parent, false)
        return TripViewHolder(view)
    }

    override fun onBindViewHolder(holder: TripViewHolder, position: Int) {
        holder.bind(trips[position])
        holder.mapView.onResume()
    }

    override fun getItemCount(): Int = trips.size

    private fun formatTime(time: Long?): String {
        return if (time != null) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(time))
        } else {
            "N/A"
        }
    }

    fun TripMetrics.toFormattedString(): String {
        val totalDistanceStr = "%.2f km".format(totalDistance)
        val totalTimeStr = totalTime.toFormattedDuration()
        val averageSpeedStr = "%.2f km/h".format(averageSpeed)

        return """
        📍 Total Distance: $totalDistanceStr
        ⏰ Total Time: $totalTimeStr
        """.trimIndent()
    }

    fun Long.toFormattedDuration(): String {
        val hours = this / 3600000
        val minutes = (this % 3600000) / 60000
        val seconds = (this % 60000) / 1000

        return when {
            hours > 0 -> "%d hrs %d mins".format(hours, minutes)
            minutes > 0 -> "%d mins %d secs".format(minutes, seconds)
            else -> "%d secs".format(seconds)
        }
    }


    private fun adjustColorForDarkMode(color: Int): Int {
        val factor = 0.5f
        return Color.rgb(
            (Color.red(color) * factor).toInt(),
            (Color.green(color) * factor).toInt(),
            (Color.blue(color) * factor).toInt()
        )
    }

    data class TripMetrics(
        val totalDistance: Double,
        val totalTime: Long,
        val averageSpeed: Double,
        val waypoints: List<CustomLatLng>
    )

    fun calculateTripMetrics(trip: Trip): TripMetrics {
        val waypoints = trip.coordinates
        val startTime = trip.startTime
        val endTime = trip.endTime ?: System.currentTimeMillis()

        val totalDistance = calculateTotalDistance(waypoints)
        val totalTime = endTime - startTime
        val averageSpeed =
            if (totalTime > 0) (totalDistance / (totalTime / 1000.0)) * 3.6 else 0.0 // km/h

        return TripMetrics(totalDistance, totalTime, averageSpeed, waypoints)
    }

    private fun calculateTotalDistance(waypoints: List<CustomLatLng>): Double {
        return waypoints.zipWithNext { start, end -> start.haversineDistance(end) }.sum()
    }

    private fun CustomLatLng.haversineDistance(other: CustomLatLng): Double {
        val earthRadiusKm = 6371 // Earth's radius in kilometers

        val lat1Rad = Math.toRadians(this.latitude)
        val lon1Rad = Math.toRadians(this.longitude)
        val lat2Rad = Math.toRadians(other.latitude)
        val lon2Rad = Math.toRadians(other.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = sin(dLat / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }

}
