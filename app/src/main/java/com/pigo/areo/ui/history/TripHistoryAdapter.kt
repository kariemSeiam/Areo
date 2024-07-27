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
import com.pigo.areo.ui.current_trip.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            startTime.text = "Start: ${formatTime(trip.startTime)}"
            endTime.text = "End: ${formatTime(trip.endTime)}"

            mapView.getMapAsync { googleMap ->
                googleMap.clear()
                // Enable dark map style if night mode is enabled
                if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
                    googleMap.setMapStyle(
                        MapStyleOptions.loadRawResourceStyle(
                            itemView.context,
                            R.raw.map_style
                        )
                    )
                }

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

    override fun getItemCount(): Int = minOf(trips.size, 5)

    private fun formatTime(time: Long?): String {
        return if (time != null) {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            sdf.format(Date(time))
        } else {
            "N/A"
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
}
