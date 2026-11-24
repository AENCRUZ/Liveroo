package com.group4.liveroo.sync

import com.group4.liveroo.data.local.TaskStatus
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.Ver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

/** The wire format (protocol v2) round-trips, and pre-v2 data is recognised and refused. */
class WireTest {

    private fun roundTrip(msg: Msg): Msg {
        val buffer = ByteArrayOutputStream()
        Wire.send(DataOutputStream(buffer), msg)
        return Wire.receive(DataInputStream(ByteArrayInputStream(buffer.toByteArray())))
    }

    private fun decode(json: String): Msg = Wire.json.decodeFromString(Msg.serializer(), json)

    @Test
    fun hello_roundTrips_andCarriesCurrentVersion() {
        val msg = newHello("u1", "Alice", listOf("g1", "g2"), "sync")
        assertEquals(PROTOCOL_VERSION, msg.v)
        assertEquals(msg, roundTrip(msg))
        assertFalse(msg.isVersionMismatch())
    }

    @Test
    fun hello_carriesAHandoverRequest_andARefusalReason() {
        val request = Transfer(groupId = "g1", newLeaderId = "u2", at = 1_234, expectedEpoch = 3)
        val ask = newHello("u1", "Alice", listOf("g1"), PURPOSE_TRANSFER, transfer = request)
        assertEquals(ask, roundTrip(ask))
        assertEquals(request, (roundTrip(ask) as Hello).transfer)

        val refusal = newHello("u2", "Bob", emptyList(), PURPOSE_TRANSFER_INVALID, reason = "STALE_EPOCH")
        assertEquals("STALE_EPOCH", (roundTrip(refusal) as Hello).reason)
        assertEquals(null, (roundTrip(newHello("u", "A", emptyList(), "sync")) as Hello).transfer)
    }

    @Test
    fun inviteAnswer_carriesAnOptionalRefusalReason() {
        assertEquals(null, (roundTrip(InviteAnswer(true)) as InviteAnswer).reason)
        val refusal = InviteAnswer(false, INVITE_REASON_ALREADY_HAS_GROUP)
        assertEquals(refusal, roundTrip(refusal))
    }

    @Test
    fun dataMsg_carriesNoHandoverRequest_anymore() {
        val json = Wire.json.encodeToString(Msg.serializer(), DataMsg(emptyList(), emptyList(), emptyList()))
        assertTrue(json, !json.contains("transfer"))
    }

    @Test
    fun helloWithoutVersion_decodesAsZero_andIsAMismatch() {
        val old = decode("""{"type":"hello","userId":"u","displayName":"A","groupIds":[],"purpose":"sync"}""") as Hello
        assertEquals(0, old.v)
        assertTrue(old.isVersionMismatch())
    }

    @Test
    fun helloFromFutureVersion_orVersionMismatchPurpose_isAMismatch() {
        assertTrue(Hello("u", "A", emptyList(), "sync", v = PROTOCOL_VERSION + 1).isVersionMismatch())
        assertTrue(newHello("u", "A", emptyList(), PURPOSE_VERSION_MISMATCH).isVersionMismatch())
    }

    @Test
    fun dataMsg_roundTrips_withChainJoinTimeAndRegisters() {
        val ver = Ver(epoch = 1, ts = 3, by = "u1")
        val msg = DataMsg(
            groups = listOf(
                GroupDto(
                    "g1", "Study", createdAt = 1, updatedAt = 2,
                    leaderChain = listOf(LeaderEntry("u1", 1), LeaderEntry("u2", 2))
                )
            ),
            members = listOf(MemberDto("g1", "u1", "Alice", joinedAt = 1, leftAt = null, updatedAt = 2)),
            tasks = listOf(
                TaskDto("t1", "g1", TaskRegisters.create("Read", "ch. 1", "u2", TaskStatus.TODO, ver)
                    .withStatus(TaskStatus.IN_PROGRESS, Ver(1, 4, "u2")))
            )
        )
        val back = roundTrip(msg) as DataMsg
        assertEquals(msg, back)
        assertEquals("u2", back.groups.single().leaderId)
        assertEquals(4L, back.tasks.single().updatedAt)
    }

    @Test
    fun ackAndInviteMessages_roundTrip() {
        listOf(Ack(true, 3, null), Ack(false, 0, "nope"), InviteOffer("g1", "Study", "Alice"), InviteAnswer(true))
            .forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun receive_rejectsBadSizes() {
        fun framed(size: Int) = DataInputStream(ByteArrayInputStream(ByteArrayOutputStream().also { DataOutputStream(it).writeInt(size) }.toByteArray()))
        assertThrows(IllegalArgumentException::class.java) { Wire.receive(framed(0)) }
        assertThrows(IllegalArgumentException::class.java) { Wire.receive(framed(5_000_001)) }
    }

    @Test
    fun preV2Task_withFlatFieldsAndNoRegisters_isRejected() {
        val old = """{"type":"data","groups":[],"members":[],"tasks":[
            {"id":"t1","groupId":"g1","title":"Read","description":"","status":"TODO","updatedAt":1,"updatedBy":"u1","deleted":false}]}"""
        assertThrows(IllegalArgumentException::class.java) { decode(old) }
    }

    @Test
    fun groupWithEmptyLeaderChain_isRejected() {
        val bad = """{"type":"data","groups":[{"id":"g1","name":"S","createdAt":1,"updatedAt":1,"leaderChain":[]}],"members":[],"tasks":[]}"""
        assertThrows(IllegalArgumentException::class.java) { decode(bad) }
    }
}
