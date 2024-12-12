package com.example.snowspotterapp2

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.PopupWindow
import android.widget.Switch
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
    private val BASE_CIRCLE_RADIUS = 25000.0 // 15km base radius

    private lateinit var settingsButton: MaterialButton
    private lateinit var userButton: MaterialButton


    private var mediaPlayer: MediaPlayer? = null
    private var isMusicPlaying = false
    private var isMusicEnabled = true  // Default to true since music starts enabled


    private var currentTheme = "snow"  // Default theme


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

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

            // Modify each PopupWindow creation to add these lines:
            popupWindow.setBackgroundDrawable(null)  // Remove default background

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

            // Main menu option1 (Theme) click handler
            popupView.findViewById<LinearLayout>(R.id.option1Container).setOnClickListener {
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                themeSubmenuWindow.showAtLocation(
                    view,
                    Gravity.NO_GRAVITY,
                    location[0] - 10,
                    location[1] - 500
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
                    location[1] - 500
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

            // Show main menu
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            popupWindow.showAtLocation(
                view,
                Gravity.NO_GRAVITY,
                location[0] - 10,
                location[1] - 500
            )
        }


        userButton.setOnClickListener {
            // TODO: Implement user profile functionality
            showSnackbar("User profile clicked!")
        }

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

        // Reset UI elements to default blue
        val defaultBlue = getColor(com.google.android.material.R.color.design_default_color_primary)
        binding.findSnowButton.setBackgroundColor(defaultBlue)
        binding.settingsButton.setBackgroundColor(defaultBlue)
        binding.userButton.setBackgroundColor(defaultBlue)

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
        val newRadius = BASE_CIRCLE_RADIUS * pow(0.75, zoom)
        userCircle?.radius = newRadius
    }

    private fun findNextSnowLocation() {
        if (snowLocations.isEmpty()) {
            showSnackbar("No snow locations found yet! Try again in a moment.")
            return
        }

        // Reset if we've gone through all locations
        if (currentSnowIndex >= snowLocations.size - 1) {
            currentSnowIndex = -1
        }

        // Sort locations by distance if this is the first click
        if (currentSnowIndex == -1) {
            snowLocations.sortBy { location ->
                calculateDistance(
                    madisonLocation.latitude, madisonLocation.longitude,
                    location.position.latitude, location.position.longitude
                )
            }
        }

        // Move to next location
        currentSnowIndex++
        val location = snowLocations[currentSnowIndex]

        // Reset previous highlighted marker to theme
        val defaultColor = when (currentTheme) {
            "dark" -> BitmapDescriptorFactory.HUE_BLUE
            "snow" -> BitmapDescriptorFactory.HUE_BLUE
            else -> BitmapDescriptorFactory.HUE_CYAN
        }
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(defaultColor))

        // Highlight current marker
        location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
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


        // Set new highlighted marker color based on current theme
        val highlightColor = when (currentTheme) {
            "dark" -> BitmapDescriptorFactory.HUE_YELLOW
            "snow" -> BitmapDescriptorFactory.HUE_AZURE
            else -> BitmapDescriptorFactory.HUE_VIOLET
        }
        location.marker?.setIcon(BitmapDescriptorFactory.defaultMarker(highlightColor))
        currentHighlightedMarker = location.marker


        // Show distance and location info
        showSnackbar("Found snow $distanceInMiles miles away at ${location.name}!")
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

                val regions = listOf(
                    Pair(45.0, -100.0),
                    Pair(45.0, -80.0),
                    Pair(45.0, -120.0),
                    Pair(60.0, -100.0)
                )

                var totalSnowLocations = 0

                for (region in regions) {
                    val url = URL("https://api.openweathermap.org/data/2.5/find?" +
                            "lat=${region.first}&lon=${region.second}" +
                            "&cnt=50" +
                            "&appid=$API_KEY")

                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)
                    val list = jsonResponse.getJSONArray("list")

                    withContext(Dispatchers.Main) {
                        for (i in 0 until list.length()) {
                            val item = list.getJSONObject(i)
                            val weather = item.getJSONArray("weather").getJSONObject(0)
                            val weatherId = weather.getInt("id")

                            if (weatherId in 600..622) {
                                totalSnowLocations++
                                val coord = item.getJSONObject("coord")
                                val lat = coord.getDouble("lat")
                                val lon = coord.getDouble("lon")
                                val name = item.getString("name")
                                val desc = weather.getString("description")
                                val position = LatLng(lat, lon)

                                val marker = mMap.addMarker(MarkerOptions()
                                    .position(position)
                                    .title("$name")
                                    .snippet("Condition: $desc")
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                        when (currentTheme) {
                                            "dark" -> BitmapDescriptorFactory.HUE_BLUE                                    "snow" -> BitmapDescriptorFactory.HUE_BLUE
                                            else -> BitmapDescriptorFactory.HUE_CYAN
                                        }
                                    )))

                                snowLocations.add(SnowLocation(position, name, desc, marker))
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (totalSnowLocations == 0) {
                        showSnackbar("No snow conditions found in North America")
                    } else {
                        showSnackbar("Found $totalSnowLocations locations with snow!")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather data", e)
                withContext(Dispatchers.Main) {
                    showSnackbar("Error fetching weather data: ${e.message}")
                }
            }
        }
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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        weatherScope.cancel()
    }

}