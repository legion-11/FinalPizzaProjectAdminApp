package com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class AdminLocation (
    var lat: Double? = 0.0,
    var lng: Double? = 0.0
) {
    override fun toString(): String {
        return "Admin(lat=$lat, lng=$lng)"
    }
}
