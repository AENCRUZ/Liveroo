package com.group4.liveroo.testsupport

import com.group4.liveroo.data.MergeResult
import com.group4.liveroo.rules.classifySyncScope
import com.group4.liveroo.sync.SyncOutcome
import com.group4.liveroo.sync.TransferIntent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One full-state exchange between two phones without the transport: each side exports the groups in its own
 * sync scope (D4) from before the exchange and merges what the other exported, exactly like the real handshake.
 * Returns (a's merge result, b's merge result).
 */
suspend fun exchangeState(a: TestPhone, b: TestPhone): Pair<MergeResult, MergeResult> {
    val aScope = classifySyncScope(a.repo.groupSyncViews(), b.userId, b.repo.localGroupIds()).ordinary
    val bScope = classifySyncScope(b.repo.groupSyncViews(), a.userId, a.repo.localGroupIds()).ordinary
    val fromA = a.repo.export(aScope)
    val fromB = b.repo.export(bScope)
    val resultA = a.repo.merge(fromB, a.userId, aScope.toSet())
    val resultB = b.repo.merge(fromA, b.userId, bScope.toSet())
    return resultA to resultB
}

/**
 * Runs a real leadership handover over a loopback connection: [from] (the leader) hands [groupId] to [to], who agrees.
 * Returns (initiator outcome, responder outcome).
 */
suspend fun completeTransfer(from: TestPhone, to: TestPhone, groupId: String): Pair<SyncOutcome, Any> = coroutineScope {
    val channels = connectedChannels()
    val responder = async(Dispatchers.IO) {
        to.sync.respond(channels.responderSide, allowSync = { true }, allowInvite = { false }, allowTransfer = { true })
    }
    val initiator = from.sync.initiateSync(channels.initiatorSide, TransferIntent(groupId, to.userId))
    initiator to responder.await()
}

/** Gives [phone] the leader's current copy of a group, as an accepted invitation would. */
suspend fun TestPhone.joinFrom(leader: TestPhone, groupId: String): String? {
    // importInvite refuses a group the phone already has (D9), so a refreshed copy starts from nothing.
    db.tasks().deleteForGroup(groupId)
    db.members().deleteForGroup(groupId)
    db.groups().delete(groupId)
    return repo.importInvite(leader.repo.export(listOf(groupId)))
}
