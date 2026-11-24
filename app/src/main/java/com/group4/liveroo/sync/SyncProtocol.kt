package com.group4.liveroo.sync

import com.group4.liveroo.data.local.GroupEntity
import com.group4.liveroo.data.local.MemberEntity
import com.group4.liveroo.data.local.TaskEntity
import com.group4.liveroo.data.model.LeaderEntry
import com.group4.liveroo.data.model.ModelJson
import com.group4.liveroo.data.model.TaskRegisters
import com.group4.liveroo.data.model.chain
import com.group4.liveroo.data.model.regs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Wire protocol version. Version 2 is the first with leadership chains, task registers and member join times.
 * Phones on different versions never exchange data (N12): the handshake stops at Hello.
 * A Hello with no `v` (every pre-v2 build) decodes as 0 and is treated as a mismatch.
 */
const val PROTOCOL_VERSION = 2

/** Shown on both phones when the app versions differ. */
const val VERSION_MISMATCH_MESSAGE = "Update Liveroo on both phones."

/** Hello purpose sent by a responder that refuses to continue because the versions differ. */
const val PURPOSE_VERSION_MISMATCH = "version_mismatch"

// ---------- Data sent over Bluetooth (small JSON payloads, about 10 KB) ----------

/** [leaderChain] is oldest first and never empty; the current leader is its last entry. */
@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val leaderChain: List<LeaderEntry>
) {
    init {
        require(leaderChain.isNotEmpty()) { "Group $id has no leadership chain" }
    }

    val leaderId: String get() = leaderChain.last().id
}

@Serializable
data class MemberDto(
    val groupId: String, val userId: String, val displayName: String,
    val joinedAt: Long, val leftAt: Long? = null, val updatedAt: Long
)

/** A task travels as its registers; the flat values are derived from them on arrival. */
@Serializable
data class TaskDto(val id: String, val groupId: String, val registers: TaskRegisters) {
    /** Time of the most recent change, used by the current last-write-wins merge. */
    val updatedAt: Long get() = registers.lastChange().ts
}

/**
 * A leadership handover request, carried in the initiator's Hello (docs/MERGE_SPEC.md 2.3). [at] is the initiating
 * phone's time and becomes the timestamp of the new chain entry on both phones; [expectedEpoch] is the epoch the
 * initiator believes the group is at, so a stale view is refused.
 */
@Serializable
data class Transfer(val groupId: String, val newLeaderId: String, val at: Long, val expectedEpoch: Int)

/** Every message is one of these. The "type" field in the JSON says which. */
@Serializable
sealed interface Msg

/**
 * First message each way. purpose = "sync" or "invite" ("declined" / "version_mismatch" from a responder).
 * [v] is 0 when absent. Build messages with [newHello] so the current version is always stamped.
 */
@Serializable @SerialName("hello")
data class Hello(
    val userId: String, val displayName: String, val groupIds: List<String>, val purpose: String,
    val v: Int = 0,
    /** Set by an initiator whose purpose is "transfer". */
    val transfer: Transfer? = null,
    /** Why a responder refused ("transfer_invalid"): the name of a TransferError. */
    val reason: String? = null
) : Msg

fun newHello(
    userId: String, displayName: String, groupIds: List<String>, purpose: String,
    transfer: Transfer? = null, reason: String? = null
) = Hello(userId, displayName, groupIds, purpose, v = PROTOCOL_VERSION, transfer = transfer, reason = reason)

/** True when the other phone runs a different protocol version or says it can't continue. */
fun Hello.isVersionMismatch(): Boolean = v != PROTOCOL_VERSION || purpose == PURPOSE_VERSION_MISMATCH

@Serializable @SerialName("data")
data class DataMsg(
    val groups: List<GroupDto>,
    val members: List<MemberDto>,
    val tasks: List<TaskDto>
) : Msg

@Serializable @SerialName("invite")
data class InviteOffer(val groupId: String, val groupName: String, val leaderName: String) : Msg

/** Reason an invitee gives when it refuses before asking the person: it already has the group (D9). */
const val INVITE_REASON_ALREADY_HAS_GROUP = "already_has_group"

@Serializable @SerialName("invite_answer")
data class InviteAnswer(val accept: Boolean, val reason: String? = null) : Msg

/**
 * Sent after a phone has received everything. Changes are committed only after both acks (Risk 3, T11).
 * [accepted] lists the groups this phone merged (its sync scope), so the other phone only clears "Not yet synced"
 * for groups that really arrived. Empty for invites.
 */
@Serializable @SerialName("ack")
data class Ack(
    val ok: Boolean, val received: Int = 0, val reason: String? = null,
    val accepted: List<String> = emptyList(),
    /** Groups where I had already recorded the other phone's departure: confirms it without any group data (N1). */
    val departureConfirmed: List<String> = emptyList(),
    /** Groups we both hold that I skipped because I don't recognise the other phone as an active member (N9). */
    val skipped: List<String> = emptyList()
) : Msg

// ---------- Framing: 4-byte length, then UTF-8 JSON ----------

object Wire {
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        encodeDefaults = true
    }

    fun send(out: DataOutputStream, msg: Msg) {
        val bytes = json.encodeToString(Msg.serializer(), msg).toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    fun receive(input: DataInputStream): Msg {
        val size = input.readInt()
        require(size in 1..5_000_000) { "Bad message size $size" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return json.decodeFromString(Msg.serializer(), String(bytes, Charsets.UTF_8))
    }
}

// ---------- Mapping between Room rows and messages ----------

fun GroupEntity.toDto() = GroupDto(id, name, createdAt, updatedAt, chain())
fun MemberEntity.toDto() = MemberDto(groupId, userId, displayName, joinedAt, leftAt, updatedAt)
fun TaskEntity.toDto() = TaskDto(id, groupId, regs())

fun GroupDto.toEntity(leftByMe: Boolean = false) =
    GroupEntity(
        id, name, leaderId, ModelJson.encodeChain(leaderChain), createdAt, updatedAt,
        dirty = false, leftByMe = leftByMe
    )

fun MemberDto.toEntity() = MemberEntity(groupId, userId, displayName, joinedAt, leftAt, updatedAt, dirty = false)
