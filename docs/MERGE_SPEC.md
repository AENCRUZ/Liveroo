# Liveroo Phase 1 merge specification (Step 1a — DRAFT for review)

Status: **implemented** (Steps 1b–7 built it, Step 8 verified it end to end). This document is the design; the section
"Implementation notes" at the end lists where the code differs from the original draft.
Inputs: decisions D0–D13, N1–N12 and the approved R1–R6 design.

## 0. Scope and trust model

- Goal: any number of phones that sync pairwise, in any order, end with the **same effective state** once everyone has exchanged data with the holders of every valid change.
- Identities are self-asserted (D5). **Nothing here is cryptographic authentication.** A phone that forges a leadership relay can redirect authority. This is documented, not prevented.
- Clocks are wall clocks and may be skewed (D11). Ordering uses timestamps only *within* a leadership epoch; across epochs the epoch dominates.
- Mixed app versions do not sync (N12). The database is still version 1 and there is no migration (N10).

## 1. Building blocks

**Version (`Ver`)** — who wrote a value, under which leadership epoch, when:

```
Ver(epoch: Int, ts: Long, by: String)
order: epoch, then ts, then by (string compare)   // total order
```

**Register** — one logical field (or group of fields) with a `Ver`. Merge keeps the greater. If two *different* payloads carry an identical `Ver`, the one with the greater serialized form wins, so the order is total.

**Raw state vs effective state.**
- *Raw state* = registers, merged by the rules below. This is what is exchanged and what must converge.
- *Effective state* = a pure function of raw state (leader, members, assignee, status, deleted). It never depends on arrival order or local history.
- The existing columns (`tasks.title`, `status`, `assigneeId`, `deleted`, `groups.leaderId`, …) are kept as **materialized effective values**, recomputed inside the merge transaction. DAO queries and screens keep working unchanged.

## 2. Groups and the leadership chain

### 2.1 Structure
A group carries `leaderChain`: an ordered list of `(leaderId, at)`. Index = epoch.
- Entry 0 is `(creator, createdAt)`.
- `leaderId` = last entry. `leaderEpoch` = `size - 1` (derived, not stored separately).
- Each validated transfer appends one entry.

### 2.2 Chain order and join (deterministic, fork-safe)
Compare two chains entry by entry:
1. At the first index where the entries differ, the entry with the **smaller `at`** wins. If the `at` values are identical, the entry with the **lexicographically smaller `leaderId`** (plain string comparison) wins.
2. If one chain is a prefix of the other, the **longer** wins.

The winner is adopted **whole**. Entries from two different chains are never mixed. This is what prevents incompatible leadership branches from being combined. Chains whose entry 0 differs belong to different groups and the incoming group row is rejected.

Practical meaning: this is **timestamp-based deterministic resolution**. `at` is the initiating phone's wall-clock time, so the rule makes every phone pick the same chain. It does **not** prove which handover actually completed first, and with skewed clocks it can pick the handover that happened later.

### 2.3 Transfer (D6, D7)
`Transfer(groupId, newLeaderId, at, expectedEpoch)`, carried in `Hello` (purpose = `"transfer"`) so the new leader sees a consent dialog before any data moves.

Both phones must verify, and both reach the same verdict:
- the group is shared and writable on both;
- the sender is `leaderId` in the receiver's local chain and `expectedEpoch` equals the receiver's epoch (a stale view fails);
- `newLeaderId` is an active member and is not the sender;
- the responder **is** `newLeaderId` and explicitly consented (D7).

On success both phones append the identical entry `(newLeaderId, at)` and set `group.updatedAt = at`. If any check fails, nothing changes. Leadership never changes through an ordinary sync of group fields.

### 2.4 Relayed leadership (N4)
A phone that was not part of the transfer learns it from any peer whose chain wins under §2.2, provided that the chain's leaders are members of the group. This is accepted **on trust** (D5).

### 2.5 Forks
A fork can only arise when the final ack of a transfer is lost on one side, and the old leader then transfers again from the stale view. The deterministic order picks one chain everywhere. Edits made under the losing leader become invalid (§4.3) and are discarded. This is a documented, accepted limitation.

## 3. Members

Row: `(groupId, userId, displayName, joinedAt, leftAt?, updatedAt)`.
- `joinedAt` is new. It is set when the member is created or re-added.
- Merge: greater `updatedAt` wins; ties break on `leftAt` present, then `displayName`.
- **Active** = row exists and `leftAt == null`.
- Phase 1 does **not** validate who authored a member row (limitation, see §9).

## 4. Tasks

### 4.1 Registers

| Register | Fields | Who may write | Key |
|---|---|---|---|
| Content | title, description | leader of the epoch in `Ver` | `Ver` |
| Assignee | assigneeId?, **snapshot** (status the leader had seen: value + status key) | leader of the epoch in `Ver` | `Ver` |
| Tombstone | present or absent | leader of the epoch in `Ver` | present beats absent, then `Ver` |
| Status | value | leader, or the assignee | `(basis, Ver)` |

- **Basis** (status only) = the Assignee register's `Ver` the author held when writing.
- A **status key** is `(basis, Ver)`, compared in that order. Writing on top of a newer assignment outranks an older one regardless of clocks, so a stale writer cannot shadow a valid one.
- The task row also keeps a local `updatedAt/updatedBy` = latest change of any kind. It is used for sorting and `markClean`, and is not authoritative.

### 4.2 Receive rules
Process a message in this order: group chain → members → tasks. Then recompute effective values.

- Reject (never write): data outside the allowed groups, malformed data, and any version whose **author is not a member** of the group. Membership rows are never deleted while the group exists, so this check does not depend on arrival order.
- **Content, Assignee, Tombstone:** accept an incoming version only if it is **valid**: `chain[ver.epoch].leaderId == ver.by`, using the receiver's chain after the group step. A task that does not exist locally is created only if its first versions are valid.
- **Status:** never dropped for being "invalid". It is ranked by key only, and validity is applied in the effective view (§4.4). This is what keeps merging order-independent.
- Merge each register independently by its key.

### 4.3 Validity and forks
After a chain change (fork resolution), any local Content/Assignee/Tombstone version that is no longer valid is **tainted**: it is kept for display but ranks below every valid version. A valid version always replaces a tainted one. Tainted tombstones do not delete (the task reappears).

### 4.4 Effective view (pure functions)

**Deleted (D10).** `deleted` = a Tombstone is present and valid. It is sticky: no later edit clears it.

**Content.** Effective title/description = the Content register.

**Assignee (D3, N8).** Let `m` = the member row for `assigneeId`.
```
effectiveAssignee =
  assigneeId  if m exists && m.leftAt == null && assignee.ver.ts >= m.joinedAt
  null        otherwise
```
This replaces the old persisted `unassign`. Leaving clears assignments on every phone identically. Re-adding a member does not restore old assignments because `joinedAt` is newer than the assignment.

**Status (D2, N6 Option A).** Let `S` be the status register, `snap` the Assignee register's snapshot.
```
valid(S) = chain[S.ver.epoch].leaderId == S.ver.by      // leader-authored
        || S.ver.by == effectiveAssignee                // assignee-authored
        || S.key <= snap.key                            // the leader had already seen it

effectiveStatus =
  S.value        if S exists && valid(S) && S.key > snap.key
  snap.value     otherwise
```
Meaning: a status the leader had seen when reassigning is kept. A status written without having seen the reassignment is discarded unless its author is still the assignee. Anything invalid falls back to the leader's snapshot. A forged status can only revert to the snapshot. It can never set an arbitrary value.

### 4.5 Local writes (Step 3)
- `saveTask` (create or edit title, description, assignee) and `deleteTask`: leader only (D2, D3). The assignee must be an active member.
- `setStatus`: leader, or the effective assignee (D2).
- Every write also sets local `dirty`. `Ver.epoch` is the author's current `leaderEpoch`.
- A leader reassignment records `snapshot` = the current effective status and its key.

## 5. Scoping and invites (summary, detailed in Steps 4, 6 and 7)

- **Ordinary sync (D4):** group G is exchanged only if both phones hold G and, in my local data, the peer is an active member. Otherwise show the N9 message.
- **Departure (N1):** a phone that has already recorded my departure still counts as shared, for confirming the departure only.
- **Invite (D8, D9, N2):** the invitee commits first; the leader adds the member after the invitee's ack. If the invitee already holds the group, reject — except idempotent completion when the offer comes from the group's current leader and the invitee is already an active member.

## 6. Schema and wire changes

**Database (version 1 stays, edited in place — N10):**
- `groups`: add `leaderChain TEXT NOT NULL` (JSON list of `{id, at}`). `leaderId` stays as the materialized last entry.
- `members`: add `joinedAt INTEGER NOT NULL`.
- `tasks`: add `registers TEXT NOT NULL` (JSON of the four registers). Existing columns remain as materialized effective values.
- `app/schemas/.../1.json` is regenerated and committed.

**Wire v2 (not compatible with the old format — N12):**
- `Hello(v, userId, displayName, groupIds, purpose, transfer?)`. `purpose` ∈ sync, transfer, invite, declined.
- `Transfer(groupId, newLeaderId, at, expectedEpoch)`. `DataMsg.transfer` is removed.
- `GroupDto` carries `leaderChain`. `MemberDto` adds `joinedAt`. `TaskDto` carries the registers.
- `InviteAnswer(accept, reason?)`. The invitee's ack means "committed".
- A mismatched or missing `v` fails before any data is exchanged: "Update Liveroo on both phones."

## 7. Why this converges

- Group chain: join = maximum under a total order → commutative, associative, idempotent.
- Members, Content, Assignee, Tombstone, Status: join = maximum under a total order (Status is never filtered by validity at merge time).
- Effective state is a pure function of merged raw state.
- Receive-time rejection uses only facts that do not change with arrival order: allowed group, malformed, author is a member, and "author led epoch *e*" (stable unless a fork occurs, in which case §4.3 re-ranks deterministically).

**Assumptions:** every valid change still exists on its holder and syncing continues. The guarantee is *eventual*: after a change is exchanged with everyone who needs it, every phone shows the same effective state. It is not instantaneous and not cryptographic.

## 8. Worked examples

1. **Rename vs status.** Leader renames at t10 (Content). Assignee A (stale) sets status at t12 (Status). Different registers, so both survive on every phone, in any order.
2. **Reassign vs status.** Leader reassigns A→B at t10 without having seen A's status S (written t12). Status key of S is below B's basis, and A ≠ B, so S is invalid. Effective status = snapshot. If B later writes a status, its basis is the new assignment, so it outranks S. Order of arrival does not matter.
3. **Double transfer.** O→N1 at t1 (O misses the commit), then O→N2 at t2. Chains `[O,N1]` and `[O,N2]` fork at index 1. The smaller `at` wins (and if `t1 == t2`, the lexicographically smaller leader id wins), so every phone settles on the same chain. N2's edits are tainted and discarded if N1's chain wins.

## 9. Known limitations (to be copied to the README)

- Self-asserted identities; relayed leadership accepted on trust.
- Wall-clock skew within an epoch.
- Edits made under a losing leadership branch are discarded.
- Fork resolution is timestamp-based, not proof of which handover completed first (see §2.2).
- A non-assignee member could forge a status. Effect is limited to reverting a task to the leader's snapshot.
- Member rows (name, `leftAt`) are not authenticated per author in Phase 1.
- A reassignment A→B→A while A's status is still in flight can lose that status update.
- A leader's pre-transfer edit that arrives late through a third phone can be rejected if that phone already knows the new chain (G12; believed rare because the previous leader's data reaches the new leader during the transfer).
- Two phones cannot atomically commit over one Bluetooth link. A lost final message can leave one side ahead until the next sync.
- Local group deletion by a sole-member leader does not delete the group on other devices.

## 10. Test plan (maps to the checklist)

- **Pure rules (Step 2):** ordering and ties for `Ver` and status keys; chain order (prefix, fork, tie, different origin); effective assignee/status/deleted truth tables; validity and taint.
- **Convergence (Step 4):** randomized runs with 3 and 4 simulated devices — create, edit, reassign, status, delete, leave/rejoin, transfer, injected fork — over random sync pairs, then full gossip to fixpoint. Assert identical effective state everywhere, idempotence (merging twice changes nothing), and order independence of pairwise merges.
- **Rejection (Step 4):** foreign group, non-member author, non-leader content/assignee/delete, malformed data.
- **Transfer (Step 5), invites (Step 6), departures (Step 7):** as in the approved plan, over loopback channels with injected drops.

## 10a. Implementation notes (where the code differs from the draft)

- **`leaderEpoch` is not a column.** It is derived: the length of `leaderChain` minus one.
- **Handover on the wire.** The request travels in `Hello` (purposes `transfer`, `transfer_accepted`, `transfer_invalid`,
  with an optional `reason`); `DataMsg` carries no transfer. The responder runs every check except consent before it asks.
- **One commit.** `commitSync` merges, clears "Not yet synced" for confirmed groups and applies a handover in a single
  transaction, re-checking the handover on the merged data and rolling everything back if it fails. The group row stays
  marked unsynced afterwards, because third phones haven't heard.
- **`Ack` carries three lists:** `accepted` (groups this phone merged), `departureConfirmed` (groups where it had already
  recorded the other phone's departure, no data exchanged) and `skipped` (shared groups it skipped). Departures finish only
  when the peer merged them or confirmed them; the skipped list drives the N9 message.
- **N9 wording.** The approved sentence is shown unchanged, prefixed with the group's name ("Study: ...") so several skipped
  groups can be told apart.
- **Invitations.** The payload carries the invitee's member row built in memory; the invitee stores the group and then
  confirms; the leader writes the row (same timestamp) only after the confirmation. The invitee's own decision (new,
  completion, refuse) is made before the person is asked.
- **`addMember` is idempotent for an active member,** so re-adding someone who never left keeps their join time (and so
  their assignments).
- **A member taken back by the leader** gets a usable group again: the local read-only flag clears when the leader's
  re-add reaches them, and a pending departure is never finished for someone whose own member row is active.
- **Redundant clause.** The third validity clause in the effective status ("the leader had already seen it") never changes
  the result, because the snapshot already wins in that case. It is implemented as written.
- **Not implemented, by decision:** per-member sync tracking (D13), cryptographic identities (D5), automatic retries (T8),
  insecure RFCOMM (T15).

## 11. Decisions requested

1. **Fork rule (APPROVED):** timestamp-based deterministic resolution — smaller `at` wins, then lexicographically smaller `leaderId`. Not a proof of which handover completed first. Rejected alternative: make the old leader commit first so forks cannot occur, at the cost of a possible leaderless gap and breaking the "you stay the leader if it fails" promise.
2. **Schema shape:** JSON `registers` column plus materialized effective columns (recommended) versus about 25 individual columns.
3. **Member rows unauthenticated** in Phase 1 (§9).
4. **Derived assignment** uses `joinedAt`, so a re-added member does not get old tasks back.
5. **Forged status** can only revert to the snapshot (§9).
