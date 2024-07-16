package com.pigo.areo.utils

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object SphericalUtil {

    /**
     * Computes the distance between two LatLng points, in meters.
     */
    fun computeDistanceBetween(from: LatLng, to: LatLng): Double {
        val fromLat = Math.toRadians(from.latitude)
        val fromLng = Math.toRadians(from.longitude)
        val toLat = Math.toRadians(to.latitude)
        val toLng = Math.toRadians(to.longitude)

        val earthRadius = 6371009.0 // Earth's radius in meters

        val dLat = toLat - fromLat
        val dLng = toLng - fromLng

        val a = sin(dLat / 2).pow(2) + cos(fromLat) * cos(toLat) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * Computes the initial bearing (angle) between two LatLng points, in degrees.
     */
    fun computeHeading(from: LatLng, to: LatLng): Double {
        val fromLat = Math.toRadians(from.latitude)
        val fromLng = Math.toRadians(from.longitude)
        val toLat = Math.toRadians(to.latitude)
        val toLng = Math.toRadians(to.longitude)

        val dLng = toLng - fromLng

        val y = sin(dLng) * cos(toLat)
        val x = cos(fromLat) * sin(toLat) - sin(fromLat) * cos(toLat) * cos(dLng)
        var heading = atan2(y, x)

        heading = Math.toDegrees(heading)
        heading = (heading + 360) % 360

        return heading
    }

}


