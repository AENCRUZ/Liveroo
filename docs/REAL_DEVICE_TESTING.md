# Real-device testing status

Last updated: 2026-11-24. Everything in Phase 1 was built and checked on the JVM (181 automated tests, including
simulated phones over loopback connections). This document tracks what has been tried on **real phones over real
Bluetooth**, which the automated tests can't cover.

## At a glance

| Area | Status |
| --- | --- |
| Install and launch on two phones | **Passed** (reported by the project owner) |
| Group functionality between two phones | **Passed** at a basic level (reported; flows not itemised, see below) |
| Third phone | Not tested |
| Connection interruptions | Not tested |
| Reconnection after a failure | Not tested |
| Sync after being offline | Not tested |
| Android version coverage (10–11 vs 12+) | Not tested |

**Real-device testing is not complete.** Only the first, basic two-phone round has been done.

## Done

### Round 1: two phones, basic check: passed (2026-11-24)

Reported by the project owner: the app installed and launched properly on both phones, and group functionality was
tested between the two devices and worked.

Not recorded, so please add them when convenient:

- phone models and Android versions;
- which flows were exercised (creating a group, inviting or joining, assigning tasks, status changes, syncing, leadership
  transfer, leaving);
- whether the first-time Bluetooth pairing dialog appeared and how it behaved.

Until that is filled in, this round counts as "the basics work", not as proof that every flow works on hardware.

## Not done yet

Do not treat any of these as passed.

- [ ] **Third phone.** Leadership relayed to a phone that wasn't present, the skipped-group message, three-way agreement
      after syncing in different orders.
- [ ] **Connection interruptions.** A link that drops during connecting, while a prompt is open, or during the exchange.
- [ ] **Reconnection.** Trying again after a failure, in either direction, with nothing left half-applied.
- [ ] **Sync after being offline.** Changes made on both phones while apart (Bluetooth off, or far away), then a sync.
- [ ] **Android versions.** Android 12+ ("Nearby devices" permission) and Android 10–11 (location permission, and the
      system Location switch must be on for discovery).

## Recommended next test: interruptions, on the two phones you already have

Why this one first: it needs no extra hardware, and it checks the claim the automated tests can only simulate, that
nothing is applied until both phones have confirmed. Real Bluetooth also fails in ways loopback can't.

**The exchange itself takes a fraction of a second, so don't try to catch it by timing.** Use the moments the app waits
for a person, which you can hold open as long as you like:

1. **Prompt held open.** Phone A taps Start Sync and picks Phone B. On B leave the "A wants to sync. Allow?" prompt
   open. Now break the link on A (turn Bluetooth off, or move out of range). Then answer the prompt on B.
2. **Invitation held open.** Leader starts an invitation; on the invited phone leave the "You're invited" prompt open,
   break the link from the leader's side, then tap Join.
3. **Handover prompt held open.** Same idea with a leadership transfer: leave "wants to make you the leader" open and
   break the link, then tap Become leader.

Before each, make a change on **each** phone that hasn't been synced (for example a new task on the leader and a status
change on the member), so there is something to lose.

**Pass criteria, after every interruption:**

- Neither phone shows a half-done result: no new members, no changed leader, no merged tasks that weren't confirmed.
  "Not yet synced" still shows on both.
- An interrupted invitation leaves no ghost member on the leader's phone.
- An interrupted handover leaves the same person leading on both phones.
- Turning Bluetooth back on and tapping **Try again** (there is no automatic retry yet) completes normally, and both
  phones then show the same tasks.

**Also watch for these known, unfixed problems** while testing (they are in the README's list):

- **Rotating the screen** during a sync cancels it, because the sync is tied to the screen.
- A phone waiting for an answer can **wait indefinitely** if the other phone goes silent: there are no timeouts yet.
  Note how long you waited and whether Cancel works.
- On Android 10–11, discovery finds nothing if the system **Location** switch is off.

## After that

1. **Sync after being offline** on the same two phones: work separately for a while, including a rename on the leader and
   a status change on the member of the same task, then sync. Both changes should survive.
2. **Third phone**: the relayed-leadership and skipped-group checks.
3. Repeat the key flows on a second Android version (10–11 versus 12+).

## Results log

Add a row for each real-device round.

| Date | Phones (model, Android) | What was tried | Result | Notes |
| --- | --- | --- | --- | --- |
| 2026-11-24 | Two phones (not recorded) | Install, launch, group functionality between the two | Passed | Reported by the project owner. Details not recorded. |
