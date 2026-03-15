package com.example.whitemap

import android.app.Application
import android.util.Log
import com.yandex.mapkit.MapKitFactory

class WhiteMapApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Устанавливаем ключ ПЕРЕД инициализацией
        MapKitFactory.setApiKey("e52cd296-fe46-43fe-a18a-76d94c09f128")
        
        try {
            MapKitFactory.initialize(this)
            Log.d("WhiteMap", "MapKit initialized successfully")
        } catch (e: Exception) {
            Log.e("WhiteMap", "MapKit init error: ${e.message}")
        }
    }
}
