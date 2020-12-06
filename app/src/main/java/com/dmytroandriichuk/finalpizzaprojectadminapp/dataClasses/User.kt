package com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User (
    var email: String? = "",
    var name:String? = "",
    var isAdmin: Boolean = false
) {
    override fun toString(): String {
        return "User(email=$email, name=$name, isAdmin=$isAdmin)"
    }
}