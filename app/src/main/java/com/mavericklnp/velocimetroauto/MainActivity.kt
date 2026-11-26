package com.mavericklnp.velocimetroauto

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            updateStatus(isGranted)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }

        statusText = TextView(this).apply {
            textSize = 18f
        }

        val btnPermissao = Button(this).apply {
            text = "Conceder Permissão de GPS"
            setOnClickListener {
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        layout.addView(statusText)
        layout.addView(btnPermissao)
        setContentView(layout)

        checkPermission()
    }

    private fun checkPermission() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        updateStatus(granted)
    }

    private fun updateStatus(granted: Boolean) {
        statusText.text = if (granted) {
            "GPS liberado! Conecte o celular na multimídia e abra 'Velocímetro GPS' no Android Auto."
        } else {
            "Permissão de GPS pendente. Toque no botão abaixo para liberar antes de usar na moto."
        }
    }
}
