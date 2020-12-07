package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var listener: ValueEventListener
    private lateinit var reference: DatabaseReference

    private var key: String? = null
    private var latLng: LatLng? = null
    private var order: MutableLiveData<Order> = MutableLiveData()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        mAuth = FirebaseAuth.getInstance();
        database = Firebase.database
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapView) as SupportMapFragment
        mapFragment.getMapAsync(this)

        key = intent.getStringExtra("key")
    }

    private fun loadFromFireBaseDB(context: Context) {
        key?.let { key ->
            reference = database.getReference("Order").child(key)
            val button = findViewById<Button>(R.id.takeOrDismissButton)
            listener = object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val success = snapshot.getValue(Order::class.java)
                        order.value = success
                        if (success?.status == 0) {
                            button.text = "Take this order"
                            button.isClickable = true
                        } else if (success?.status == 1){
                            button.text = "Abort"
                            button.isClickable = true
                        } else {
                            button.visibility =View.GONE
                        }
                    } else {
                        Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
                }
            }
            reference.addValueEventListener(listener)
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        var orderMarker: Marker? = null
        order.observe(this, { order ->
            findViewById<TextView>(R.id.addressTV).text = order?.flat + ", " + order?.address
            findViewById<TextView>(R.id.personsNameTV).text = order?.name
            findViewById<TextView>(R.id.toppingsTV).text = order?.toppings?.joinToString(", ")
            val size = when (order?.size) {
                0 -> "Small "
                1 -> "Medium "
                2 -> "Large "
                else -> "Extra "
            }

            findViewById<TextView>(R.id.pizzaTV).text = size + order?.pizza
            latLng = LatLng(order.lat, order.lng)

            Log.i("TAG", "onMapReady: "+latLng.toString())

            latLng?.let {
                if (orderMarker == null) {
                    orderMarker = mMap.addMarker(MarkerOptions().apply {
                        position(it)
                        title(order?.address)
                    })
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                } else if (orderMarker?.position != latLng){
                    orderMarker?.position = it
                    orderMarker?.title = order?.address
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                }
            }
        })

        var myLocation: Marker? = null
        myPositionLatLng.observe(this, {
            if (myLocation == null) {
                myLocation = mMap.addMarker(MarkerOptions().apply {
                    position(it)
                })
            } else {
                myLocation?.position = it
            }
        })

        val progressBar = findViewById<ProgressBar>(R.id.mapProgressBar)
        progressBar.visibility = View.VISIBLE
        loadFromFireBaseDB(this)
        progressBar.visibility = View.GONE

        val button = findViewById<Button>(R.id.takeOrDismissButton)
        button.setOnClickListener {
            order.value?.let { order ->
                progressBar.visibility = View.VISIBLE
                if (button.text == "Take this order") {
                    order.status = 1
                    order.adminId = mAuth.currentUser?.uid
                    database.getReference("Order").child(key!!).setValue(order)
                            .addOnSuccessListener {
                                Toast.makeText(this, "success", Toast.LENGTH_LONG).show()
                                button.text = "Abort"
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
                            }

                } else if (button.text == "Abort"){
                    order.status = 0
                    order.adminId = null
                    database.getReference("Order").child(key!!).setValue(order)
                            .addOnSuccessListener {
                                Toast.makeText(this, "success", Toast.LENGTH_LONG).show()
                                button.text = "Take this order"
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Something wrong happened", Toast.LENGTH_LONG).show()
                            }
                }
                progressBar.visibility = View.GONE
            }
        }
    }

    companion object {
        val myPositionLatLng: MutableLiveData<LatLng> = MutableLiveData()
    }
}