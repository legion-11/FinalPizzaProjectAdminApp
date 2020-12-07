package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.AdminLocation
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.system.exitProcess

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    val locationRequest = LocationRequest()
    var locationCallback: LocationCallback? = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult?) {
            super.onLocationResult(result)
            val location = result?.lastLocation
            if (location != null) {
                sendLocation(location)
            }
        }
    }

    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    private lateinit var newOrdersQuery: Query
    private var listenerNewOrders: ValueEventListener? = null

    private lateinit var myOrdersQuery: Query
    private var listenerMyOrders: ValueEventListener? = null

    private var startTime: Long = Date(0).time

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

// Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment


        mapFragment.getMapAsync(this)

        locationRequest.interval = 30_000
        locationRequest.fastestInterval = 5_000
        locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        mAuth = FirebaseAuth.getInstance();
        database = Firebase.database
    }

    fun updateGPS(){
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==  PackageManager.PERMISSION_GRANTED) {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                null
            )
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
                requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    updateGPS()
                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                    mAuth.signOut()
                    finish()
                }
            }
        }
    }

    private fun sendLocation(location: Location) {
        val admin = AdminLocation(location.latitude, location.longitude)
        myLocation.value = LatLng(location.latitude, location.longitude)
        Log.i("TAG", "sendLocation: "+admin.toString())
        mAuth.currentUser?.uid?.let { userId ->
            database.getReference("Admins").child(userId).setValue(admin).addOnCompleteListener {
                if (!it.isSuccessful) {
                    Toast.makeText(this, "Internet Connection Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)

        var myLocationMarker: Marker? = null
        myLocation.observe(this, {
            if (myLocationMarker == null) {
                myLocationMarker = mMap.addMarker(MarkerOptions().apply {
                    position(it)
                    icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(resources, R.drawable.ic_my_location_marker)))

                })
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 12f))
            } else {
                if (it != null) {
                    myLocationMarker?.position = it
                } else {
                    myLocationMarker?.remove()
                    myLocationMarker = null
                }
            }
        })

        var myOrdersMarkersList = mutableListOf<Marker>()
        newOrdersLiveData.observe(this, {
            val tmp = mutableListOf<Marker>()
            for (p in it) {
                val marker = mMap.addMarker(MarkerOptions().apply {
                    position(LatLng(p.first.lat, p.first.lng))
                    title(p.first.address)

                    icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))

                }).apply { tag = Pair(tmp.size, "new") }
                tmp.add(marker)
            }
            for (m in myOrdersMarkersList) {
                m.remove()
            }
            myOrdersMarkersList = tmp
        })

        var newOrdersMarkersList = mutableListOf<Marker>()
        myOrdersLiveData.observe(this, {
            val tmp = mutableListOf<Marker>()
            for (p in it) {
                val marker = mMap.addMarker(MarkerOptions().apply {
                    position(LatLng(p.first.lat, p.first.lng))
                    title(p.first.address)
                    icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                }).apply { tag =  Pair(tmp.size, "my")}
                tmp.add(marker)
            }
            for (m in newOrdersMarkersList) {
                m.remove()
            }
            newOrdersMarkersList = tmp

        })


        val myOrdersCB = findViewById<CheckBox>(R.id.myOrdersCB)
        myOrdersCB.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                loadMyFromFireBaseDB()
            } else {
                listenerMyOrders?.let { myOrdersQuery.removeEventListener(it) }
                myOrdersLiveData.value = mutableListOf()
            }
        }

        val newOrdersCB = findViewById<CheckBox>(R.id.newOrdersCB)
        if (newOrdersCB.isChecked) {loadWaitingFromFireBaseDB()}
        newOrdersCB.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                loadWaitingFromFireBaseDB()
            } else {
                listenerNewOrders?.let { newOrdersQuery.removeEventListener(it) }
                newOrdersLiveData.value = mutableListOf()
            }
        }

        val myLocationCB = findViewById<CheckBox>(R.id.myLocationCB)
        myLocationCB.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                updateGPS()
            } else {
                fusedLocationProviderClient.removeLocationUpdates(locationCallback)
                myLocation.value = null
            }
        }
        myLocationCB.isChecked = true
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        val p = marker.tag as Pair<*, *>?
        p?.let {
            if (it.second == "my") {
                val orderData = myOrdersLiveData.value?.get(p.first as Int)
                if (orderData != null) {
                    buildDialog(orderData, "my order")
                }
            } else {
                val orderData = newOrdersLiveData.value?.get(p.first as Int)
                if (orderData != null) {
                    buildDialog(orderData, "take order")
                }
            }
        }
        return false
    }

    private fun buildDialog(pair: Pair<Order, String>, action: String) {
        val order = pair.first
        val key = pair.second
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_order)
        val name = dialog.findViewById<TextView>(R.id.dialogPersonsNameTV)
        name.text = order.name
        val phone = dialog.findViewById<TextView>(R.id.dialogPhoneNumberTV2)
        phone.text = order.phone
        val address = dialog.findViewById<TextView>(R.id.dialogAddressTV)
        address.text = order.address
        val pizza = dialog.findViewById<TextView>(R.id.dialogPizzaTV)
        pizza.text = order.pizza
        val top = dialog.findViewById<TextView>(R.id.dialogToppingsTV)
        top.text = order.toppings?.joinToString(" ") ?: "no toppings"
        val price = dialog.findViewById<TextView>(R.id.dialogPriceTV)
        price.text = order.price.toString()
        val progressBar = dialog.findViewById<ProgressBar>(R.id.mapsProgressBar)
        val buttonTake = dialog.findViewById<Button>(R.id.takeDismissButton)
        val buttonComplete = dialog.findViewById<Button>(R.id.dialogCompleteOrderButton)
        buttonComplete.isEnabled = action == "my order"

        buttonTake.text = if (action == "my order") "abort" else "take order"
        buttonTake.setOnClickListener {
            progressBar?.visibility = View.VISIBLE
            if (buttonTake.text == "take order") {
                database.getReference("Order").child(key)
                    .addListenerForSingleValueEvent( checkOrderIsTakenAndTakeItIfNot(key, order, buttonTake))
            } else {
                setCurrentUserResponsibleForOrder(key, order, buttonTake, null)
            }
            progressBar?.visibility = View.GONE
        }

        buttonComplete.setOnClickListener {
            if (buttonComplete.text == "complete") {
                completeOrder(key, order, buttonComplete, buttonTake, null)
            } else {
                completeOrder(key, order, buttonComplete, buttonTake, mAuth.currentUser?.uid)
            }
        }

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun completeOrder(key: String, order: Order, buttonCompleteView: Button, buttonAbortView: Button, userId: String?) {
        order.status = if (userId == null) 2 else 1
        order.adminId = userId
        database.getReference("Order").child(key).setValue(order)
            .addOnSuccessListener {
                Toast.makeText(this, "success", Toast.LENGTH_LONG).show()
                buttonCompleteView.apply {
                    this.text =  if (this.text == "abort") "complete" else "abort"
                }
                buttonAbortView.apply {
                    this.isEnabled =  buttonCompleteView.text == "complete"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            }
    }


    private fun checkOrderIsTakenAndTakeItIfNot(key: String, initialOrder: Order, buttonView: Button): ValueEventListener {
        return object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val order = snapshot.getValue(Order::class.java)
                if (order?.status == 0 && initialOrder.address == order.address) {
                    setCurrentUserResponsibleForOrder(key, order, buttonView, mAuth.currentUser?.uid)
                }
                else {
                    Toast.makeText(this@MapsActivity, "Someone was faster", Toast.LENGTH_LONG).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
    }

    private fun setCurrentUserResponsibleForOrder(key: String, order: Order, buttonView: Button, userId: String?) {
        order.status = if (userId != null) 1 else 0
        order.adminId = userId
        database.getReference("Order").child(key).setValue(order)
            .addOnSuccessListener {
                Toast.makeText(this, "success", Toast.LENGTH_LONG).show()
                buttonView.apply {
                    this.text =  if (this.text == "abort") "take order" else "abort"
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun loadWaitingFromFireBaseDB() {
        newOrdersQuery = database.getReference("Order").orderByChild("status").equalTo(0.toDouble() )

        listenerNewOrders = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<Pair<Order, String>>()

                for (childSnapshot in snapshot.children) {
                    val order = childSnapshot.getValue(Order::class.java)
                    val key = childSnapshot.key
                    if (order!=null && key != null) {
                        val pair = Pair(order ,key)
                        orders.add(pair)
                    }
                }
                newOrdersLiveData.value = orders
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MapsActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        newOrdersQuery.addValueEventListener(listenerNewOrders as ValueEventListener)
    }

    private fun loadMyFromFireBaseDB() {
        myOrdersQuery = database.getReference("Order").orderByChild("adminId").equalTo(mAuth.currentUser?.uid)

        listenerMyOrders = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<Pair<Order, String>>()

                for (childSnapshot in snapshot.children) {
                    val order = childSnapshot.getValue(Order::class.java)
                    val key = childSnapshot.key
                    if (order!=null && key != null) {
                        val pair = Pair(order ,key)
                        orders.add(pair)
                    }
                }
                myOrdersLiveData.value = orders
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@MapsActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
        myOrdersQuery.addValueEventListener(listenerMyOrders as ValueEventListener)
    }

    override fun onBackPressed() {
        if (System.currentTimeMillis() - startTime < 2000){
            super.onBackPressed()
            mAuth.signOut()
            fusedLocationProviderClient.removeLocationUpdates(locationCallback)
            listenerNewOrders?.let { newOrdersQuery.removeEventListener(it) }
            listenerMyOrders?.let { myOrdersQuery.removeEventListener(it) }

        } else {
            Toast.makeText(
                this,
                "Press one more time to log out.",
                Toast.LENGTH_LONG
            ).show()
            startTime = System.currentTimeMillis()
        }
    }

    companion object {
        val newOrdersLiveData: MutableLiveData<List<Pair<Order, String>>> = MutableLiveData()
        val myOrdersLiveData: MutableLiveData<List<Pair<Order, String>>> = MutableLiveData()
        val myLocation: MutableLiveData<LatLng> = MutableLiveData()
    }
}