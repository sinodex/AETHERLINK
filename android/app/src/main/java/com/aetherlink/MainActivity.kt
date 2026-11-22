package com.aetherlink

import android.Manifest
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(
            arrayOf(
                Manifest.permission.POST_NOTIFICATIONS,
                Manifest.permission.RECORD_AUDIO,
                "android.permission.BLUETOOTH_CONNECT"
            ), 0
        )

        setContent {
            MaterialTheme {
                var serviceOn by remember { mutableStateOf(false) }
                var micOn by remember { mutableStateOf(Settings.micEnabled) }
                var micSource by remember { mutableStateOf(Settings.micSource) }
                var micOptions by remember { mutableStateOf(availableMics()) }
                var menuOpen by remember { mutableStateOf(false) }
                var minBuffer by remember { mutableStateOf(Settings.minBufferMs.toFloat()) }

                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Start / stop the streaming service.
                    Text(
                        if (serviceOn) "Wartet auf PC (Port ${Protocol.PORT})" else "Gestoppt",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = {
                        val intent = Intent(this@MainActivity, ReceiverService::class.java)
                        if (serviceOn) stopService(intent) else startForegroundService(intent)
                        serviceOn = !serviceOn
                    }) { Text(if (serviceOn) "Stoppen" else "Starten") }

                    // Microphone: master switch and source.
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mikrofon erlauben")
                        Spacer(Modifier.width(12.dp))
                        Switch(checked = micOn, onCheckedChange = {
                            micOn = it
                            Settings.micEnabled = it
                        })
                    }
                    Text("Aktiv nur, wenn eine PC-App das Mikro benutzt", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Mikrofon-Quelle", style = MaterialTheme.typography.labelMedium)
                    Box {
                        OutlinedButton(onClick = { micOptions = availableMics(); menuOpen = true }) {
                            Text(micOptions.firstOrNull { it.first == micSource }?.second ?: "Handy-Mikro (eingebaut)")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            micOptions.forEach { (id, label) ->
                                DropdownMenuItem(text = { Text(label) }, onClick = {
                                    micSource = id
                                    Settings.micSource = id
                                    menuOpen = false
                                })
                            }
                        }
                    }

                    // Smallest allowed playback buffer.
                    Spacer(Modifier.height(24.dp))
                    Text("Mindest-Puffer: ${minBuffer.toInt()} ms", style = MaterialTheme.typography.labelMedium)
                    Text("kleiner = weniger Verzögerung, aber evtl. Knacken", style = MaterialTheme.typography.bodySmall)
                    Slider(value = minBuffer, valueRange = 10f..300f, onValueChange = {
                        minBuffer = it
                        Settings.minBufferMs = it.toInt()
                    })
                }
            }
        }
    }

    /** Microphones Android currently offers, as (id, label) pairs. */
    private fun availableMics(): List<Pair<Int, String>> {
        val audioManager = getSystemService(AudioManager::class.java)
        val list = mutableListOf(Settings.MIC_BUILTIN to "Handy-Mikro (eingebaut)")
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

        inputs.forEach {
            when (it.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> list += it.id to "Kabel-Headset: ${it.productName}"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> list += it.id to "USB: ${it.productName}"
            }
        }

        fun isBluetooth(type: Int) = type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        val bluetoothAvailable = inputs.any { isBluetooth(it.type) } ||
                (Build.VERSION.SDK_INT >= 31 && audioManager.availableCommunicationDevices.any { isBluetooth(it.type) })
        if (bluetoothAvailable) list += Settings.MIC_BLUETOOTH to "Bluetooth-Headset-Mikro (Ton wird schlechter)"
        return list
    }
}
