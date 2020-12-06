package com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class OrderTracking (
    var AdminId: String? = ""
) {
    override fun toString(): String {
        return "OrderTracking(AdminId=$AdminId)"
    }
}