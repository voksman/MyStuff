package com.example.weatherapp

import android.content.SharedPreferences
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
class MainActivityUnitTest {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        context = ApplicationProvider.getApplicationContext()
        sharedPreferences = context.getSharedPreferences(
            "MyPrefs", Context.MODE_PRIVATE
        )
        editor = sharedPreferences.edit()
    }

    @After
    fun cleanup() {
        editor.clear()
        editor.apply()
    }

    @Test
    fun testStoreAndRetrieveDoubleValue() {
        val doubleValue = 123.45
        editor.putFloat("double_key", doubleValue.toFloat())
        editor.apply()

        val defaultValue = 0.0
        val storedValue = sharedPreferences.getFloat("double_key", defaultValue.toFloat()).toDouble()

        // Assert that the retrieved value matches the stored value
        assert(doubleValue == storedValue)
    }

    @Test
    fun testRetrieveDefaultValue() {
        val defaultValue = 0.0
        val storedValue = sharedPreferences.getFloat("non_existent_key", defaultValue.toFloat()).toDouble()

        // Assert that the retrieved value matches the default value
        assert(defaultValue == storedValue)
    }

    @Test
    fun testLocationPermissionGranted() {
        Mockito.`when`(
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ).thenReturn(PackageManager.PERMISSION_GRANTED)

        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.CREATED)

        val mainActivityController = activityScenario.onActivity { activity ->
            val locationManager = LocationServices.getFusedLocationProviderClient(activity)
            assert(locationManager == fusedLocationProviderClient)
        }

        mainActivityController.close()
    }

    @Test
    fun testLocationPermissionDenied() {
        Mockito.`when`(
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            )
        ).thenReturn(PackageManager.PERMISSION_DENIED)

        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.CREATED)

        val mainActivityController = activityScenario.onActivity { activity ->
            val locationManager = LocationServices.getFusedLocationProviderClient(activity)
            assert(locationManager != fusedLocationProviderClient)
        }

        mainActivityController.close()
    }
}
