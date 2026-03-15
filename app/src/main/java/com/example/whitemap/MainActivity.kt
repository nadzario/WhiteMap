package com.example.whitemap

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.whitemap.network.ApiService
import com.example.whitemap.network.GeoJsonData
import com.example.whitemap.ui.theme.WhiteMapTheme
import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.LinearRing
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.geometry.Polygon
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationObjectListener
import com.yandex.mapkit.user_location.UserLocationView
import com.yandex.runtime.image.ImageProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

enum class Screen {
    Map, About
}

class MainActivity : ComponentActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)

        setContent {
            WhiteMapTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
    }

    override fun onStop() {
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showStartPage by remember { mutableStateOf(!hasLocationPermission) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = locationGranted
        if (locationGranted) {
            showStartPage = false
            try {
                context.startForegroundService(Intent(context, ConnectionTrackerService::class.java))
            } catch (_: Exception) {
                Log.e("WhiteMap", "Service start failed")
            }
        }
    }

    if (showStartPage) {
        StartPage(onGrantPermissions = {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            permissionLauncher.launch(permissions.toTypedArray())
        })
    } else {
        AppNavigationWrapper()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationWrapper() {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Map) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "WhiteMap",
                    modifier = Modifier.padding(16.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                NavigationDrawerItem(
                    label = { Text("Карта") },
                    selected = currentScreen == Screen.Map,
                    onClick = {
                        currentScreen = Screen.Map
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    shape = RectangleShape
                )
                NavigationDrawerItem(
                    label = { Text("О приложении") },
                    selected = currentScreen == Screen.About,
                    onClick = {
                        currentScreen = Screen.About
                        scope.launch { drawerState.close() }
                    },
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    shape = RectangleShape
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentScreen == Screen.Map) "Карта" else "О приложении") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Меню")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentScreen) {
                    Screen.Map -> YandexMapContent()
                    Screen.About -> AboutContent()
                }
            }
        }
    }
}

@Composable
fun AboutContent() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "WhiteMap",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Версия 1.0.0",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "Это приложение создано для мониторинга качества мобильной связи. Оно собирает анонимные данные о доступности сети в различных точках города, помогая формировать карту покрытия.",
            fontSize = 16.sp,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun StartPage(onGrantPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.Start
    ) {
        Column {
            Spacer(modifier = Modifier.height(64.dp))
            Text(
                text = "White\nMap",
                fontSize = 84.sp,
                lineHeight = 80.sp,
                fontWeight = FontWeight.ExtraLight,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Text(
                text = "Мониторинг\nкачества связи",
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Для работы приложения необходим доступ к геопозиции в фоновом режиме. Мы не собираем ваши персональные данные, а используем обезличенные идентификаторы.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
            
            Button(
                onClick = onGrantPermissions,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                shape = RectangleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    "НАЧАТЬ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 4.sp
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun YandexMapContent() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val moscowCenter = Point(55.7558, 37.6173)
    var geoJsonData by remember { mutableStateOf<GeoJsonData?>(null) }
    
    val mapView = remember { MapView(context) }
    var userLocation by remember { mutableStateOf<Point?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val apiService = remember {
        try {
            Retrofit.Builder()
                .baseUrl("https://med-ai-assistant.ru/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService::class.java)
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(apiService) {
        if (apiService != null) {
            while (true) {
                try {
                    val response = apiService.getMapData()
                    if (response.isSuccessful) {
                        geoJsonData = response.body()
                        Log.d("WhiteMap", "Map data updated: ${geoJsonData?.features?.size} features")
                    } else {
                        Log.e("WhiteMap", "Map data load failed: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e("WhiteMap", "Network error loading map: ${e.message}")
                }
                delay(60000L) // Обновление каждую минуту
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                mapView.apply {
                    val mapKit = MapKitFactory.getInstance()
                    
                    mapWindow.map.setMapStyle("""
                        [
                          { "tags": { "any": ["poi", "transit", "admin_level_3", "admin_level_4"] }, "stylers": { "visibility": "off" } },
                          { "tags": { "all": ["landscape"] }, "stylers": { "color": "fcfcfc" } },
                          { "tags": { "all": ["water"] }, "stylers": { "color": "e0eef0" } }
                        ]
                    """.trimIndent())

                    val userLocationLayer = mapKit.createUserLocationLayer(mapWindow)
                    userLocationLayer.isVisible = true
                    userLocationLayer.setObjectListener(object : UserLocationObjectListener {
                        override fun onObjectAdded(view: UserLocationView) {
                            userLocation = view.pin.geometry
                            view.pin.setIcon(ImageProvider.fromResource(ctx, android.R.drawable.ic_menu_mylocation))
                            view.accuracyCircle.fillColor = Color(0x334285F4).toArgb()
                        }
                        override fun onObjectRemoved(view: UserLocationView) {}
                        override fun onObjectUpdated(view: UserLocationView, event: com.yandex.mapkit.layers.ObjectEvent) {
                            userLocation = view.pin.geometry
                        }
                    })

                    mapWindow.map.move(
                        com.yandex.mapkit.map.CameraPosition(moscowCenter, 11f, 0f, 0f)
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                val mapObjects = view.mapWindow.map.mapObjects
                mapObjects.clear()
                
                val outageColor = Color(0xFFFF4B4B)
                
                geoJsonData?.features?.forEach { feature ->
                    val geometry = feature.geometry
                    if (geometry.type == "Polygon") {
                        val outerRingPoints = geometry.coordinates.firstOrNull()?.map { Point(it[1], it[0]) }
                        if (outerRingPoints != null && outerRingPoints.isNotEmpty()) {
                            val polygon = Polygon(LinearRing(outerRingPoints), emptyList())
                            mapObjects.addPolygon(polygon).apply {
                                fillColor = outageColor.copy(alpha = 0.6f).toArgb()
                                strokeColor = Color.Transparent.toArgb() // Убираем границу
                                strokeWidth = 0f
                            }
                        }
                    }
                }
            }
        )

        SmallFloatingActionButton(
            onClick = {
                userLocation?.let {
                    mapView.mapWindow.map.move(
                        com.yandex.mapkit.map.CameraPosition(it, 15f, 0f, 0f),
                        Animation(Animation.Type.SMOOTH, 1f),
                        null
                    )
                } ?: Toast.makeText(context, "Определяем местоположение...", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 24.dp, end = 16.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            shape = RectangleShape
        ) {
            Text("Я", fontWeight = FontWeight.Medium)
        }
    }
}
