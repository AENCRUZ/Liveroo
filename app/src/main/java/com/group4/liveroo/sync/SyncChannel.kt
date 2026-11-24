package com.group4.liveroo.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream

/**
 * A connection to one other phone, as far as [SyncManager] cares. Production uses [BluetoothSyncChannel];
 * tests use in-memory or loopback streams so the whole handshake can run without Bluetooth hardware.
 */
interface SyncChannel : Closeable {
    val input: InputStream
    val output: OutputStream

    /** Stable identifier of the other device (its Bluetooth address), used to remember known devices. */
    val remoteAddress: String
}

/** Wraps an open RFCOMM socket. Callers have already checked BtPermissions. */
@SuppressLint("MissingPermission")
class BluetoothSyncChannel(private val socket: BluetoothSocket) : SyncChannel {
    override val input: InputStream get() = socket.inputStream
    override val output: OutputStream get() = socket.outputStream
    override val remoteAddress: String get() = socket.remoteDevice.address
    override fun close() = socket.close()
}
