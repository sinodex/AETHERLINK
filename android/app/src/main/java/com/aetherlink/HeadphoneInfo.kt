package com.aetherlink

import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** Reads what Android knows about the connected Bluetooth headphones (name, MAC, battery, profiles). */
@Suppress("MissingPermission")
class HeadphoneInfo(private val context: Context) {
    private var a2dp: BluetoothProfile? = null
    private var receiverRegistered = false
    private val batteryLevels = ConcurrentHashMap<String, Int>()

    // Headphones report their battery through this broadcast; there is no public API for it.
    private val batteryReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, intent: Intent) {
            try {
                val device = if (Build.VERSION.SDK_INT >= 33)
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
                if (device != null && level in 0..100) batteryLevels[device.address] = level
            } catch (_: Exception) { }
        }
    }

    private fun hasPermission() = Build.VERSION.SDK_INT < 31 ||
            context.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasPermission()) return

        if (!receiverRegistered) {
            val filter = IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(batteryReceiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(batteryReceiver, filter)
            receiverRegistered = true
        }

        // The A2DP profile proxy lists the headphones that are currently connected for audio.
        if (a2dp == null) {
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { a2dp = proxy }
                override fun onServiceDisconnected(profile: Int) { a2dp = null }
            }, BluetoothProfile.A2DP)
        }
    }

    fun stop() {
        try { if (receiverRegistered) context.unregisterReceiver(batteryReceiver) } catch (_: Exception) { }
        receiverRegistered = false
        try {
            a2dp?.let { context.getSystemService(BluetoothManager::class.java)?.adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) }
        } catch (_: Exception) { }
        a2dp = null
    }

    /** JSON for the PC, or null when no headphones are connected (or the permission is missing). */
    fun toJson(): JSONObject? {
        if (!hasPermission()) return null
        val device = try { a2dp?.connectedDevices?.firstOrNull() } catch (_: Exception) { null } ?: return null
        return try {
            val name = device.name ?: "?"
            JSONObject()
                .put("name", name)
                .put("alias", if (Build.VERSION.SDK_INT >= 30) (device.alias ?: name) else name)
                .put("address", device.address)
                .put("brand", guessBrand(name))
                .put("battery", batteryOf(device))
                .put("class", className(device.bluetoothClass))
                .put("bond", if (device.bondState == BluetoothDevice.BOND_BONDED) "gekoppelt" else "nicht gekoppelt")
                .put("type", when (device.type) { 1 -> "Classic"; 2 -> "LE"; 3 -> "Dual"; else -> "?" })
                .put("profiles", profilesOf(device))
        } catch (_: Exception) { null }
    }

    /** Battery from the broadcast if one arrived, otherwise from a hidden method that only some devices support. */
    private fun batteryOf(device: BluetoothDevice): Int {
        batteryLevels[device.address]?.let { return it }
        return try {
            val level = device.javaClass.getMethod("getBatteryLevel").invoke(device) as Int
            if (level in 0..100) level else -1
        } catch (_: Throwable) { -1 }
    }

    /** Headphones do not report their manufacturer, so it is guessed from the device name. */
    private fun guessBrand(name: String): String {
        val lower = name.lowercase()
        val brands = listOf(
            "airpods" to "Apple", "beats" to "Beats (Apple)", "galaxy buds" to "Samsung",
            "wh-" to "Sony", "wf-" to "Sony", "linkbuds" to "Sony", "sony" to "Sony",
            "bose" to "Bose", "jbl" to "JBL (Harman)", "sennheiser" to "Sennheiser", "momentum" to "Sennheiser",
            "jabra" to "Jabra", "soundcore" to "Anker (Soundcore)", "liberty" to "Anker (Soundcore)", "anker" to "Anker",
            "freebuds" to "Huawei", "huawei" to "Huawei", "redmi" to "Xiaomi", "xiaomi" to "Xiaomi",
            "pixel buds" to "Google", "oneplus" to "OnePlus", "nothing" to "Nothing", "marshall" to "Marshall",
            "skullcandy" to "Skullcandy", "jlab" to "JLab", "tozo" to "TOZO", "soundpeats" to "SoundPEATS",
            "qcy" to "QCY", "1more" to "1MORE", "haylou" to "Haylou", "tribit" to "Tribit", "mpow" to "Mpow",
            "edifier" to "Edifier", "oppo" to "OPPO", "realme" to "realme", "beyerdynamic" to "beyerdynamic",
            "akg" to "AKG (Harman)", "philips" to "Philips", "panasonic" to "Panasonic"
        )
        return brands.firstOrNull { lower.contains(it.first) }?.second ?: "unbekannt"
    }

    private fun className(bluetoothClass: BluetoothClass?): String {
        val deviceClass = bluetoothClass?.deviceClass ?: return "?"
        return when (deviceClass) {
            BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Kopfhörer"
            BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Headset"
            BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Freisprecheinrichtung"
            BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Lautsprecher"
            BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "Tragbares Audio"
            else -> "Klasse 0x" + Integer.toHexString(deviceClass)
        }
    }

    /** Supported audio profiles, taken from the 16-bit service UUIDs the device advertises. */
    private fun profilesOf(device: BluetoothDevice): String {
        val names = mapOf("110B" to "A2DP", "110D" to "A2DP", "110E" to "AVRCP", "110C" to "AVRCP",
            "111E" to "HFP", "1108" to "HSP", "184E" to "LE Audio", "1850" to "LE Audio")
        return try {
            device.uuids.orEmpty()
                .mapNotNull { names[it.uuid.toString().substring(4, 8).uppercase()] }
                .distinct().joinToString(", ").ifEmpty { "?" }
        } catch (_: Exception) { "?" }
    }
}
