package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.AdminLocation
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
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
    private var keys: List<String> = emptyList()
    private lateinit var database: FirebaseDatabase
    private lateinit var querry: Query
    private var listener: ValueEventListener? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_see_orders)

        locationRequest.interval = 30_000
        locationRequest.fastestInterval = 5_000
        locationRequest.priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
        mAuth = FirebaseAuth.getInstance();
        database = Firebase.database
        updateGPS()


        val list = findViewById<RecyclerView>(R.id.list)
        list.layoutManager = LinearLayoutManager(this)
        list.addItemDecoration(DividerItemDecoration(list.context, DividerItemDecoration.VERTICAL))
        ordersLiveData.observe(this, {
            Log.i("TAG", "onCreate: liveDataObserved")
            list.adapter = OrdersAdapter(it, this)
            Log.i("TAG", "onCreate: $it")
        })

        loadWaitingFromFireBaseDB(this)

        title = "You can take this orders"
        val button = findViewById<Button>(R.id.seeOrdersButton)
        button.setOnClickListener {
            if (title == "You can take this orders"){
                title = "My orders"
                button.text = "New orders"
                loadMyFromFireBaseDB(this)
            } else {
                title = "You can take this orders"
                button.text = "My orders"
                loadWaitingFromFireBaseDB(this)
            }
        }
    }

    private fun loadWaitingFromFireBaseDB(context: Context) {
        if (listener != null) { querry.removeEventListener(listener as ValueEventListener) }
        querry = database.getReference("Order").orderByChild("status").equalTo(0.toDouble() )

        listener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val orders = mutableListOf<Order>()
                    val keys = mutableListOf<String>()

                    for (childSnapshot in snapshot.children) {
                        val order = childSnapshot.getValue(Order::class.java)
                        order?.let {orders.add(it)}
                        childSnapshot.key?.let { keys.add(it) }
                    }

                    ordersLiveData.value = orders
                    this@SeeOrdersActivity.keys = keys
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

    override fun onBackPressed() {
        super.onBackPressed()
        mAuth.signOut()
    }

    private fun loadMyFromFireBaseDB(context: Context) {
        if (listener != null) { querry.removeEventListener(listener as ValueEventListener) }
        querry = database.getReference("Order").orderByChild("adminId").equalTo(mAuth.currentUser?.uid)

        listener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<Order>()
                val keys = mutableListOf<String>()

                for (childSnapshot in snapshot.children) {
                    val order = childSnapshot.getValue(Order::class.java)
                    order?.let {orders.add(it)}
                    childSnapshot.key?.let { keys.add(it) }
                }

                ordersLiveData.value = orders
                this@SeeOrdersActivity.keys = keys

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
        val admin = AdminLocation(location.latitude, location.longitude)
        MapActivity.myPositionLatLng.value = LatLng(location.latitude, location.longitude)
        Log.i("TAG", "sendLocation: "+admin.toString())
        mAuth.currentUser?.uid?.let { userId ->
            database.getReference("Admins").child(userId).setValue(admin).addOnCompleteListener {
                if (!it.isSuccessful) {
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
                    mAuth.signOut()
                    finish()
                }
            }
        }
    }

    override fun onOrderClick(position: Int) {
        seeOrder(position)
        Log.i("TAG", "onOrderClick: " + ordersLiveData.value?.get(position).toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        if (listener != null) { querry.removeEventListener(listener as ValueEventListener) }
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun seeOrder(position: Int) {
        val key = keys[position]
        val newIntent = Intent(this@SeeOrdersActivity, MapActivity::class.java).apply {
            putExtra("key", keys[position])
        }
        startActivity(newIntent)
    }
}