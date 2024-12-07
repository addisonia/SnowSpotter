package com.example.snowspotterapp2

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.snowspotterapp2.databinding.ActivityMapsBinding
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import java.net.HttpURLConnection

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val weatherScope = CoroutineScope(Dispatchers.IO + Job())
    private val API_KEY = "6d05802c8dd306c4a02c96c9bf433ea2"
    private val TAG = "MapsActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        enableMyLocation()
        fetchSnowLocations()
    }

    private fun enableMyLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            getCurrentLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun getCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val currentLatLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 10f))
                }
            }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Location permission needed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun fetchSnowLocations() {
        weatherScope.launch {
            try {
                // Using the find weather API to get a broader range of weather data
                val url = URL("https://api.openweathermap.org/data/2.5/find?" +
                        "lat=45&lon=-100" +  // Center point (adjust as needed)
                        "&cnt=50" +  // Number of cities to return
                        "&appid=$API_KEY")

                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Weather API Response: $response")  // Debug log

                val jsonResponse = JSONObject(response)
                val list = jsonResponse.getJSONArray("list")

                var snowLocationsFound = false

                withContext(Dispatchers.Main) {
                    for (i in 0 until list.length()) {
                        val item = list.getJSONObject(i)
                        val weather = item.getJSONArray("weather").getJSONObject(0)
                        val weatherId = weather.getInt("id")

                        // Check for snow conditions (codes 600-622)
                        if (weatherId in 600..622) {
                            snowLocationsFound = true
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

                            Log.d(TAG, "Added snow marker at $name ($lat, $lon)")  // Debug log
                        }
                    }

                    if (!snowLocationsFound) {
                        Toast.makeText(
                            this@MapsActivity,
                            "No snow conditions found in the current area",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching weather data", e)  // Debug log
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MapsActivity,
                        "Error fetching weather data: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
                    Toast.makeText(
                        this,
                        "Location permission denied",
                        Toast.LENGTH_SHORT
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