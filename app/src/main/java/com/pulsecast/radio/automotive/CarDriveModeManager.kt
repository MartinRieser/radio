package com.pulsecast.radio.automotive

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Intelligent Automotive Car Drive Mode Detector.
 * Automatically activates a simplified, distraction-free Car GUI when:
 * 1. Phone is connected to a Bluetooth Car Audio head unit.
 * 2. Vehicle speed is > 35 km/h (distinguishing driving from bicycles < 22 km/h and train/tram stops).
 * Also supports 1-tap manual override toggle in the app header.
 */
class CarDriveModeManager(private val context: Context) {

    private val _isCarModeActive = MutableStateFlow(false)
    val isCarModeActive: StateFlow<Boolean> = _isCarModeActive.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0f)
    val currentSpeedKmh: StateFlow<Float> = _currentSpeedKmh.asStateFlow()

    private val _carConnectionReason = MutableStateFlow("Bereit")
    val carConnectionReason: StateFlow<String> = _carConnectionReason.asStateFlow()

    private var isBluetoothCarConnected = false
    private var isManualOverride = false
    private var locationManager: LocationManager? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val action = intent.action ?: return

            @Suppress("DEPRECATION")
            val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            }

            val deviceName = try { device?.name ?: "" } catch (_: Exception) { "" }

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (isCarAudioDevice(deviceName)) {
                        isBluetoothCarConnected = true
                        _carConnectionReason.value = "Bluetooth Auto-Audio ($deviceName)"
                        Log.d("PulseCastCar", "Car Bluetooth connected: $deviceName")
                        updateCarModeState()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (isCarAudioDevice(deviceName)) {
                        isBluetoothCarConnected = false
                        _carConnectionReason.value = "Bluetooth getrennt"
                        Log.d("PulseCastCar", "Car Bluetooth disconnected: $deviceName")
                        updateCarModeState()
                    }
                }
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.hasSpeed()) {
                val speedKmh = location.speed * 3.6f // m/s to km/h
                _currentSpeedKmh.value = speedKmh

                // Bicycle speeds are usually 10-22 km/h. Car driving is > 35 km/h.
                val isCarSpeed = speedKmh >= 35.0f

                if (isCarSpeed && isBluetoothCarConnected) {
                    _carConnectionReason.value = "Autofahrt (${speedKmh.toInt()} km/h + BT)"
                } else if (isCarSpeed) {
                    _carConnectionReason.value = "Geschwindigkeit ${speedKmh.toInt()} km/h"
                }

                updateCarModeState()
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        registerBluetoothReceiver()
        registerLocationUpdates()
    }

    private fun isCarAudioDevice(name: String): Boolean {
        val lowerName = name.lowercase()
        val carKeywords = listOf("car", "auto", "bmw", "audi", "mercedes", "mb", "vw", "volkswagen", "porsche", "ford", "sync", "toyota", "honda", "nissan", "hyundai", "kia", "uconnect", "carplay", "androidauto", "pioneer", "kenwood", "alpine")
        return carKeywords.any { lowerName.contains(it) }
    }

    private fun registerBluetoothReceiver() {
        try {
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            context.registerReceiver(bluetoothReceiver, filter)
        } catch (e: Exception) {
            Log.w("PulseCastCar", "Failed to register Bluetooth receiver: ${e.message}")
        }
    }

    private fun registerLocationUpdates() {
        try {
            locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val lm = locationManager ?: return
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    10f,
                    locationListener
                )
            }
        } catch (e: Exception) {
            Log.w("PulseCastCar", "Location updates not available: ${e.message}")
        }
    }

    private fun updateCarModeState() {
        if (isManualOverride) return // Keep user manual choice
        val speedKmh = _currentSpeedKmh.value
        val shouldBeActive = isBluetoothCarConnected || (speedKmh >= 35.0f && isBluetoothCarConnected)
        _isCarModeActive.value = shouldBeActive
    }

    fun toggleManualCarMode() {
        isManualOverride = true
        _isCarModeActive.value = !_isCarModeActive.value
        if (_isCarModeActive.value) {
            _carConnectionReason.value = "Manuell aktiviert"
        } else {
            _carConnectionReason.value = "Manuell beendet"
        }
    }

    fun exitCarMode() {
        isManualOverride = true
        _isCarModeActive.value = false
        _carConnectionReason.value = "Beendet"
    }

    fun unregister() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: Exception) {}
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: Exception) {}
    }
}
