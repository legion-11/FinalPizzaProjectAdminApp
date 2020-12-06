package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Admin
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


class SeeOrdersActivity : AppCompatActivity(), OrdersAdapter.OnOrderClickListener  {
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
    private var ordersLiveData: MutableLiveData<List<Order>> = MutableLiveData()
    private lateinit var database: FirebaseDatabase
    private lateinit var querry: Query
    private var listener: ValueEventListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_see_orders)

        locationRequest.interval = 30_000
        locationRequest.fastestInterval = 5_000
        locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY

        updateGPS()

        mAuth = FirebaseAuth.getInstance();
        database = Firebase.database

        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))

        ordersLiveData.observe(this, {
            Log.i("TAG", "onCreate: liveDataObserved")
            list.adapter = OrdersAdapter(it, this)
            Log.i("TAG", "onCreate: $it")
        })
        loadWaitingFromFireBaseDB(this)
    }

    private fun loadWaitingFromFireBaseDB(context: Context) {
        querry = database.getReference("Order").orderByChild("status").equalTo(0.toDouble() )
        listener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val orders = mutableListOf<Order>()
                    for (childSnapshot in snapshot.children) {
                        val order = childSnapshot.getValue(Order::class.java)
                        order?.let {orders.add(it)}
                        // TODo childSnapshot.key tracking
                        Log.i("loadFromFireBaseDB", "onDataChange: " + childSnapshot.key)
                    }
                    ordersLiveData.value = orders
                } else {
                    Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Something wrong happened", Toast.LENGTH_LONG).show()
            }
        }
        querry.addValueEventListener(listener as ValueEventListener)
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

    private fun sendLocation(location: Location) {
        Log.i("TAG", "sendLocation: " + location.latitude)
        Log.i("TAG", "sendLocation: " + location.longitude)
        val admin = Admin(location.latitude, location.longitude)
        mAuth.currentUser?.uid?.let { userId ->
            database.getReference("Admins").child(userId).setValue(admin).addOnCompleteListener {
                if (it.isSuccessful) {
                    Toast.makeText(this, "Internet Connection Error", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PERMISSION_GRANTED) {
                    updateGPS()

                } else {
                    Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                    finish()
                }
                return
            }
        }
    }

    override fun onOrderClick(position: Int) {
        Log.i("TAG", "onOrderClick: " + ordersLiveData.value?.get(position).toString())
    }
}