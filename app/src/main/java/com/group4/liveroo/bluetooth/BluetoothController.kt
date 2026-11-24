package com.group4.liveroo.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

data class FoundDevice(val address: String, val bluetoothName: String)

/** Runtime permissions (T2). Android 12+ shows these as one "Nearby devices" prompt. */
object BtPermissions {
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun granted(context: Context): Boolean = required.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/**
 * Classic Bluetooth (RFCOMM), decided in C22.
 * - Visibility: the UI launches BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE (see ui/screens/BluetoothUi.kt).
 * - Discovery: startDiscovery() fills [found].
 * - Server side: acceptOne() waits for a groupmate to connect.
 * - Client side: connect(address).
 */
@SuppressLint("MissingPermission") // every public call checks BtPermissions first
class BluetoothController(private val context: Context) {

    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /** This phone's Bluetooth name, shown on the Join screen ("Your phone appears as …"). */
    val myBluetoothName: String
        get() = if (BtPermissions.granted(context)) adapter?.name ?: "This phone" else "This phone"

    private val _found = MutableStateFlow<List<FoundDevice>>(emptyList())
    val found: StateFlow<List<FoundDevice>> = _found.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    @Volatile private var serverSocket: BluetoothServerSocket? = null

    fun startDiscovery() {
        val a = adapter ?: return
        if (!BtPermissions.granted(context)) return
        stopDiscovery()
        _found.value = emptyList()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        val device: BluetoothDevice? =
                            if (Build.VERSION.SDK_INT >= 33) {
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                            }
                        if (device != null && _found.value.none { it.address == device.address }) {
                            _found.value = _found.value + FoundDevice(device.address, device.name ?: device.address)
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _scanning.value = false
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
        _scanning.value = a.startDiscovery()
    }

    fun stopDiscovery() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
        if (BtPermissions.granted(context)) adapter?.cancelDiscovery()
        _scanning.value = false
    }

    /** Waits for one incoming connection (Join, Sync). Blocks until a groupmate connects or [timeoutMs] passes. */
    suspend fun acceptOne(timeoutMs: Int = 120_000): BluetoothSocket = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bluetooth not supported")
        // TODO(T15): switch to listenUsingInsecureRfcommWithServiceRecord to skip the pairing-code dialog, if the team prefers.
        val server = a.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
        serverSocket = server
        try {
            server.accept(timeoutMs)
        } finally {
            runCatching { server.close() }
            serverSocket = null
        }
    }

    /** Stops acceptOne() early (screen closed, Cancel tapped). */
    fun cancelListening() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    /** Connects to a groupmate. Android may show its pairing-code dialog the first time (T15). */
    suspend fun connect(address: String): BluetoothSocket = withContext(Dispatchers.IO) {
        val a = adapter ?: throw IOException("Bluetooth not supported")
        a.cancelDiscovery()
        val socket = a.getRemoteDevice(address).createRfcommSocketToServiceRecord(APP_UUID)
        socket.connect()
        socket
    }

    companion object {
        const val SERVICE_NAME = "Liveroo"
        /** Every Liveroo phone uses this ID. Keep it the same on all builds. */
        val APP_UUID: UUID = UUID.fromString("6f1c2a3e-8b4d-4e0a-9c11-2f7a5b9d4e21")
        const val VISIBLE_SECONDS = 120
    }
}
