package com.mavericklnp.velocimetroauto

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlin.math.roundToInt

class SpeedScreen(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(carContext)

    private var currentSpeedKmh: Int = 0
    private var maxSpeedKmh: Int = 0
    private var totalDistanceMeters: Float = 0f
    private var lastValidLocation: Location? = null

    private var hasGpsFix: Boolean = false
    private var permissionMissing: Boolean = false

    private val maxScaleKmh = 130
    private val totalBlocks = 15

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            hasGpsFix = true

            val speedKmh = if (location.hasSpeed()) {
                val rawKmh = (location.speed * 3.6f).roundToInt()
                if (rawKmh < 2) 0 else rawKmh
            } else {
                0
            }

            if (speedKmh > maxSpeedKmh) {
                maxSpeedKmh = speedKmh
            }

            if (speedKmh >= 2 && location.accuracy <= 25f) {
                lastValidLocation?.let { previous ->
                    val deltaMeters = previous.distanceTo(location)
                    if (deltaMeters in 0.5f..150f) {
                        totalDistanceMeters += deltaMeters
                    }
                }
                lastValidLocation = location
            } else if (speedKmh == 0) {
                lastValidLocation = location
            }

            currentSpeedKmh = speedKmh
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        startLocationUpdates()
    }

    override fun onStop(owner: LifecycleOwner) {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun resetTrip() {
        maxSpeedKmh = 0
        totalDistanceMeters = 0f
        lastValidLocation = null
        invalidate()
    }

    private fun buildSpeedBar(speed: Int): String {
        val ratio = (speed.toFloat() / maxScaleKmh).coerceIn(0f, 1f)
        val filledCount = (ratio * totalBlocks).roundToInt()
        val emptyCount = totalBlocks - filledCount

        val filled = "▰".repeat(filledCount)
        val empty = "▱".repeat(emptyCount)
        return "$filled$empty"
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            carContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            permissionMissing = true
            invalidate()
            return
        }

        permissionMissing = false

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()

        if (permissionMissing) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Permissão de GPS necessária")
                    .addText("Abra o app 'Velocímetro GPS' no celular e libere a localização.")
                    .build()
            )
        } else {
            val statusSubtext = if (hasGpsFix) {
                buildSpeedBar(currentSpeedKmh)
            } else {
                "Buscando sinal de satélite..."
            }

            val tripKm = totalDistanceMeters / 1000f
            val tripFormatted = String.format(Locale("pt", "BR"), "%.2f km", tripKm)

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("$currentSpeedKmh km/h")
                    .addText(statusSubtext)
                    .build()
            )

            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Trip: $tripFormatted  |  Máx: $maxSpeedKmh km/h")
                    .build()
            )
        }

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Zerar Trip")
                    .setOnClickListener { resetTrip() }
                    .build()
            )
            .build()

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle("Velocímetro GPS")
            .setHeaderAction(Action.APP_ICON)
            .setActionStrip(actionStrip)
            .build()
    }
}
