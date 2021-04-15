package com.dmytroandriichuk.finalpizzaprojectadminapp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.preference.PreferenceManager
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.Order
import com.dmytroandriichuk.finalpizzaprojectadminapp.dialogs.OrderTakeDialog
import com.dmytroandriichuk.finalpizzaprojectadminapp.services.LocationUpdatesService
import com.dmytroandriichuk.finalpizzaprojectadminapp.services.LocationUpdatesService.LocalBinder
import com.dmytroandriichuk.finalpizzaprojectadminapp.services.Utils
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.util.*
import java.util.concurrent.TimeUnit


class MapsActivity : AppCompatActivity(),
        OnMapReadyCallback,
        GoogleMap.OnMarkerClickListener, OrderTakeDialog.DialogClickListener {
    private lateinit var mMap: GoogleMap
    private lateinit var mAuth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // Listener and query reference to new orders
    private lateinit var newOrdersQuery: Query
    private var listenerNewOrders: ValueEventListener? = null
    // Listener and query reference to taken orders
    private lateinit var myOrdersQuery: Query
    private var listenerMyOrders: ValueEventListener? = null

    private var mBound = false
    private var mService: LocationUpdatesService? = null
    private val mServiceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as LocalBinder
            mService = binder.service
            mBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            mService = null
            mBound = false
        }
    }

    //show my location on map
    private var myLocationMarker: Marker? = null
    private var startTime: Long = Date(0).time

    private val broadcastReceiver = object : BroadcastReceiver()  {
        val TAG = "BroadcastReceiver"
        override fun onReceive(context: Context?, intent: Intent?) {
            val location: Location? = intent!!.getParcelableExtra(LocationUpdatesService.EXTRA_LOCATION)
            Log.d(TAG, "onReceive: $location")
            if (location != null) {
                myLocation.value = LatLng(location.latitude, location.longitude)
            }
        }
    }

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        val mToolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(mToolbar)
        mToolbar.setNavigationIcon(R.drawable.ic_log_out)
        title = "View Orders"
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@MapsActivity)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        mAuth = FirebaseAuth.getInstance()
        database = Firebase.database
        //permission for fitness
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
            != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), REQUEST_OAUTH_REQUEST_CODE)
            }
        }

        val fitnessOptions =
                FitnessOptions.builder()
                        .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                        .addDataType(DataType.TYPE_STEP_COUNT_DELTA)
                        .build()

        if (!GoogleSignIn.hasPermissions(GoogleSignIn.getLastSignedInAccount(this), fitnessOptions)) {
            GoogleSignIn.requestPermissions(
                    this,
                    REQUEST_OAUTH_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(this),
                    fitnessOptions)
        }
    }

    override fun onStart() {
        super.onStart()
        //checkbox to enable showing markers of current location
        val myLocationCB = findViewById<CheckBox>(R.id.myLocationCB)
        myLocationCB.isChecked = Utils.requestingLocationUpdates(this)
        myLocationCB.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                updateGPS()
                Log.d(TAG, "onCreate: ")
            } else {
                disableGPS()
                myLocationMarker?.remove()
                myLocationMarker = null
            }
        }

        Log.d(TAG, "onStart: bindService")

        // Bind to the service. If the service is in foreground mode, this signals to the service
        // that since this activity is in the foreground, the service can exit foreground mode.
        bindService(Intent(this, LocationUpdatesService::class.java), mServiceConnection, BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        //register our broadcast receiver
        val intentFilter = IntentFilter()
        intentFilter.addAction(LocationUpdatesService.ACTION_BROADCAST)
        registerReceiver(broadcastReceiver, intentFilter)
    }

    override fun onPause() {
        unregisterReceiver(broadcastReceiver)
        super.onPause()
    }

    override fun onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(mServiceConnection)
            mBound = false
        }

        super.onStop()
    }

    private fun updateGPS(){
        //check permission and get location
        Log.d(TAG, "updateGPS: ")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==  PackageManager.PERMISSION_GRANTED) {
            mService?.requestLocationUpdates(mAuth.currentUser!!.uid)
            Log.d(TAG, "updateGPS: $mService")
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), PERMISSIONS_REQUEST_LOCATION)
        }
    }

    private fun disableGPS() {
        mService?.removeLocationUpdates()
    }

    //check if permission was granted, then enable location update (send location)
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSIONS_REQUEST_LOCATION -> {
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode != REQUEST_OAUTH_REQUEST_CODE) {
                finish()
            } else {
                subscribe()
            }
        }
    }

    // start observing my location, taken , and new orders data
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.setOnMarkerClickListener(this)

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

        // show location of new orders, will be updated with each new order appearing and changing
        var myOrdersMarkersList = mutableListOf<Marker>()
        newOrdersLiveData.observe(this, {
            val tmp = addMarkers(it, true)
            for (m in myOrdersMarkersList) {
                m.remove()
            }
            myOrdersMarkersList = tmp
        })

        // show location of taken orders, will be updated with each new order appearing and changing
        var newOrdersMarkersList = mutableListOf<Marker>()
        myOrdersLiveData.observe(this, {
            val tmp = addMarkers(it, false)
            for (m in newOrdersMarkersList) {
                m.remove()
            }
            newOrdersMarkersList = tmp
        })

        //checkbox to enable showing markers of taken orders on map
        val myOrdersCB = findViewById<CheckBox>(R.id.myOrdersCB)
        myOrdersCB.setOnCheckedChangeListener { _: CompoundButton, b: Boolean ->
            if (b) {
                loadMyFromFireBaseDB()
            } else {
                listenerMyOrders?.let { myOrdersQuery.removeEventListener(it) }
                myOrdersLiveData.value = mutableListOf()
            }
        }

        //checkbox to enable showing markers of new orders on map
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
    }
    /**
     * adding markers on the map and depending on if it's a new order or an old one change color
     */
    private fun addMarkers(list: List<Pair<Order, String>>, isNew: Boolean): MutableList<Marker> {
        val tmp = mutableListOf<Marker>()
        for (p in list) {
            val marker = mMap.addMarker(MarkerOptions().apply {
                position(LatLng(p.first.lat, p.first.lng))
                title(p.first.address)

                icon(BitmapDescriptorFactory.defaultMarker(if (isNew) BitmapDescriptorFactory.HUE_CYAN else BitmapDescriptorFactory.HUE_GREEN))

            }).apply { tag = Pair(tmp.size, if (isNew) OrderStatus.NEW else OrderStatus.TAKEN)}
            tmp.add(marker)
        }
        return tmp
    }

    //show dialogs depending on was that dialog taken(status == 1) or not (status == 0)
    @Suppress("UNCHECKED_CAST")
    override fun onMarkerClick(marker: Marker): Boolean {
        val p = marker.tag as Pair<Int, OrderStatus>?
        p?.let {
            if (it.second == OrderStatus.TAKEN) {
                val orderData = myOrdersLiveData.value?.get(p.first)
                if (orderData != null) {
                    buildDialog(orderData, OrderStatus.TAKEN)
                }
            } else {
                val orderData = newOrdersLiveData.value?.get(p.first)
                if (orderData != null) {
                    buildDialog(orderData, OrderStatus.NEW)
                }
            }
        }
        return false
    }

    // build dialog with order information
    private fun buildDialog(pair: Pair<Order, String>?, status: OrderStatus) {
        pair?.let {
            val dialog = OrderTakeDialog(it.first, pair.second, status, this)
            dialog.show(supportFragmentManager.beginTransaction(), "order dialog")
        }
    }

    // load orders with status 0
    private fun loadWaitingFromFireBaseDB() {
        newOrdersQuery = database.getReference("Order").orderByChild("status").equalTo(0.toDouble())

        listenerNewOrders = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<Pair<Order, String>>()

                for (childSnapshot in snapshot.children) {
                    val order = childSnapshot.getValue(Order::class.java)
                    val key = childSnapshot.key
                    if (order!=null && key != null) {
                        val pair = Pair(order, key)
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

    // load orders with adminId == current user
    private fun loadMyFromFireBaseDB() {
        myOrdersQuery = database.getReference("Order").orderByChild("adminId").equalTo(mAuth.currentUser?.uid)

        listenerMyOrders = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val orders = mutableListOf<Pair<Order, String>>()

                for (childSnapshot in snapshot.children) {
                    val order = childSnapshot.getValue(Order::class.java)
                    val key = childSnapshot.key
                    if (order!=null && key != null) {
                        val pair = Pair(order, key)
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

    // logout after double click
    override fun onBackPressed() {
        if (System.currentTimeMillis() - startTime < 2000){
            finishAffinity()
        } else {
            Toast.makeText(
                    this,
                    "Press one more time to close the app.",
                    Toast.LENGTH_LONG
            ).show()
            startTime = System.currentTimeMillis()
        }
    }

    override fun completeOrder(key: String, order: Order, dialog: OrderTakeDialog) {
        assignOrderToUser(key, mapOf("adminId" to null, "status" to 2))
                .addOnSuccessListener {
                    Toast.makeText(this, "success", Toast.LENGTH_LONG).show()
                    dialog.button.text = getString(R.string.abort)
                    dialog.status = OrderStatus.FINISHED
                    updateSalary(true, order)
                    val tmp = sharedPreferences.getLong(KEY_START_DATE, -1)
                    sharedPreferences.edit().putLong(KEY_START_DATE, Date().time).apply()
                    sharedPreferences.edit().putLong(KEY_FINISH_DATE, tmp).apply()
                }
    }

    override fun uncompleteOrder(key: String, order: Order, dialog: OrderTakeDialog) {
        assignOrderToUser(key, mapOf("adminId" to mAuth.currentUser!!.uid, "status" to 1))
                .addOnSuccessListener {
                    dialog.button.text = getString(R.string.complete)
                    dialog.status = OrderStatus.TAKEN
                    val tmp = sharedPreferences.getLong(KEY_FINISH_DATE, 0)
                    sharedPreferences.edit().putLong(KEY_START_DATE, tmp).apply()
                    updateSalary( false, order)
                }
    }

    override fun takeOrder(key: String, initialOrder: Order, dialog: OrderTakeDialog) {
        val isFirstOrder = myOrdersLiveData.value!!.isEmpty()
        database.getReference("Order").child(key).addListenerForSingleValueEvent(
                object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val order = snapshot.getValue(Order::class.java)
                        if (order?.status == 0 && initialOrder.address == order.address) {
                            assignOrderToUser(key, mapOf("adminId" to mAuth.currentUser!!.uid, "status" to 1, "date" to Date().time))
                                    .addOnSuccessListener {
                                        dialog.button.text = getString(R.string.abort)
                                        dialog.status = OrderStatus.TAKEN
                                        if (isFirstOrder) {
                                            sharedPreferences.edit().putLong(KEY_START_DATE, Date().time).apply()
                                        }
                                    }
                        } else {
                            Toast.makeText(this@MapsActivity, "Someone already took that order", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                }
        )
    }

    override fun giveBackOrder(key: String, dialog: OrderTakeDialog) {
        val isLastOrder = (myOrdersLiveData.value!!.size - 1) == 0
        assignOrderToUser(key, mapOf("adminId" to null, "status" to 0, "date" to null))
                .addOnSuccessListener {
                    dialog.button.text = getString(R.string.take)
                    dialog.status = OrderStatus.NEW
                    if (isLastOrder) { sharedPreferences.edit().putLong(KEY_START_DATE, 0).apply() }
                }
    }

    // set order status and adminId
    private fun assignOrderToUser(key: String, data: Map<String, *>): Task<Void> {
        return database.getReference("Order")
                .child(key)
                .updateChildren(data)
                .addOnFailureListener {
                    Toast.makeText(this@MapsActivity, "${it.message}", Toast.LENGTH_LONG).show()
                }
    }

    private fun subscribe() {
        // To create a subscription, invoke the Recording API. As soon as the subscription is
        // active, fitness data will start recording.
        Fitness.getRecordingClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .subscribe(DataType.TYPE_STEP_COUNT_CUMULATIVE)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("MapsActivity", "Successfully subscribed!")
                    } else {
                        Log.w("MapsActivity", "There was a problem subscribing.", task.exception)
                    }
                }
    }
    /**
     * update our salary by calculating price for order, steps taken today, and how much time
     * it took to complete an order
     */
    private fun updateSalary(isAdding: Boolean, order: Order){
        val timeSince1970 = sharedPreferences.getLong(KEY_START_DATE, Date().time)
        val cal = Calendar.getInstance()
        cal.time = Date(timeSince1970)
        val startTime = cal.timeInMillis
        cal.time = Date()
        val endTime = cal.timeInMillis

        val readRequest = DataReadRequest.Builder()
                .aggregate(DataType.AGGREGATE_STEP_COUNT_DELTA)
                .bucketByTime(1, TimeUnit.HOURS)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .enableServerQueries()
                .build()
        Fitness.getHistoryClient(this@MapsActivity, GoogleSignIn.getLastSignedInAccount(this)!!)
                .readData(readRequest).addOnSuccessListener { response ->
                    // sum all steps from data points
                    var total = 0
                    for (dataSet in response.buckets.flatMap { it.dataSets }) {
                        for (dp in dataSet.dataPoints) {
                            total += dp.getValue(Field.FIELD_STEPS).toString().toInt()
                        }
                    }
                    Log.d(TAG, "onDataChange: $total steps during this order")
                    //check if order was finished within 30 minutes
                    val within30Minutes = TimeUnit.MILLISECONDS.toMinutes(endTime - order.date) < 30
                    // if we complete order calculate how much steps does it take from the last one
                    if (isAdding) { updateSteps(total) }
                    updateMoney(within30Minutes, isAdding)
                    // if we aborting order calculate steps back
                    if (!isAdding) { updateSteps(-total) }
                }
                .addOnFailureListener {

                }
    }


    private fun updateSteps(stepsDelta: Int) {
        sharedPreferences.edit()
                .putInt(KEY_STEPS, sharedPreferences.getInt(KEY_STEPS, 0) + stepsDelta)
                .apply()
    }

    private fun updateMoney( withinTime: Boolean, addMoney: Boolean) {
        val steps = sharedPreferences.getInt(KEY_STEPS, 0)
        val moneyForInTime = if (withinTime) { 1.5f } else { 0.75f }
        val moneyForEverything = if (steps > resources.getInteger(R.integer.steps_to_get_120_percent)) { moneyForInTime * 1.2f } else { moneyForInTime }
        val addFinalMoney = if (addMoney) { moneyForEverything } else { moneyForEverything * -1f }
        Log.d(TAG, "updateMoney: steps $steps within 30m $withinTime,  $addMoney, final money $addFinalMoney")
        sharedPreferences.edit()
                .putFloat(KEY_MONEY, sharedPreferences.getFloat(KEY_MONEY, 0f) + addFinalMoney)
                .apply()
    }

    private fun showInformation() {
        val money = sharedPreferences.getFloat(KEY_MONEY, 0f)
        val steps = sharedPreferences.getInt(KEY_STEPS, 0)
        val stepsLeft = resources.getInteger(R.integer.steps_to_get_120_percent) - steps
        AlertDialog.Builder(this)
                .setTitle("You made $ ${String.format("%.2f", money)} today")
                .setMessage(if (stepsLeft < 0) "You are obtaining 20% more money per order!"
                            else "You are left to make $stepsLeft steps to obtain 20% more money per order")
                .setPositiveButton("Ok", null)
                .show()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.search_action_bar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.step_statistics -> {
            // User chose the "Show My Statistics" item, show the app filter dialog to specify search...
            Log.d("SearchFragment", "onOptionsItemSelected: Statistics")
            showInformation()
            true
        }

        android.R.id.home -> {
            // User chose the "Log Out" action
            Log.d("SearchFragment", "onOptionsItemSelected: Home")
            mAuth.signOut()
            mService?.removeLocationUpdates()
            //if you do not sign out from google you can not choose other user
            finish()
            true
        }
        else -> {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            super.onOptionsItemSelected(item)
        }
    }

    // singleton with mutable live data
    companion object {
        val newOrdersLiveData: MutableLiveData<List<Pair<Order, String>>> = MutableLiveData(listOf())
        val myOrdersLiveData: MutableLiveData<List<Pair<Order, String>>> = MutableLiveData(listOf())
        val myLocation: MutableLiveData<LatLng> = MutableLiveData()

        const val PERMISSIONS_REQUEST_LOCATION = 1
        const val REQUEST_OAUTH_REQUEST_CODE = 2
        private const val TAG = "MapsActivity"
        private const val KEY_STEPS = "STEPS_COUNTED"
        private const val KEY_MONEY = "MONEY_TODAY"
        private const val KEY_START_DATE = "KEY_START_DATE"
        private const val KEY_FINISH_DATE = "KEY_FINISH_DATE"
        enum class OrderStatus {
            NEW,
            TAKEN,
            FINISHED,
        }
    }
}