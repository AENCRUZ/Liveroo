package com.group4.liveroo.testsupport

import com.group4.liveroo.sync.SyncChannel
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/** One end of a loopback TCP connection, standing in for a Bluetooth RFCOMM socket. */
class LoopbackChannel(private val socket: Socket, override val remoteAddress: String) : SyncChannel {
    override val input: InputStream get() = socket.getInputStream()
    override val output: OutputStream get() = socket.getOutputStream()
    override fun close() = socket.close()
}

/**
 * Wraps a channel so a test can look at the world at the exact moment a message is about to leave this phone:
 * [beforeWrite] receives the bytes of each outgoing write (length prefix + JSON) before they are sent.
 */
class SpyChannel(private val inner: SyncChannel, private val beforeWrite: (ByteArray) -> Unit) : SyncChannel {
    override val input: InputStream get() = inner.input
    override val remoteAddress: String get() = inner.remoteAddress
    override val output: OutputStream = object : OutputStream() {
        override fun write(b: Int) = inner.output.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) {
            beforeWrite(b.copyOfRange(off, off + len))
            inner.output.write(b, off, len)
        }
        override fun flush() = inner.output.flush()
    }
    override fun close() = inner.close()
}

/** True if these outgoing bytes are a wire message of the given type (for example "ack"). */
fun ByteArray.isWireMessage(type: String): Boolean = String(this, Charsets.UTF_8).contains("\"type\":\"$type\"")

/**
 * Two connected channels. `initiatorSide` is the end the initiating phone holds (its remote is [responderAddress]);
 * `responderSide` is the end the responding phone holds (its remote is [initiatorAddress]).
 * Real sockets are used instead of piped streams because piped streams fail when the writing thread ends.
 */
class ChannelPair(val initiatorSide: SyncChannel, val responderSide: SyncChannel)

fun connectedChannels(initiatorAddress: String = "AA:AA", responderAddress: String = "BB:BB"): ChannelPair {
    val loopback = InetAddress.getLoopbackAddress()
    ServerSocket(0, 1, loopback).use { server ->
        val client = Socket(loopback, server.localPort)
        val accepted = server.accept()
        return ChannelPair(
            initiatorSide = LoopbackChannel(client, remoteAddress = responderAddress),
            responderSide = LoopbackChannel(accepted, remoteAddress = initiatorAddress)
        )
    }
}
