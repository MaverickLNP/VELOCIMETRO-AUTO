package com.mavericklnp.velocimetroauto

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.content.ContextCompat
import androidx.media.MediaBrowserServiceCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale
import kotlin.math.roundToInt

class SpeedMediaService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private var currentSpeedKmh: Int = 0
    private var maxSpeedKmh: Int = 0
    private var totalDistanceMeters: Float = 0f
    private var lastValidLocation: Location? = null
    private var hasGpsFix: Boolean = false

    private val maxScaleKmh = 130
    private val totalBlocks = 12

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
            updateDisplay()
        }
    }

    override fun onCreate() {
        super.onCreate()

        mediaSession = MediaSessionCompat(this, "VelocimetroAutoSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    startGps()
                    updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                }

                override fun onPause() {
                    updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                }

                override fun onSkipToNext() {
                    resetTrip()
                }

                override fun onCustomAction(action: String?, extras: Bundle?) {
                    if (action == "ACTION_RESET_TRIP") {
                        resetTrip()
                    }
                }
            })
            isActive = true
        }

        sessionToken = mediaSession.sessionToken
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        updateDisplay()
        startGps()
    }

    private fun resetTrip() {
        maxSpeedKmh = 0
        totalDistanceMeters = 0f
        lastValidLocation = null
        updateDisplay()
    }

    private fun buildSpeedBar(speed: Int): String {
        val ratio = (speed.toFloat() / maxScaleKmh).coerceIn(0f, 1f)
        val filledCount = (ratio * totalBlocks).roundToInt()
        val emptyCount = totalBlocks - filledCount
        return "▰".repeat(filledCount) + "▱".repeat(emptyCount)
    }

    private fun updatePlaybackState(state: Int) {
        val customAction = PlaybackStateCompat.CustomAction.Builder(
            "ACTION_RESET_TRIP",
            "Zerar Trip",
            android.R.drawable.ic_menu_rotate
        ).build()

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
            .addCustomAction(customAction)
            .setState(state, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)

        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun updateDisplay() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val titleText: String
        val subtitleText: String

        if (!granted) {
            titleText = "Permissão de GPS pendente"
            subtitleText = "Abra o app no celular e libere a localização"
        } else {
            val bar = if (hasGpsFix) buildSpeedBar(currentSpeedKmh) else "Buscando satélite..."
            val tripKm = totalDistanceMeters / 1000f
            val tripFormatted = String.format(Locale("pt", "BR"), "%.2f km", tripKm)

            titleText = "$currentSpeedKmh km/h   $bar"
            subtitleText = "Trip: $tripFormatted  |  Máx: $maxSpeedKmh km/h"
        }

        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, titleText)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitleText)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Velocímetro GPS")
            .build()

        mediaSession.setMetadata(metadata)
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            updateDisplay()
            return
        }

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()

        fusedLocationClient.requestLocationUpdates(
            request,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("root_velocimetro", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId("velocimetro_ao_vivo")
            .setTitle("Velocímetro GPS Ativo")
            .setSubtitle("Toque para exibir velocidade e barra")
            .build()

        val item = MediaBrowserCompat.MediaItem(
            description,
            MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
        )
        result.sendResult(mutableListOf(item))
    }

    override fun onDestroy() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mediaSession.release()
        super.onDestroy()
    }
}
