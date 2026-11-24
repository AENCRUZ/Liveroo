package com.group4.liveroo.sync

import com.group4.liveroo.rules.TransferError
import com.group4.liveroo.rules.TransferProposal

/** What the person chose on the Transfer screen: hand [groupId] to [newLeaderId]. The sync fills in the rest. */
data class TransferIntent(val groupId: String, val newLeaderId: String)

/** What the person asked to become leader is shown before agreeing (D6). Not a wire message. */
data class TransferOffer(val fromName: String, val groupId: String, val groupName: String)

/** Hello purposes used by a handover. The initiator sends "transfer"; the responder answers with one of the others. */
const val PURPOSE_TRANSFER = "transfer"
const val PURPOSE_TRANSFER_ACCEPTED = "transfer_accepted"
const val PURPOSE_TRANSFER_INVALID = "transfer_invalid"

fun Transfer.toProposal() = TransferProposal(groupId, newLeaderId, at, expectedEpoch)

/**
 * Plain-language reason a handover didn't happen. [asResponder] is true on the phone being asked to become leader,
 * where a few reasons read differently.
 */
fun TransferError.message(asResponder: Boolean): String = when (this) {
    TransferError.GROUP_NOT_SHARED -> "That group isn't on both phones."
    TransferError.GROUP_NOT_WRITABLE -> "A group that has been left can't be handed over."
    TransferError.SENDER_NOT_LEADER ->
        if (asResponder) "That person isn't the leader of this group on this phone. Sync with them first."
        else "Only the group's current leader can hand over leadership."
    TransferError.STALE_EPOCH ->
        "The two phones disagree about who leads this group. Sync normally first, then try again."
    TransferError.NEW_LEADER_IS_SENDER -> "Choose a member other than yourself."
    TransferError.NEW_LEADER_NOT_ACTIVE_MEMBER -> "That person isn't an active member of the group."
    TransferError.RESPONDER_NOT_NEW_LEADER ->
        if (asResponder) "Someone tried to hand you a group's leadership, but it was meant for another member."
        else "That's not the member you chose."
    TransferError.NOT_CONSENTED -> "They didn't agree to become the leader."
}

/** Reads back the reason a responder sent in a "transfer_invalid" Hello. */
fun refusalMessage(reason: String?): String {
    val error = reason?.let { runCatching { TransferError.valueOf(it) }.getOrNull() }
    return error?.message(asResponder = false) ?: "The other phone couldn't accept this handover."
}
