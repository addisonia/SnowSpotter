package com.example.snowspotterapp2

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import android.view.Gravity
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Switch
import androidx.appcompat.app.ActionBar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.example.snowspotterapp2.databinding.ActivityMapsBinding
import com.google.android.gms.maps.LocationSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.gms.maps.model.MapStyleOptions
import kotlinx.coroutines.*
import org.json.JSONObject
import java.lang.Math.pow
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale
import kotlin.math.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val weatherScope = CoroutineScope(Dispatchers.IO + Job())
    private val API_KEY = "6d05802c8dd306c4a02c96c9bf433ea2"
    private val TAG = "MapsActivity"

    // Madison, WI coordinates
    private val madisonLocation = LatLng(43.0731, -89.4012)
    private var userMarker: Marker? = null

    // Store snow locations and tracking variables
    private val snowLocations = mutableListOf<SnowLocation>()
    private var currentSnowIndex = -1
    private var currentHighlightedMarker: Marker? = null

    data class SnowLocation(
        val position: LatLng,
        val name: String,
        val description: String,
        var marker: Marker? = null
    )

    private var userCircle: Circle? = null
    private val BASE_CIRCLE_RADIUS = 10000.0

    private lateinit var settingsButton: MaterialButton
    private lateinit var userButton: MaterialButton


    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    private var isMusicEnabled = true  // Default to true since music starts enabled


    private var currentTheme = "snow"  // Default theme


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //customize the title
        supportActionBar?.setDisplayShowHomeEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)  // This removes the default title
        supportActionBar?.setDisplayShowCustomEnabled(true)

        // Center the title
        val textView = TextView(this)
        textView.text = "SnowSpotter"
        textView.textSize = 20f  // Adjust size as needed
        textView.setTypeface(null, Typeface.BOLD)  // Make it bold
        val layoutParams = ActionBar.LayoutParams(
            ActionBar.LayoutParams.WRAP_CONTENT,
            ActionBar.LayoutParams.WRAP_CONTENT
        )
        layoutParams.gravity = Gravity.CENTER
        supportActionBar?.setCustomView(textView, layoutParams)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Apply snow theme immediately after binding
        applySnowTheme()

        binding.findSnowButton.setOnClickListener {
            findNextSnowLocation()
        }

        settingsButton = binding.settingsButton
        userButton = binding.userButton

        settingsButton.setOnClickListener { view ->
            val popupView = layoutInflater.inflate(R.layout.settings_popup, null)
            val popupWindow = PopupWindow(
                popupView,
                250.dpToPx(this),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.elevation = 10f
            popupWindow.setBackgroundDrawable(null)

            // Pre-create both submenus
            val themeSubmenuView = layoutInflater.inflate(R.layout.theme_submenu, null)
            val themeSubmenuWindow = PopupWindow(
                themeSubmenuView,
                250.dpToPx(this),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            themeSubmenuWindow.elevation = 10f
            themeSubmenuWindow.setBackgroundDrawable(null)

            val basemapSubmenuView = layoutInflater.inflate(R.layout.basemap_submenu, null)
            val basemapSubmenuWindow = PopupWindow(
                basemapSubmenuView,
                250.dpToPx(this),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            basemapSubmenuWindow.elevation = 10f
            basemapSubmenuWindow.setBackgroundDrawable(null)

            // Measure views for proper positioning
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            themeSubmenuView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            basemapSubmenuView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )

            // Get content containers and ensure visibility
            val mainMenuContent = popupView.findViewById<LinearLayout>(R.id.menuContent)
            mainMenuContent.apply {
                visibility = View.VISIBLE
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(Color.BLACK)
                    } else if (child is LinearLayout) {
                        for (j in 0 until child.childCount) {
                            val nestedChild = child.getChildAt(j)
                            if (nestedChild is TextView) {
                                nestedChild.setTextColor(Color.BLACK)
                            }
                        }
                    }
                }
            }

            // Set up theme submenu content
            val themeSubmenuContent = themeSubmenuView.findViewById<LinearLayout>(R.id.submenuContent)
            themeSubmenuContent.apply {
                visibility = View.VISIBLE
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(Color.BLACK)
                    }
                }
                alpha = 0f
            }

            // Set up basemap submenu content
            val basemapSubmenuContent = basemapSubmenuView.findViewById<LinearLayout>(R.id.submenuContent)
            basemapSubmenuContent.apply {
                visibility = View.VISIBLE
                for (i in 0 until childCount) {
                    val child = getChildAt(i)
                    if (child is TextView) {
                        child.setTextColor(Color.BLACK)
                    }
                }
                alpha = 0f
            }

            // Theme submenu listeners
            themeSubmenuView.findViewById<TextView>(R.id.themeOption1).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    applyDarkTheme()
                    themeSubmenuWindow.dismiss()
                }
            }
            themeSubmenuView.findViewById<TextView>(R.id.themeOption2).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    applySnowTheme()
                    themeSubmenuWindow.dismiss()
                }
            }
            themeSubmenuView.findViewById<TextView>(R.id.themeOption3).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    applyBlizzardTheme()
                    themeSubmenuWindow.dismiss()
                }
            }

            // Basemap submenu listeners
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption1).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    showSnackbar("Basemap 1 selected")
                    basemapSubmenuWindow.dismiss()
                }
            }
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption2).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    showSnackbar("Basemap 2 selected")
                    basemapSubmenuWindow.dismiss()
                }
            }
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption3).apply {
                setTextColor(Color.BLACK)
                setOnClickListener {
                    showSnackbar("Basemap 3 selected")
                    basemapSubmenuWindow.dismiss()
                }
            }

            // Calculate position relative to map bottom
            val mapBottom = binding.mapCardView.bottom + binding.mapCardView.top
            val popupY = mapBottom - popupView.measuredHeight + 20

            // Main menu option1 (Theme) click handler
            popupView.findViewById<LinearLayout>(R.id.option1Container).setOnClickListener {
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                themeSubmenuWindow.showAtLocation(
                    view,
                    Gravity.NO_GRAVITY,
                    location[0] - 10,
                    popupY
                )

                mainMenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))

                Handler(Looper.getMainLooper()).postDelayed({
                    themeSubmenuContent.alpha = 1f
                    themeSubmenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
                }, 50)

                Handler(Looper.getMainLooper()).postDelayed({
                    popupWindow.dismiss()
                }, 300)
            }

            // Main menu option2 (Basemap) click handler
            popupView.findViewById<LinearLayout>(R.id.option2Container).setOnClickListener {
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                basemapSubmenuWindow.showAtLocation(
                    view,
                    Gravity.NO_GRAVITY,
                    location[0] - 10,
                    popupY
                )

                mainMenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))

                Handler(Looper.getMainLooper()).postDelayed({
                    basemapSubmenuContent.alpha = 1f
                    basemapSubmenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
                }, 50)

                Handler(Looper.getMainLooper()).postDelayed({
                    popupWindow.dismiss()
                }, 300)
            }

            // Music toggle with state persistence
            val musicToggle = popupView.findViewById<Switch>(R.id.musicToggle)
            musicToggle.isChecked = isMusicEnabled
            musicToggle.setOnCheckedChangeListener { _, isChecked ->
                isMusicEnabled = isChecked
                if (isChecked) {
                    startMusic()
                } else {
                    stopMusic()
                }
            }

            // Show main menu with calculated position
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            popupWindow.showAtLocation(
                view,
                Gravity.NO_GRAVITY,
                location[0] - 10,
                popupY
            )
        }

        setupUserButton()
        initializeMusic()
    }


    private var overlay: View? = null

    private fun applyDarkTheme() {
        overlay = binding.themeOverlay
        overlay?.setBackgroundColor(Color.argb(120, 0, 0, 0))  // More opaque dark overlay
        overlay?.visibility = View.VISIBLE

        // Add text color to the button
        binding.findSnowButton.setTextColor(Color.WHITE)

        // Darker UI elements
        binding.findSnowButton.setBackgroundColor(Color.argb(255, 40, 40, 40))
        binding.settingsButton.setBackgroundColor(Color.argb(255, 40, 40, 40))
        binding.userButton.setBackgroundColor(Color.argb(255, 40, 40, 40))

        // Darker circle
        userCircle?.strokeColor = Color.argb(255, 100, 100, 100)  // Darker grey outline
        userCircle?.fillColor = Color.argb(90, 50, 50, 50)      // Darker grey fill

        // Darker card
        binding.mapCardView.setCardBackgroundColor(Color.argb(255, 30, 30, 30))

        // Update markers to darker colors
        snowLocations.forEach { location ->
            location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        }
        // Update highlighted marker if one is selected
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))

        currentTheme = "dark"

        showSnackbar("Dark theme applied")
    }

    private fun applySnowTheme() {
        overlay?.visibility = View.GONE

        // Add text color to the button
        binding.findSnowButton.setTextColor(Color.WHITE)

        // Use the default color from the theme - this will match the initial light purple
        val defaultColor = getColor(com.google.android.material.R.color.m3_sys_color_dynamic_light_primary)
        binding.findSnowButton.setBackgroundColor(defaultColor)
        binding.settingsButton.setBackgroundColor(defaultColor)
        binding.userButton.setBackgroundColor(defaultColor)

        // Original blue/cyan circle
        userCircle?.strokeColor = Color.argb(255, 0, 150, 255)    // Blue outline
        userCircle?.fillColor = Color.argb(70, 0, 255, 255)       // Cyan fill

        // Reset card
        binding.mapCardView.setCardBackgroundColor(Color.WHITE)

        // Update markers to blue colors
        snowLocations.forEach { location ->
            location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        }
        // Update highlighted marker if one is selected
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))

        currentTheme = "snow"

        showSnackbar("Snow theme applied")
    }

    private fun applyBlizzardTheme() {
        overlay = binding.themeOverlay
        overlay?.setBackgroundColor(Color.argb(100, 255, 255, 255))  // More visible white overlay
        overlay?.visibility = View.VISIBLE

        // Lighter UI elements
        binding.findSnowButton.setBackgroundColor(Color.argb(255, 200, 220, 255))
        binding.settingsButton.setBackgroundColor(Color.argb(255, 200, 220, 255))
        binding.userButton.setBackgroundColor(Color.argb(255, 200, 220, 255))

        // White/blue circle
        userCircle?.strokeColor = Color.argb(255, 255, 255, 255)  // White outline
        userCircle?.fillColor = Color.argb(90, 220, 240, 255)     // Light blue-white fill

        // Lighter card
        binding.mapCardView.setCardBackgroundColor(Color.argb(255, 240, 245, 255))

        // Update markers to white/light blue colors
        snowLocations.forEach { location ->
            location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
        }
        // Update highlighted marker if one is selected
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))

        currentTheme = "blizzard"

        showSnackbar("Blizzard theme applied")
    }





    private fun setupThemeSubmenuListeners(submenuView: View, submenuWindow: PopupWindow) {
        submenuView.findViewById<TextView>(R.id.themeOption1).setOnClickListener {
            showSnackbar("Theme 1 selected")
            submenuWindow.dismiss()
        }
        submenuView.findViewById<TextView>(R.id.themeOption2).setOnClickListener {
            showSnackbar("Theme 2 selected")
            submenuWindow.dismiss()
        }
        submenuView.findViewById<TextView>(R.id.themeOption3).setOnClickListener {
            showSnackbar("Theme 3 selected")
            submenuWindow.dismiss()
        }
    }

    private fun setupBasemapSubmenuListeners(submenuView: View, submenuWindow: PopupWindow) {
        submenuView.findViewById<TextView>(R.id.basemapOption1).setOnClickListener {
            showSnackbar("Basemap 1 selected")
            submenuWindow.dismiss()
        }
        submenuView.findViewById<TextView>(R.id.basemapOption2).setOnClickListener {
            showSnackbar("Basemap 2 selected")
            submenuWindow.dismiss()
        }
        submenuView.findViewById<TextView>(R.id.basemapOption3).setOnClickListener {
            showSnackbar("Basemap 3 selected")
            submenuWindow.dismiss()
        }
    }


    private fun createUserLocationMarker() {
        // Create semi-transparent circle marker for user location
        val circleOptions = CircleOptions()
            .center(madisonLocation)
            .radius(BASE_CIRCLE_RADIUS)
            .strokeWidth(5f)  // Increased from 3f to 5f for thicker outline
            .strokeColor(Color.argb(255, 0, 150, 255))  // Solid blue outline
            .fillColor(Color.argb(70, 0, 255, 255))     // Semi-transparent cyan fill

        userCircle = mMap.addCircle(circleOptions)

        // Add zoom level listener to adjust circle size
        mMap.setOnCameraIdleListener {
            updateCircleSize()
        }
    }

    private fun updateCircleSize() {
        val zoom = mMap.cameraPosition.zoom.toDouble()
        // Adjust radius based on zoom level (exponentially)
        val newRadius = BASE_CIRCLE_RADIUS * pow(1.1, zoom)
        userCircle?.radius = newRadius
    }




    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in kilometers

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c
    }

    private fun showSnackbar(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_margin_bottom)
        snackbarView.layoutParams = params

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.gravity = Gravity.CENTER_HORIZONTAL

        snackbar.show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val northAmerica = LatLngBounds(
            LatLng(20.0, -130.0),
            LatLng(60.0, -60.0)
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(northAmerica, 100))

        // Enable location features if permission is granted
        enableMyLocation()  // Add this new function call

        // Customize map UI settings
        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true  // Make sure this is set to true
        }

        createUserLocationMarker()
        fetchSnowLocations()
    }


    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION  // Changed from COARSE to FINE
            ) == PackageManager.PERMISSION_GRANTED) {

            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true

            // Add location button click listener to center on Madison
            mMap.setOnMyLocationButtonClickListener {
                mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(madisonLocation, 10f),
                    1000,
                    null
                )
                true
            }
        } else {
            // Request the permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,  // Add FINE location
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showUserLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {

            // Remove previous marker if it exists
            userMarker?.remove()

            // Add marker at Madison location
            userMarker = mMap.addMarker(
                MarkerOptions()
                    .position(madisonLocation)
                    .title("Your Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )

            // Show city name
            val geocoder = Geocoder(this, Locale.getDefault())
            try {
                val addresses = geocoder.getFromLocation(
                    madisonLocation.latitude,
                    madisonLocation.longitude,
                    1
                )
                addresses?.firstOrNull()?.let { address ->
                    val cityName = address.locality ?: address.subAdminArea ?: address.adminArea
                    cityName?.let { city ->
                        showSnackbar("Located near: $city")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting location name", e)
            }

        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    enableMyLocation()
                } else {
                    showSnackbar("Location permission denied. Using default location: Madison, WI")
                }
            }
        }
    }

    private fun fetchSnowLocations() {
        weatherScope.launch {
            try {
                snowLocations.clear()
                currentSnowIndex = -1
                currentHighlightedMarker = null

                // Create grid points
                val latitudes = (25..65 step 5).map { it.toDouble() }
                val longitudes = (-160..-60 step 10).map { it.toDouble() }

                // Create all coordinate pairs
                val coordinates = latitudes.flatMap { lat ->
                    longitudes.map { lon -> Pair(lat, lon) }
                }.shuffled() // Shuffle to spread out the visible loading across the map

                var totalSnowLocations = 0
                val processedCities = mutableSetOf<String>()

                // Process coordinates in batches
                val batchSize = 5 // Number of API calls per batch
                coordinates.chunked(batchSize).forEach { batch ->
                    try {
                        // Process each coordinate in the batch concurrently
                        val deferredResults = batch.map { (lat, lon) ->
                            async {
                                try {
                                    val url = URL("https://api.openweathermap.org/data/2.5/find?" +
                                            "lat=$lat&lon=$lon" +
                                            "&cnt=50" +
                                            "&appid=$API_KEY")

                                    val connection = url.openConnection() as HttpURLConnection
                                    connection.requestMethod = "GET"
                                    connection.connectTimeout = 5000
                                    connection.readTimeout = 5000

                                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                                        JSONObject(response)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error fetching data for lat: $lat, lon: $lon", e)
                                    null
                                }
                            }
                        }

                        // Wait for all requests in batch to complete
                        deferredResults.awaitAll().filterNotNull().forEach { jsonResponse ->
                            if (jsonResponse.getString("cod") == "200") {
                                val list = jsonResponse.getJSONArray("list")

                                withContext(Dispatchers.Main) {
                                    for (i in 0 until list.length()) {
                                        val item = list.getJSONObject(i)
                                        val coord = item.getJSONObject("coord")
                                        val cityLat = coord.getDouble("lat")
                                        val cityLon = coord.getDouble("lon")

                                        if (cityLat in 25.0..70.0 && cityLon in -170.0..-50.0) {
                                            val name = item.getString("name")
                                            val weather = item.getJSONArray("weather").getJSONObject(0)
                                            val weatherId = weather.getInt("id")

                                            val cityIdentifier = "$name:$cityLat:$cityLon"

                                            if (weatherId in 600..622 && !processedCities.contains(cityIdentifier)) {
                                                processedCities.add(cityIdentifier)
                                                totalSnowLocations++

                                                val desc = weather.getString("description")
                                                val position = LatLng(cityLat, cityLon)

                                                val marker = mMap.addMarker(MarkerOptions()
                                                    .position(position)
                                                    .title(name)
                                                    .snippet("Condition: $desc")
                                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                                        when (currentTheme) {
                                                            "dark" -> BitmapDescriptorFactory.HUE_BLUE
                                                            "snow" -> BitmapDescriptorFactory.HUE_BLUE
                                                            else -> BitmapDescriptorFactory.HUE_CYAN
                                                        }
                                                    )))

                                                snowLocations.add(SnowLocation(position, name, desc, marker))

                                                // Show ongoing progress
                                                if (totalSnowLocations % 5 == 0) {
                                                    showSnackbar("Found $totalSnowLocations snow locations so far...")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Delay between batches
                        delay(500) // 500ms delay between batches

                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing batch", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    if (totalSnowLocations == 0) {
                        showSnackbar("No snow conditions found in North America")
                    } else {
                        showSnackbar("Search complete! Found $totalSnowLocations locations with snow!")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in fetchSnowLocations", e)
                withContext(Dispatchers.Main) {
                    val errorMessage = when (e) {
                        is java.net.UnknownHostException -> "No internet connection"
                        is java.net.SocketTimeoutException -> "Connection timed out"
                        else -> "Error fetching weather data: ${e.message}"
                    }
                    showSnackbar(errorMessage)
                }
            }
        }
    }


    // New function to find a random snow location
    private fun findNextSnowLocation() {
        if (snowLocations.isEmpty()) {
            showSnackbar("No snow locations found yet! Try again in a moment.")
            return
        }

        // Reset previous highlighted marker to theme
        val defaultColor = when (currentTheme) {
            "dark" -> BitmapDescriptorFactory.HUE_BLUE
            "snow" -> BitmapDescriptorFactory.HUE_BLUE
            else -> BitmapDescriptorFactory.HUE_CYAN
        }
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(defaultColor))

        // Pick a random location
        val randomIndex = (0 until snowLocations.size).random()
        val location = snowLocations[randomIndex]

        // Highlight the selected marker
        val highlightColor = when (currentTheme) {
            "dark" -> BitmapDescriptorFactory.HUE_YELLOW
            "snow" -> BitmapDescriptorFactory.HUE_AZURE
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
        location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(highlightColor))
        currentHighlightedMarker = location.marker

        // Calculate distance
        val distance = calculateDistance(
            madisonLocation.latitude, madisonLocation.longitude,
            location.position.latitude, location.position.longitude
        )
        val distanceInMiles = (distance * 0.621371).roundToInt()

        // Animate camera
        mMap.animateCamera(
            CameraUpdateFactory.newLatLngZoom(location.position, 8f),
            1500,
            null
        )

        // Show distance and location info
        showSnackbar("Found snow $distanceInMiles miles away at ${location.name}!")
    }

    // Add this extension function at the end of your MapsActivity class
    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }


    //Music
    private fun initializeMusic() {
        mediaPlayer = MediaPlayer.create(this, R.raw.hypnogogis)
        mediaPlayer?.isLooping = true
        mediaPlayer?.setVolume(1.0f, 1.0f)

        val songDuration = mediaPlayer?.duration ?: 0

        Handler(Looper.getMainLooper()).postDelayed(object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    val currentPosition = player.currentPosition
                    if (currentPosition >= songDuration - 5000) {
                        fadeOutMusic()
                    }
                    if (currentPosition < 100) {
                        player.setVolume(1.0f, 1.0f)
                    }
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 1000)
            }
        }, 1000)

        // Only start music if it's enabled
        if (isMusicEnabled) {
            startMusic()
        }
    }

    private fun startMusic() {
        if (!isMusicPlaying && isMusicEnabled) {  // Check if music is enabled
            mediaPlayer?.start()
            isMusicPlaying = true
        }
    }

    private fun fadeOutMusic(duration: Long = 5000) {  // 5000ms = 5 seconds
        val fadeOut = ValueAnimator.ofFloat(1.0f, 0.0f)
        fadeOut.duration = duration
        fadeOut.addUpdateListener { animation ->
            val volume = animation.animatedValue as Float
            mediaPlayer?.setVolume(volume, volume)
        }
        fadeOut.start()
    }

    private fun stopMusic() {
        if (isMusicPlaying) {
            mediaPlayer?.pause()
            isMusicPlaying = false
        }
    }


    //User Login
    private fun setupUserButton() {
        userButton.setOnClickListener {
            val popupView = layoutInflater.inflate(R.layout.login_popup, null)

            // Get screen width and calculate popup width (2/3 of screen width)
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val popupWidth = (screenWidth * 2) / 3

            // Create popup window
            val popupWindow = PopupWindow(
                popupView,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            popupWindow.elevation = 10f
            popupWindow.setBackgroundDrawable(null)

            // Get references to views
            val loginTitle = popupView.findViewById<TextView>(R.id.loginTitle)
            val emailInput = popupView.findViewById<EditText>(R.id.emailInput)
            val passwordInput = popupView.findViewById<EditText>(R.id.passwordInput)
            val signInButton = popupView.findViewById<Button>(R.id.signInButton)
            val createAccountText = popupView.findViewById<TextView>(R.id.createAccountText)

            // Create drawable for rounded corners
            val backgroundDrawable = GradientDrawable().apply {
                cornerRadius = 14f * resources.displayMetrics.density  // 14dp corners
                setStroke(1, Color.parseColor("#E0E0E0"))  // 1px stroke with light gray color
            }

            // Apply theme-specific colors
            when (currentTheme) {
                "dark" -> {
                    backgroundDrawable.setColor(Color.argb(255, 40, 40, 40))
                    popupView.background = backgroundDrawable
                    loginTitle.setTextColor(Color.WHITE)
                    emailInput.setTextColor(Color.WHITE)
                    emailInput.setHintTextColor(Color.GRAY)
                    emailInput.background.setColorFilter(Color.argb(255, 60, 60, 60), PorterDuff.Mode.SRC_ATOP)
                    passwordInput.setTextColor(Color.WHITE)
                    passwordInput.setHintTextColor(Color.GRAY)
                    passwordInput.background.setColorFilter(Color.argb(255, 60, 60, 60), PorterDuff.Mode.SRC_ATOP)
                    signInButton.setBackgroundColor(Color.argb(255, 80, 80, 80))
                    createAccountText.setTextColor(Color.LTGRAY)
                }
                "blizzard" -> {
                    backgroundDrawable.setColor(Color.argb(255, 240, 245, 255))
                    popupView.background = backgroundDrawable
                    signInButton.setBackgroundColor(Color.argb(255, 200, 220, 255))
                    emailInput.background.setColorFilter(Color.argb(255, 230, 240, 255), PorterDuff.Mode.SRC_ATOP)
                    passwordInput.background.setColorFilter(Color.argb(255, 230, 240, 255), PorterDuff.Mode.SRC_ATOP)
                }
                else -> {
                    // Snow theme (default)
                    backgroundDrawable.setColor(Color.WHITE)
                    popupView.background = backgroundDrawable
                    val defaultColor = getColor(com.google.android.material.R.color.m3_sys_color_dynamic_light_primary)
                    signInButton.setBackgroundColor(defaultColor)
                    loginTitle.setTextColor(defaultColor)  // Added title color
                    emailInput.setTextColor(defaultColor)
                    emailInput.setHintTextColor(defaultColor)  // Added hint text color
                    passwordInput.setTextColor(defaultColor)
                    passwordInput.setHintTextColor(defaultColor)  // Added hint text color
                }
            }

            // Setup click listeners
            signInButton.setOnClickListener {
                val email = emailInput.text.toString()
                val password = passwordInput.text.toString()

                if (email.isEmpty() || password.isEmpty()) {
                    showSnackbar("Please fill in all fields")
                    return@setOnClickListener
                }

                showSnackbar("Signing in...")
                popupWindow.dismiss()
            }

            createAccountText.setOnClickListener {
                showSnackbar("Create account clicked!")
            }

            // Center the popup in the screen
            popupWindow.showAtLocation(
                binding.root,
                Gravity.CENTER,
                0,
                0
            )

            // Add fade in animation
            popupView.alpha = 0f
            popupView.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }



    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        weatherScope.cancel()
    }

}