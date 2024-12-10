package com.example.snowspotterapp2

import android.Manifest
import android.content.pm.PackageManager
import android.location.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import android.view.Gravity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.snackbar.Snackbar
import com.example.snowspotterapp2.databinding.ActivityMapsBinding
import kotlinx.coroutines.*
import org.json.JSONObject
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

    // Store snow locations
    private val snowLocations = mutableListOf<SnowLocation>()

    data class SnowLocation(
        val position: LatLng,
        val name: String,
        val description: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set up Find Me Snow button click listener
        binding.findSnowButton.setOnClickListener {
            findNearestSnow()
        }
    }

    private fun findNearestSnow() {
        if (snowLocations.isEmpty()) {
            showSnackbar("No snow locations found yet! Try again in a moment.")
            return
        }

        // Find closest snow location
        var closestLocation: SnowLocation? = null
        var shortestDistance = Double.MAX_VALUE

        for (location in snowLocations) {
            val distance = calculateDistance(
                madisonLocation.latitude, madisonLocation.longitude,
                location.position.latitude, location.position.longitude
            )
            if (distance < shortestDistance) {
                shortestDistance = distance
                closestLocation = location
            }
        }

        closestLocation?.let { location ->
            // Convert distance to miles
            val distanceInMiles = (shortestDistance * 0.621371).roundToInt()

            // Animate camera to location
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(location.position, 8f),
                1500,  // 1.5 seconds
                null
            )

            // Show distance and location info
            showSnackbar("Found snow $distanceInMiles miles away at ${location.name}!")
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371 // Earth's radius in kilometers

        val latDistance = Math.toRadians(lat2 - lat1)
        val lonDistance = Math.toRadians(lon2 - lon1)
        val a = sin(latDistance / 2) * sin(latDistance / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(lonDistance / 2) * sin(lonDistance / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return R * c  // Distance in kilometers
    }

    private fun showSnackbar(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        val params = snackbarView.layoutParams as ViewGroup.MarginLayoutParams
        params.bottomMargin = resources.getDimensionPixelSize(R.dimen.snackbar_margin_bottom)
        snackbarView.layoutParams = params

        // Center the text
        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
        textView.gravity = Gravity.CENTER_HORIZONTAL

        snackbar.show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Set default view to show most of North America
        val northAmerica = LatLngBounds(
            LatLng(20.0, -130.0),  // SW bounds
            LatLng(60.0, -60.0)   // NE bounds
        )
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(northAmerica, 100))

        // Add custom marker for user location in Madison
        showUserLocation()
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
                // Clear previous snow locations
                snowLocations.clear()

                val regions = listOf(
                    Pair(45.0, -100.0),  // Central North America
                    Pair(45.0, -80.0),   // Eastern North America
                    Pair(45.0, -120.0),  // Western North America
                    Pair(60.0, -100.0)   // Northern regions
                )

                var totalSnowLocations = 0

                for (region in regions) {
                    val url = URL("https://api.openweathermap.org/data/2.5/find?" +
                            "lat=${region.first}&lon=${region.second}" +
                            "&cnt=50" +  // Maximum cities per request
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
                                mMap.addMarker(MarkerOptions()
                                    .position(position)
                                    .title("$name")
                                    .snippet("Condition: $desc"))

                                // Store snow location
                                snowLocations.add(SnowLocation(position, name, desc))
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

    override fun onDestroy() {
        super.onDestroy()
        weatherScope.cancel()
    }
}