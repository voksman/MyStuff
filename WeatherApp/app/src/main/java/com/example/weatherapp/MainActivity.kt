package com.example.weatherapp

import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest

/*
 * App fetches the weather info for specific location
 */
class MainActivity : ComponentActivity() {
    private lateinit var cityEditText: EditText
    private lateinit var weatherTextView: TextView
    private lateinit var weatherIconImageView: ImageView
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cityEditText = findViewById(R.id.cityEditText)
        weatherTextView = findViewById(R.id.weatherTextView)
        weatherIconImageView = findViewById(R.id.weatherIconImageView)

        // Set a click listener for the fetchWeather button
        val fetchWeatherButton: Button = findViewById(R.id.fetchWeatherButton)
        fetchWeatherButton.setOnClickListener {
            val locationName = cityEditText.text.toString()
            getLocationCoordinates(locationName)
        }
        // Set a click listener for the MyLocationWeather button
        val fetchMyLocationWeather: Button = findViewById(R.id.fetchMyLocationWeatherButton)
        fetchMyLocationWeather.setOnClickListener {
            checkLocationPermissions()
        }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        displayLastLocation()
    }

    /*
     * To get latitude&longitude for specific city/zip code
     */
    private fun getLocationCoordinates(locationName: String) {
        val geocoder = Geocoder(this, Locale.getDefault())

        lifecycleScope.launch {
            try {
                val locations = withContext(Dispatchers.IO) {
                    geocoder.getFromLocationName(locationName, 1)
                }

                if (!locations.isNullOrEmpty()) {
                    val latitude = locations[0].latitude
                    val longitude = locations[0].longitude
                    fetchWeatherByCoordinates(latitude, longitude)
                } else {
                    weatherTextView.text = "Location not found."
                }
            } catch (e: IOException) {
                weatherTextView.text = "Error fetching location data."
            }
        }
    }

    /*
     * To obtain weather info and show it.
     */
    private suspend fun fetchWeatherByCoordinates(latitude: Double, longitude: Double) {
        val service = RetrofitClient.instance.create(WeatherService::class.java)

        try {
            val response = withContext(Dispatchers.IO) {
                service.getWeatherByCoordinates(latitude, longitude, TEMP_UNITS, API_KEY)
            }

            if (response != null) {
                val temperature = response.main.temp
                val feels_like =  response.main.feels_like
                val humidity =  response.main.humidity
                val city =  response.name
                val description = response.weather[0].description
                val iconCode = response.weather[0].icon

                val weatherInfo = """
                City:     $city
                Temperature: $temperature°F
                Feels like:  $feels_like°F
                Humidity:    $humidity%
                Description: $description
                """
                weatherTextView.text = weatherInfo

                // Load and display the weather condition icon with caching
                val iconUrl = "https://openweathermap.org/img/w/$iconCode.png"
                Glide.with(this@MainActivity)
                    .load(iconUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache both original and resized images
                    .into(weatherIconImageView)
                saveLastLocation(latitude, longitude)
            } else {
                weatherTextView.text = "Weather data not available."
            }
        } catch (e: Exception) {
            weatherTextView.text = "Error fetching weather data."
        }
    }

    /*
     * Check Location permission. If granted - fetch local weather info, if not - request permission
     */
    private fun checkLocationPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            // Location permissions are granted, proceed to get the location
            getCurrentLocation()
        } else {
            // Location permissions are not granted, request them
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST)
        }
    }

    /*
     * Obrain current location and fetch local weather info
     */
    private fun getCurrentLocation() {
    // Request location updates (in your getCurrentLocation function):
        val locationRequest = LocationRequest.create()
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        locationRequest.interval = LOCATION_REQ_INTERVAL

        // Define a location callback to handle location updates
        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                locationResult ?: return
                for (location in locationResult.locations) {
                    // Use a coroutine to fetch weather data for received my location
                    CoroutineScope(Dispatchers.Main).launch {
                        fetchWeatherByCoordinates(location.latitude, location.longitude)
                    }
                }
            }
        }
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            weatherTextView.text = "Location permission not granted."
        }
    }

    /*
     * Cllback to get current location when location permission is granted.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // Call the super method
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Location permissions granted, fetch location
                getCurrentLocation()
            } else {
                // Location permissions denied, inform the user
                weatherTextView.text =
                    "Location permission denied. Cannot fetch weather for your location."
            }
        }
    }

    /*
     * Get and diplay the weather data for the last city searched
     */
    private fun displayLastLocation() {
        val sharedPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastSearchedLatitude = sharedPrefs.getFloat("lastSearchedLatitude", 0.0f)
        val lastSearchedLongitude = sharedPrefs.getFloat("lastSearchedLongitude", 0.0f)

        if (lastSearchedLatitude != 0.0f && lastSearchedLongitude != 0.0f) {
            // Use a coroutine to fetch weather data for last location
            CoroutineScope(Dispatchers.Main).launch {
                fetchWeatherByCoordinates(
                    lastSearchedLatitude.toDouble(),
                    lastSearchedLongitude.toDouble()
                )
            }
        }
    }

    /*
     * Save last the searched location for the next run
     */
    private fun saveLastLocation(latitude: Double, longitude: Double) {
        val sharedPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()
        editor.putFloat("lastSearchedLatitude", latitude.toFloat())
        editor.putFloat("lastSearchedLongitude", longitude.toFloat())
        editor.apply()
    }

    /*
     * Constants
     */
    companion object {
        // openweathermap.org/api API KEY
        const val API_KEY = "460397477cc26c2d9f59b6cc9f7acc3b"
        // Units for Temperature (Fahrenheit)
        const val TEMP_UNITS = "imperial"
        // Location Permission request definition
        const val LOCATION_PERMISSION_REQUEST = 1001
        // Location Request interval in milliseconds
        const val LOCATION_REQ_INTERVAL = 10000L
        // Units for Temperature (Fahrenheit)
        const val PREF_NAME = "com.example.weatherapp.MYSHAREDPREFERANCES.name"
    }
}