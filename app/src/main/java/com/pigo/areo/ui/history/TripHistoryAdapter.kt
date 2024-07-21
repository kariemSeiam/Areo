package com.pigo.areo.ui.history

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLngBounds
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
        val mapView: MapView = view.findViewById(R.id.mapView)

        init {
            mapView.onCreate(null)
        }

        fun bind(trip: Trip) {
            startTime.text = "Start: ${formatTime(trip.startTime)}"
            endTime.text = "End: ${formatTime(trip.endTime)}"
            mapView.getMapAsync { googleMap ->
                // Clear any previous polylines and markers
                googleMap.clear()

                // Display route with colored lines based on speed
                val polylineOptions = PolylineOptions()
                val boundsBuilder = LatLngBounds.Builder()

                for (i in trip.coordinates.indices) {
                    val color = getColorBasedOnSpeed(trip.speeds[i])
                    polylineOptions.add(trip.coordinates[i]).color(color)
                    boundsBuilder.include(trip.coordinates[i])
                }

                googleMap.addPolyline(polylineOptions)

                // Add markers for start and end points
                googleMap.addMarker(
                    MarkerOptions().position(trip.coordinates.first()).title("Start")
                )
                googleMap.addMarker(MarkerOptions().position(trip.coordinates.last()).title("End"))

                // Adjust camera to fit the entire route
                val bounds = boundsBuilder.build()
                val padding = 50 // offset from edges of the map in pixels
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                googleMap.moveCamera(cameraUpdate)
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

    private fun getColorBasedOnSpeed(speed: Float): Int {
        return when {
            speed < 20 -> Color.GREEN
            speed < 40 -> Color.YELLOW
            else -> Color.RED
        }
    }
}
