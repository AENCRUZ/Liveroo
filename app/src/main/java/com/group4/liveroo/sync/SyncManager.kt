package com.group4.liveroo.sync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import com.group4.liveroo.data.ALREADY_HAS_GROUP_MESSAGE
import com.group4.liveroo.data.CommitResult
import com.group4.liveroo.data.LiverooRepository
import com.group4.liveroo.data.TransferCommit
import com.group4.liveroo.data.deniedReason
import com.group4.liveroo.data.prefs.IdentityStore
import com.group4.liveroo.rules.DenyReason
import com.group4.liveroo.rules.GROUP_SKIPPED_MESSAGE
import com.group4.liveroo.rules.InviteDecision
import com.group4.liveroo.rules.TransferError
import com.group4.liveroo.rules.TransferVerdict
import com.group4.liveroo.rules.classifySyncScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

sealed interface SyncOutcome {
    data class Success(
        val peerName: String,
        val sent: Int,
        val received: Int,
        val notes: List<String>,
        val transferDone: Transfer? = null
    ) : SyncOutcome

    data class Failed(val reason: String, val wasTransfer: Boolean = false) : SyncOutcome
    data object Declined : SyncOutcome

    /** The person asked to become leader said no: nothing changed on either phone (D6). */
    data object TransferDeclined : SyncOutcome
}

/** Shown on the invitee's phone when the group data in an invitation failed the checks (nothing was saved). */
const val INVITE_DATA_REFUSED_MESSAGE =
    "The group data didn't look right, so nothing was saved. Ask the leader to invite you again."

sealed interface InviteOutcome {
    data class Joined(val name: String) : InviteOutcome
    data object Declined : InviteOutcome
    data class Failed(val reason: String) : InviteOutcome
}

/**
 * One-to-one sync over an open RFCOMM socket (C14). Message order:
 *   initiator: Hello -> (reads Hello) -> Data -> (reads Data) -> Ack -> (reads Ack) -> commit
 *   responder: (reads Hello) -> Hello -> (reads Data) -> Data -> (reads Ack) -> Ack -> commit
 * Nothing is merged or committed until both acks are exchanged, so a dropped connection changes nothing (C20, T11).
 * TODO(T8): automatic retries with backoff.
 */
@SuppressLint("MissingPermission")
class SyncManager(
    private val repo: LiverooRepository,
    private val identity: IdentityStore
) {
    private class Pipe(channel: SyncChannel) {
        val out = DataOutputStream(BufferedOutputStream(channel.output))
        val input = DataInputStream(BufferedInputStream(channel.input))
        fun send(m: Msg) = Wire.send(out, m)
        fun receive(): Msg = Wire.receive(input)
    }

    /**
     * I tapped a groupmate on the Sync screen. [intent] is set when this sync hands over leadership (C20): the
     * request is checked here first, then sent in my Hello so the other person is asked before any data moves.
     */
    suspend fun initiateSync(socket: BluetoothSocket, intent: TransferIntent? = null): SyncOutcome =
        initiateSync(BluetoothSyncChannel(socket), intent)

    suspend fun initiateSync(channel: SyncChannel, intent: TransferIntent? = null): SyncOutcome =
        withContext(Dispatchers.IO) {
            val isTransfer = intent != null
            try {
                channel.use { s ->
                    val (me, myName) = identity.me()
                    // A handover is judged on this phone first, so nothing is sent for one that can't work.
                    val proposal = intent?.let {
                        val epoch = repo.groupEpoch(it.groupId)
                            ?: return@use SyncOutcome.Failed(TransferError.GROUP_NOT_SHARED.message(false), true)
                        Transfer(it.groupId, it.newLeaderId, at = System.currentTimeMillis(), expectedEpoch = epoch)
                    }
                    if (proposal != null) {
                        val verdict = repo.transferVerdict(me, proposal.toProposal(), proposal.newLeaderId, consented = true)
                        if (verdict is TransferVerdict.Invalid) {
                            return@use SyncOutcome.Failed(verdict.error.message(false), true)
                        }
                    }
                    val pipe = Pipe(s)
                    pipe.send(
                        newHello(
                            me, myName, repo.localGroupIds(),
                            if (proposal != null) PURPOSE_TRANSFER else "sync", transfer = proposal
                        )
                    )
                    val peer = pipe.receive() as? Hello
                        ?: return@use SyncOutcome.Failed("Unexpected reply", isTransfer)
                    // Different app versions never exchange data: stop here, before anything is sent or saved.
                    if (peer.isVersionMismatch()) return@use SyncOutcome.Failed(VERSION_MISMATCH_MESSAGE, isTransfer)
                    if (peer.purpose == "declined") {
                        return@use if (isTransfer) SyncOutcome.TransferDeclined else SyncOutcome.Declined
                    }
                    if (peer.purpose == PURPOSE_TRANSFER_INVALID) {
                        return@use SyncOutcome.Failed(refusalMessage(peer.reason), true)
                    }
                    repo.rememberDevice(s.remoteAddress, peer.userId, peer.displayName)
                    if (proposal != null) {
                        // D7: the person I chose must be the one who answered, and must have agreed.
                        if (peer.userId != proposal.newLeaderId) {
                            return@use SyncOutcome.Failed(TransferError.RESPONDER_NOT_NEW_LEADER.message(false), true)
                        }
                        if (peer.purpose != PURPOSE_TRANSFER_ACCEPTED) return@use SyncOutcome.Failed("Unexpected reply", true)
                    }
                    exchange(pipe, me, peer, proposal, initiator = true)
                }
            } catch (e: IOException) {
                SyncOutcome.Failed("Connection lost", isTransfer)
            } catch (e: IllegalArgumentException) {
                SyncOutcome.Failed("Unreadable data", isTransfer)
            }
        }

    /**
     * A groupmate connected to me. [allow] shows "Coleen wants to sync. Allow?" (or the invite dialog)
     * and returns the user's answer.
     */
    suspend fun respond(
        socket: BluetoothSocket,
        allowSync: suspend (peerName: String) -> Boolean,
        allowInvite: suspend (offer: InviteOffer) -> Boolean,
        allowTransfer: suspend (offer: TransferOffer) -> Boolean = { false }
    ): Any = respond(BluetoothSyncChannel(socket), allowSync, allowInvite, allowTransfer)

    /**
     * [allowTransfer] asks the person whether they will become leader (D6); it is called only after every other
     * check on the request passed, so a phone the handover wasn't meant for never sees the question (D7).
     */
    suspend fun respond(
        channel: SyncChannel,
        allowSync: suspend (peerName: String) -> Boolean,
        allowInvite: suspend (offer: InviteOffer) -> Boolean,
        allowTransfer: suspend (offer: TransferOffer) -> Boolean = { false }
    ): Any = withContext(Dispatchers.IO) {
        try {
            channel.use { s ->
                val pipe = Pipe(s)
                val (me, myName) = identity.me()
                val peer = pipe.receive() as? Hello ?: return@use SyncOutcome.Failed("Unexpected message")
                if (peer.isVersionMismatch()) {
                    // Checked first: no prompt for the user, nothing remembered, no data read or written.
                    pipe.send(newHello(me, myName, emptyList(), PURPOSE_VERSION_MISMATCH))
                    return@use if (peer.purpose == "invite") InviteOutcome.Failed(VERSION_MISMATCH_MESSAGE)
                    else SyncOutcome.Failed(VERSION_MISMATCH_MESSAGE)
                }
                repo.rememberDevice(s.remoteAddress, peer.userId, peer.displayName)
                when (peer.purpose) {
                    "invite" -> {
                        pipe.send(newHello(me, myName, emptyList(), "invite"))
                        val offer = pipe.receive() as? InviteOffer ?: return@use InviteOutcome.Failed("Unexpected message")
                        // D9 / N2, decided before the person is asked: refuse if I already have this group, except
                        // that an invitation from its current leader to a member she hasn't recorded just completes it.
                        val decision = repo.inviteDecision(offer.groupId, inviterId = peer.userId)
                        if (decision == InviteDecision.REJECT_ALREADY_HAS_GROUP) {
                            pipe.send(InviteAnswer(false, INVITE_REASON_ALREADY_HAS_GROUP))
                            return@use InviteOutcome.Failed(ALREADY_HAS_GROUP_MESSAGE)
                        }
                        val completion = decision == InviteDecision.COMPLETE_EXISTING
                        val yes = completion || allowInvite(offer)
                        pipe.send(InviteAnswer(yes))
                        if (!yes) return@use InviteOutcome.Declined
                        val data = pipe.receive() as? DataMsg ?: return@use InviteOutcome.Failed("No group data")
                        // Check the payload as a whole before agreeing to it; nothing is stored unless it makes sense.
                        val problem = if (data.groups.singleOrNull()?.id != offer.groupId) {
                            "The group data didn't match the invitation."
                        } else repo.invitePayloadProblem(data)
                        if (problem != null) {
                            pipe.send(Ack(false, 0, problem))
                            return@use InviteOutcome.Failed(INVITE_DATA_REFUSED_MESSAGE)
                        }
                        // D8: I commit first; the ack means "stored". The leader adds me only after it arrives.
                        // A completion stores nothing: I already have the group and must not lose local changes.
                        if (!completion) {
                            val refused = repo.importInvite(data)
                            if (refused != null) {
                                pipe.send(Ack(false, 0, refused))
                                return@use InviteOutcome.Failed(INVITE_DATA_REFUSED_MESSAGE)
                            }
                        }
                        pipe.send(Ack(true, data.tasks.size))
                        InviteOutcome.Joined(offer.groupName)
                    }
                    PURPOSE_TRANSFER -> {
                        val proposal = peer.transfer
                        if (proposal == null) {
                            pipe.send(newHello(me, myName, emptyList(), PURPOSE_TRANSFER_INVALID, reason = TransferError.GROUP_NOT_SHARED.name))
                            return@use SyncOutcome.Failed("Unexpected message", true)
                        }
                        // Everything except my consent is checked first: nobody is asked about a handover that can't work.
                        val verdict = repo.transferVerdict(peer.userId, proposal.toProposal(), responderId = me, consented = true)
                        if (verdict is TransferVerdict.Invalid) {
                            pipe.send(newHello(me, myName, emptyList(), PURPOSE_TRANSFER_INVALID, reason = verdict.error.name))
                            return@use SyncOutcome.Failed(verdict.error.message(true), true)
                        }
                        val groupName = repo.groupName(proposal.groupId) ?: "the group"
                        if (!allowTransfer(TransferOffer(peer.displayName, proposal.groupId, groupName))) {
                            pipe.send(newHello(me, myName, emptyList(), "declined"))
                            return@use SyncOutcome.TransferDeclined
                        }
                        pipe.send(newHello(me, myName, repo.localGroupIds(), PURPOSE_TRANSFER_ACCEPTED))
                        exchange(pipe, me, peer, proposal, initiator = false)
                    }
                    else -> {
                        if (!allowSync(peer.displayName)) {
                            pipe.send(newHello(me, myName, emptyList(), "declined"))
                            return@use SyncOutcome.Declined
                        }
                        pipe.send(newHello(me, myName, repo.localGroupIds(), "sync"))
                        exchange(pipe, me, peer, transfer = null, initiator = false)
                    }
                }
            }
        } catch (e: IOException) {
            SyncOutcome.Failed("Connection lost")
        } catch (e: IllegalArgumentException) {
            SyncOutcome.Failed("Unreadable data")
        }
    }

    private suspend fun exchange(pipe: Pipe, me: String, peer: Hello, transfer: Transfer?, initiator: Boolean): SyncOutcome {
        // D4: only groups where the peer is an active member in my own data take part in the exchange. A group where
        // the peer has already left (in my data) takes part only as a departure confirmation, with no group data (N1);
        // any other group is skipped, and the ack tells the peer so (N9).
        val scope = classifySyncScope(repo.groupSyncViews(), peer.userId, peer.groupIds)
        val shared = scope.ordinary
        val snapshotAt = System.currentTimeMillis()
        val outgoing = repo.export(shared)

        val incoming: DataMsg
        if (initiator) {
            pipe.send(outgoing)
            incoming = pipe.receive() as? DataMsg ?: return SyncOutcome.Failed("Unexpected message", transfer != null)
        } else {
            incoming = pipe.receive() as? DataMsg ?: return SyncOutcome.Failed("Unexpected message")
            pipe.send(outgoing)
        }

        // A handover must be about a group that takes part in this sync (it was already agreed in the Hellos).
        val transferOk = transfer == null || transfer.groupId in shared

        val myAck = Ack(
            transferOk, incoming.tasks.size, if (transferOk) null else "Transfer not valid",
            accepted = shared, departureConfirmed = scope.departureOnly, skipped = scope.skipped
        )
        val theirAck: Ack?
        if (initiator) {
            pipe.send(myAck)
            theirAck = pipe.receive() as? Ack
        } else {
            theirAck = pipe.receive() as? Ack
            pipe.send(myAck)
        }
        if (!transferOk || theirAck?.ok != true) {
            return SyncOutcome.Failed("Sync didn't finish. Nothing was changed.", transfer != null)
        }

        // Both phones confirmed: now apply everything in one step. Only groups the other phone also merged count as
        // synced: "Not yet synced" and a pending departure are cleared for those alone. A handover is checked once
        // more against the merged data; if it fails, nothing at all is applied.
        val confirmed = shared.filter { it in theirAck!!.accepted }
        val commit = repo.commitSync(
            incoming, me, shared.toSet(), confirmed, snapshotAt,
            transfer?.let {
                TransferCommit(
                    it.toProposal(),
                    senderId = if (initiator) me else peer.userId,
                    responderId = if (initiator) peer.userId else me
                )
            }
        )
        val merge = when (commit) {
            is CommitResult.TransferRefused -> return SyncOutcome.Failed(commit.error.message(!initiator), true)
            is CommitResult.Done -> commit.merge
        }
        val notes = merge.notes.toMutableList()
        if (transfer != null) {
            val groupName = repo.groupName(transfer.groupId) ?: "the group"
            notes.add(
                0,
                if (transfer.newLeaderId == me) "You're now the leader of $groupName."
                else "${peer.displayName} is now the leader of $groupName."
            )
        }
        // N9: groups the other phone skipped because it doesn't recognise me as an active member.
        repo.groupNames(theirAck!!.skipped).values.sorted().forEach { notes += "$it: $GROUP_SKIPPED_MESSAGE" }
        // N1: a pending departure of mine is complete when the peer merged it, or had already recorded it.
        val departed = shared.filter { it in theirAck.accepted || it in theirAck.departureConfirmed }
        repo.finishDepartures(departed).forEach {
            notes += "Your departure from $it has synced. The group was removed from this phone."
        }
        val sentCount = outgoing.groups.size + outgoing.members.size + outgoing.tasks.size
        return SyncOutcome.Success(peer.displayName, sentCount, merge.changed, notes, transfer)
    }

    private fun inviteDeniedMessage(reason: DenyReason): String = when (reason) {
        DenyReason.GROUP_NOT_FOUND -> "Group not found"
        DenyReason.GROUP_NOT_WRITABLE -> "You left this group, so you can't invite people to it."
        else -> "Only the group leader can invite members."
    }

    /** Leader side of an invite (C4, C17). */
    suspend fun initiateInvite(socket: BluetoothSocket, groupId: String): InviteOutcome =
        initiateInvite(BluetoothSyncChannel(socket), groupId)

    suspend fun initiateInvite(channel: SyncChannel, groupId: String): InviteOutcome =
        withContext(Dispatchers.IO) {
            try {
                channel.use { s ->
                    // Only the group's leader may invite: refuse before anything is sent to the other phone.
                    repo.inviteDenial(groupId)?.let { return@use InviteOutcome.Failed(inviteDeniedMessage(it)) }
                    val pipe = Pipe(s)
                    val (me, myName) = identity.me()
                    pipe.send(newHello(me, myName, emptyList(), "invite"))
                    val peer = pipe.receive() as? Hello ?: return@use InviteOutcome.Failed("That phone isn't running Liveroo.")
                    if (peer.isVersionMismatch()) return@use InviteOutcome.Failed(VERSION_MISMATCH_MESSAGE)
                    repo.rememberDevice(s.remoteAddress, peer.userId, peer.displayName)
                    val groupName = repo.groupName(groupId) ?: return@use InviteOutcome.Failed("Group not found")
                    pipe.send(InviteOffer(groupId, groupName, myName))
                    val answer = pipe.receive() as? InviteAnswer ?: return@use InviteOutcome.Failed("No answer")
                    if (!answer.accept) {
                        return@use if (answer.reason == INVITE_REASON_ALREADY_HAS_GROUP) {
                            InviteOutcome.Failed("${peer.displayName} already has this group.")
                        } else InviteOutcome.Declined
                    }
                    // D8: send the group with the invitee's member row built in memory, and write that row myself
                    // only after they confirm they stored the group. Nothing is committed here before that.
                    val joinedAt = System.currentTimeMillis()
                    pipe.send(repo.invitePayload(groupId, peer.userId, peer.displayName, joinedAt))
                    val ack = pipe.receive() as? Ack
                    when {
                        ack == null -> InviteOutcome.Failed("Connection lost")
                        !ack.ok -> InviteOutcome.Failed("That phone couldn't accept the group data.")
                        else -> {
                            repo.addMember(groupId, peer.userId, peer.displayName, at = joinedAt).deniedReason()
                                ?.let { return@use InviteOutcome.Failed(inviteDeniedMessage(it)) }
                            InviteOutcome.Joined(peer.displayName)
                        }
                    }
                }
            } catch (e: IOException) {
                InviteOutcome.Failed("Couldn't reach that phone. Stay within about 10 m and keep Liveroo open on both phones.")
            }
        }
}
