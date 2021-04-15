package com.dmytroandriichuk.finalpizzaprojectadminapp.services

import android.app.*
import android.content.Intent
import android.content.res.Configuration
import android.location.Location
import android.os.*
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.dmytroandriichuk.finalpizzaprojectadminapp.MapsActivity
import com.dmytroandriichuk.finalpizzaprojectadminapp.R
import com.dmytroandriichuk.finalpizzaprojectadminapp.dataClasses.AdminLocation
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase


@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class LocationUpdatesService : Service() {
    private val mBinder: IBinder = LocalBinder()

    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false
    private lateinit var mNotificationManager: NotificationManager
    private var serviceIsRunningInForeground = false

    /**
     * Contains parameters used by [com.google.android.gms.location.FusedLocationProviderApi].
     */
    private lateinit var mLocationRequest: LocationRequest

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private lateinit var  mLocationCallback: LocationCallback
    private lateinit var mServiceHandler: Handler

    /**
     * The current location.
     */
    private var mLocation: Location? = null

    /**
     * The instance of realtime database.
     */
    private lateinit var database: FirebaseDatabase

    /**
     * Current admin UID from Firebase.Auth.
     */
    private var userId: String? = null

    override fun onCreate() {
        database = Firebase.database

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        }

        createLocationRequest()
        lastLocation()
        val handlerThread = HandlerThread(TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = getString(R.string.app_name)
            // Create the channel for the notification
            val mChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT)

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(mChannel)
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mChangingConfiguration = true
    }

    override fun onBind(intent: Intent?): IBinder {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.d(TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        serviceIsRunningInForeground = false
        return mBinder
    }

    override fun onRebind(intent: Intent?) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.d(TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        serviceIsRunningInForeground = true
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Last client unbound from service")
        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.d(TAG, "Starting foreground service")
            startForeground(NOTIFICATION_ID, notification)
            serviceIsRunningInForeground = true
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun requestLocationUpdates(userId: String) {
        Log.d(TAG, "Requesting location updates")
        this.userId = userId
        Utils.setRequestingLocationUpdates(this, true)
        startService(Intent(applicationContext, LocationUpdatesService::class.java))
        try {

            mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper())
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, false)
            Log.e(TAG, "Lost location permission. Could not request updates. $unlikely")
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * [SecurityException].
     */
    fun removeLocationUpdates() {
        Log.d(TAG, "Removing location updates")
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            Utils.setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            Utils.setRequestingLocationUpdates(this, true)
            Log.e(TAG, "Lost location permission. Could not remove updates. $unlikely")
        }
    }

    /**
     * Returns the [NotificationCompat] used as part of the foreground service.
     */
    private val notification: Notification
        get() {
            val intent = Intent(this, LocationUpdatesService::class.java)
            val text: CharSequence = Utils.getLocationText(mLocation)

            // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
            intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentText(text)
                    .setContentTitle(Utils.getLocationTitle(this))
                    .setOngoing(true)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker(text)
                    .setWhen(System.currentTimeMillis())

            return builder.build()
        }

    private fun lastLocation(){
        try {
            mFusedLocationClient.lastLocation
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful && task.result != null) {
                            mLocation = task.result
                        } else {
                            Log.w(TAG, "Failed to get location.")
                        }
                    }
        } catch (unlikely: SecurityException) {
            Log.e(TAG, "Lost location permission.$unlikely")
        }
    }

    private fun onNewLocation(location: Location) {
        Log.d(TAG, "New location: $location")
        mLocation = location

        //Send location to realtime database
        sendLocation(location)

        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        sendBroadcast(intent)
        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground) {
            mNotificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun sendLocation(location: Location) {
        val admin = AdminLocation(location.latitude, location.longitude)
        MapsActivity.myLocation.value = LatLng(location.latitude, location.longitude)
        Log.i("TAG", "sendLocation: $admin")
        database.getReference("Admins").child(userId!!).setValue(admin).addOnFailureListener {
            Toast.makeText(this, "Internet Connection Error", Toast.LENGTH_LONG).show()
        }

    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest() {
        mLocationRequest = LocationRequest.create()
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    inner class LocalBinder: Binder() {
        val service: LocationUpdatesService
            get() = this@LocationUpdatesService
    }

    companion object {
        private const val PACKAGE_NAME = "com.dmytroandriichuk.finalpizzaprojectadminapp.services.locationupdatesforegroundservice"
        private val TAG = LocationUpdatesService::class.java.simpleName

        /**
         * The name of the channel for notifications.
         */
        private const val CHANNEL_ID = "channel_01"
        const val ACTION_BROADCAST = "$PACKAGE_NAME.broadcast"
        const val EXTRA_LOCATION = "$PACKAGE_NAME.location"
        private const val EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME +
                ".started_from_notification"

        /**
         * The desired interval for location updates. Inexact. Updates may be more or less frequent.
         */
        private const val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

        /**
         * The fastest rate for active location updates. Updates will never be more frequent
         * than this value.
         */
        private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

        /**
         * The identifier for the notification displayed for the foreground service.
         */
        private const val NOTIFICATION_ID = 12345678

        /**
         * The identifier for the user uid extra.
         */
        const val KEY_USER_ID = "USER_UID"
    }
}