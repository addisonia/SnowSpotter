package com.example.snowspotterapp2

import android.Manifest
import android.content.pm.PackageManager
import android.location.*
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.example.snowspotterapp2.databinding.ActivityMapsBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection
import java.util.Locale

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
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
                        Toast.makeText(this, "Located near: $city", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(
                        this,
                        "Location permission denied. Using default location: Madison, WI",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun fetchSnowLocations() {
        weatherScope.launch {
            try {
                // Multiple API calls to cover different regions
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
                            }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (totalSnowLocations == 0) {
                        Toast.makeText(
                            this@MapsActivity,
                            "No snow conditions found in North America",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this@MapsActivity,
                            "Found $totalSnowLocations locations with snow!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MapsActivity,
                        "Error fetching weather data: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        weatherScope.cancel()
    }
}