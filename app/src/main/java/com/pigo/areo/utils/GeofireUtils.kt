package com.pigo.areo.utils

import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource


fun GeoFire.setLocationTask(key: String, location: GeoLocation): Task<Void> {
    val taskCompletionSource = TaskCompletionSource<Void>()
    this.setLocation(key, location) { _, error ->
        if (error != null) {
            taskCompletionSource.setException(error.toException())
        } else {
            taskCompletionSource.setResult(null)
        }
    }
    return taskCompletionSource.task
}

fun GeoFire.removeLocationTask(key: String): Task<Void> {
    val taskCompletionSource = TaskCompletionSource<Void>()
    this.removeLocation(key) { _, error ->
        if (error != null) {
            taskCompletionSource.setException(error.toException())
        } else {
            taskCompletionSource.setResult(null)
        }
    }
    return taskCompletionSource.task
}