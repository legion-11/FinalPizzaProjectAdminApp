package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var listener: ValueEventListener
    private lateinit var reference: DatabaseReference
    private var key: String? = null
    private var latLng: LatLng? = null
    private lateinit var mMap: GoogleMap
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
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
        reference = database.getReference("Order").child(key!!)
        listener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    order.value = snapshot.getValue(Order::class.java)
                } else {
                    Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
            }
        }
        reference.addValueEventListener(listener as ValueEventListener)
    }



    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        val marker = MarkerOptions()
        mMap.addMarker(marker)
        order.observe(this, { order ->
            findViewById<TextView>(R.id.addressTV).text = order?.flat + ", " + order?.address
            findViewById<TextView>(R.id.personsNameTV).text = order?.name
            findViewById<TextView>(R.id.toppingsTV).text = order?.toppings?.joinToString(", ")
            val size = when(order?.size) {
                0->"Small "
                1->"Medium "
                2->"Large "
                else->"Extra "
            }
            findViewById<TextView>(R.id.pizzaTV).text = size + order?.pizza
            latLng = order.lat?.let { lat ->
                order.lng?.let { lng ->
                    LatLng(lat, lng)
                }
            }

            latLng?.let {
                marker.apply {
                    position(it)
                    title(order?.address)
                }
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
            }

        })
        val myLocation = MarkerOptions()
        mMap.addMarker(myLocation)
        //todo listener location
        ll.observe(this, {
            myLocation.apply {
                position(it)
            }
        })
    }
    companion object {
        val ll: MutableLiveData<LatLng> = MutableLiveData()
    }
}