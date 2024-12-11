package com.example.snowspotterapp2

import android.Manifest
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.graphics.Color
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

            // Pre-create submenus
            val themeSubmenuView = layoutInflater.inflate(R.layout.theme_submenu, null)
            val themeSubmenuWindow = PopupWindow(
                themeSubmenuView,
                250.dpToPx(this),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            themeSubmenuWindow.elevation = 10f

            // Get content containers
            val mainMenuContent = popupView.findViewById<LinearLayout>(R.id.menuContent)
            val themeSubmenuContent = themeSubmenuView.findViewById<LinearLayout>(R.id.submenuContent)

            // Initially set the submenu content to invisible
            themeSubmenuContent.alpha = 0f

            val basemapSubmenuView = layoutInflater.inflate(R.layout.basemap_submenu, null)
            val basemapSubmenuWindow = PopupWindow(
                basemapSubmenuView,
                250.dpToPx(this),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            )
            basemapSubmenuWindow.elevation = 10f

            val basemapSubmenuContent = basemapSubmenuView.findViewById<LinearLayout>(R.id.submenuContent)
            basemapSubmenuContent.alpha = 0f

            // Set up theme submenu listeners
            themeSubmenuView.findViewById<TextView>(R.id.themeOption1).setOnClickListener {
                showSnackbar("Theme 1 selected")
                themeSubmenuWindow.dismiss()
            }
            themeSubmenuView.findViewById<TextView>(R.id.themeOption2).setOnClickListener {
                showSnackbar("Theme 2 selected")
                themeSubmenuWindow.dismiss()
            }
            themeSubmenuView.findViewById<TextView>(R.id.themeOption3).setOnClickListener {
                showSnackbar("Theme 3 selected")
                themeSubmenuWindow.dismiss()
            }

            // Set up basemap submenu listeners
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption1).setOnClickListener {
                showSnackbar("Basemap 1 selected")
                basemapSubmenuWindow.dismiss()
            }
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption2).setOnClickListener {
                showSnackbar("Basemap 2 selected")
                basemapSubmenuWindow.dismiss()
            }
            basemapSubmenuView.findViewById<TextView>(R.id.basemapOption3).setOnClickListener {
                showSnackbar("Basemap 3 selected")
                basemapSubmenuWindow.dismiss()
            }

            popupView.findViewById<LinearLayout>(R.id.option1Container).setOnClickListener {
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                // Show submenu window (without animation)
                themeSubmenuWindow.showAtLocation(
                    view,
                    Gravity.NO_GRAVITY,
                    location[0] - 10,
                    location[1] - 500
                )

                // Animate main menu content out
                mainMenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))

                Handler(Looper.getMainLooper()).postDelayed({
                    // Fade in and slide in submenu content
                    themeSubmenuContent.alpha = 1f
                    themeSubmenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
                }, 50)

                Handler(Looper.getMainLooper()).postDelayed({
                    popupWindow.dismiss()
                }, 300)
            }

            popupView.findViewById<LinearLayout>(R.id.option2Container).setOnClickListener {
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                // Show submenu window (without animation)
                basemapSubmenuWindow.showAtLocation(
                    view,
                    Gravity.NO_GRAVITY,
                    location[0] - 10,
                    location[1] - 500
                )

                // Animate main menu content out
                mainMenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_out_left))

                Handler(Looper.getMainLooper()).postDelayed({
                    // Fade in and slide in submenu content
                    basemapSubmenuContent.alpha = 1f
                    basemapSubmenuContent.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_in_right))
                }, 50)

                Handler(Looper.getMainLooper()).postDelayed({
                    popupWindow.dismiss()
                }, 300)
            }

            // Handle music toggle
            val musicToggle = popupView.findViewById<Switch>(R.id.musicToggle)
            musicToggle.setOnCheckedChangeListener { _, isChecked ->
                // TODO: Implement music toggle functionality
            }

            // Show main menu
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            popupWindow.showAtLocation(
                view,
                Gravity.NO_GRAVITY,
                location[0] - 10,
                location[1] - 515
            )
        }


        userButton.setOnClickListener {
            // TODO: Implement user profile functionality
            showSnackbar("User profile clicked!")
        }
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

        // Reset previous highlighted marker
        currentHighlightedMarker?.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))

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
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {

            // This will show the button but not update the blue dot
            mMap.setLocationSource(object : LocationSource {
                override fun activate(listener: LocationSource.OnLocationChangedListener) {
                    // Don't send any location updates
                }
                override fun deactivate() {
                    // Nothing to deactivate
                }
            })

            mMap.isMyLocationEnabled = true

            // Add location button click listener to center on Madison
            mMap.setOnMyLocationButtonClickListener {
                mMap.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(madisonLocation, 10f),
                    1000,
                    null
                )
                true
            }
        }

        // Customize map UI settings
        mMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
        }

        createUserLocationMarker()
        fetchSnowLocations()
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
                    // Enable location layer after permission is granted
                    mMap.isMyLocationEnabled = true
                    showUserLocation()
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
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))

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


    override fun onDestroy() {
        super.onDestroy()
        weatherScope.cancel()
    }
}