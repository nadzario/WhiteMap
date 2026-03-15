package com.example.whitemap

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.whitemap.network.ApiService
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.UUID
import java.util.concurrent.TimeUnit

class ConnectionTrackerService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val apiService: ApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://med-ai-assistant.ru/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    private val deviceUuid: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: UUID.randomUUID().toString()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("WhiteMap", "Service onCreate")
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    Log.d("WhiteMap", "Location update: ${location.latitude}, ${location.longitude}")
                    sendPing(location)
                } ?: Log.w("WhiteMap", "Location result is null")
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 60000) // 1 минута
            .setMinUpdateIntervalMillis(60000)
            .setMaxUpdateDelayMillis(60000)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            Log.d("WhiteMap", "Location updates requested successfully")
        } catch (e: SecurityException) {
            Log.e("WhiteMap", "Permission denied for location updates: ${e.message}")
        }
    }

    private fun sendPing(location: Location) {
        serviceScope.launch {
            try {
                Log.d("WhiteMap", "Sending ping: uuid=$deviceUuid, lat=${location.latitude}, lon=${location.longitude}")
                val response = apiService.ping(deviceUuid, location.latitude, location.longitude)
                if (response.isSuccessful) {
                    Log.d("WhiteMap", "Ping success: ${response.body()}")
                } else {
                    Log.e("WhiteMap", "Ping error: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e("WhiteMap", "Network failure during ping: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Мониторинг связи",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhiteMap активен")
            .setContentText("Отправка данных о качестве сети...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("WhiteMap", "Service onStartCommand")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d("WhiteMap", "Service onDestroy")
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "WhiteMapLocationChannel"
    }
}
