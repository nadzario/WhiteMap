package com.example.whitemap.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    // В бэкенде используется @app.post, поэтому меняем на @POST
    // Параметры uuid, lat, lon в FastAPI без Body() передаются как Query параметры
    @POST("ping")
    suspend fun ping(
        @Query("uuid") uuid: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): Response<Map<String, String>> // Сервер возвращает {"status": "ok"}

    @GET("map")
    suspend fun getMapData(): Response<GeoJsonData>
}

data class GeoJsonData(
    val type: String, // "FeatureCollection"
    val features: List<GeoJsonFeature>
)

data class GeoJsonFeature(
    val type: String, // "Feature"
    val geometry: Geometry,
    val properties: Map<String, String>? = null // Для {"id": h_idx}
)

data class Geometry(
    val type: String, // "Polygon"
    val coordinates: List<List<List<Double>>> // Координаты полигона: [[[lon, lat], ...]]
)
