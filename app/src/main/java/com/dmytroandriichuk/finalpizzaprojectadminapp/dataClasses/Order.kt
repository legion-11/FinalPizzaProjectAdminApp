package com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Order (
    var userId: String? = "",
    var name: String? = "",
    var address:String? = "",
    var flat:String? = "",
    var lat:Double? = 0.0,
    var lon:Double? = 0.0,
    var phone:String? = "",
    var pizza:String? = "",
    var size:Int? = 0,
    var toppings:List<String>? = emptyList(),
    var price: Double? = 0.0,
    var date:Long? = 0,
    var status:Int? = 0,
) {
    override fun toString(): String {
        return "Order(userId=$userId, name=$name, address=$address, flat=$flat, lat=$lat, lon=$lon," +
                " phone=$phone, pizza=$pizza, size=$size, toppings=$toppings, price=$price, date=$date, status=$status, )"
    }
}