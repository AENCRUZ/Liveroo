# ·˚𓆉 ༘₊· Liveroo ·˚𓆉 ༘₊· 
An Android group task app

Kotlin + Jetpack Compose + Room + Classic Bluetooth (RFCOMM). Groups share tasks phone to phone with no server.
Built from the approved **Liveroo UI/UX Screen Specification** (C1–C23, P1–P19, T1–T16).

## Open it

1. In Android Studio choose **File → Open** and select the `Liveroo` folder (the one with `settings.gradle.kts`).
2. Let Gradle sync (Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, JDK 17 or newer).
3. Run on **two real Android phones** (Android 8.0 / API 26 or newer). The emulator can't do Bluetooth discovery or RFCOMM.

There is no `gradlew` script in the repository. From a command line, run `gradle wrapper` once if you want one.

## Run the tests

```
gradle testDebugUnitTest
```

Real-device testing status is tracked in [`docs/REAL_DEVICE_TESTING.md`](docs/REAL_DEVICE_TESTING.md).

The unit tests run on the JVM (Robolectric, no phone needed). They include whole-conversation tests between simulated
phones, each with its own database, and randomized runs where 3 and 4 phones edit, hand over leadership, leave, return
and sync in random order and must end up identical. The first run downloads Robolectric's Android jar.

## Where things are

| Folder | What's in it |
| --- | --- |
| `ui/theme`, `ui/components` | Colours, type scale, scaffold, banners, task rows, refusal messages |
| `ui/screens` | All 18 screens (S01–S18), grouped by area |
| `ui/nav` | Routes and the NavHost |
| `data/local` | Room tables (groups, members, tasks, known devices) and queries |
| `data/model` | The task "registers", versions and leadership chain stored and sent |
| `data/LiverooRepository.kt` | All reads and writes. Enforces who may do what, merges what a groupmate sent |
| `data/Materialize.kt` | Builds the task rows the screens read from the registers |
| `data/prefs` | Display name + generated user ID (C16, T3) |
| `rules` | **Pure rules, no Android**: permissions, merge order, leadership chain, handover and invite checks, sync scope |
| `bluetooth` | Permissions (T2), discovery, visibility, RFCOMM connect/accept |
| `sync` | Message format (JSON with length prefix), the sync / invite / handover handshakes |
| `docs/MERGE_SPEC.md` | The design the merge and sync follow, with the reasoning and the accepted trade-offs |

## Screen map

| Spec | Composable | File |
| --- | --- | --- |
| S01 Splash | `SplashScreen` | OnboardingScreens.kt |
| S02 Permissions (with Skip, P19) | `PermissionsScreen` | OnboardingScreens.kt |
| S03 Bluetooth off | `BluetoothOffScreen` | OnboardingScreens.kt |
| S04 Name setup | `NameSetupScreen` | OnboardingScreens.kt |
| S05 Groups list + "+ Group" sheet | `GroupsScreen` | GroupScreens.kt |
| S06 Create group | `CreateGroupScreen` | GroupScreens.kt |
| S07 Group detail (leader / member / left) | `GroupDetailScreen` | GroupScreens.kt |
| S08 Members and progress | `MembersScreen` | GroupScreens.kt |
| S09 Invite members | `InviteScreen` | BluetoothScreens.kt |
| S10 Join a group | `JoinScreen` | BluetoothScreens.kt |
| S11 Transfer leadership | `TransferScreen` | GroupScreens.kt |
| S12 Group settings (leave, delete from this phone) | `GroupSettingsScreen` | GroupScreens.kt |
| S13 Task detail (3 role variants) | `TaskDetailScreen` | TaskScreens.kt |
| S14 Create / edit task | `TaskFormScreen` | TaskScreens.kt |
| S15 Sync + transfer mode | `SyncScreen` | BluetoothScreens.kt |
| S16 Sync result | `SyncResultScreen` | BluetoothScreens.kt |
| S17 Settings | `SettingsScreen` | SettingsScreen.kt |
| S18 My Tasks | `MyTasksScreen` | TaskScreens.kt |

## Who may do what

The repository enforces these itself, from its own data, whatever the screens show.

- **The leader** creates, edits and deletes tasks, assigns them (to active members only), invites people, and can
  change the status of any task.
- **The assigned member** can change the status of their own task, and nothing else.
- Other members can't change tasks. Members can leave, but **a leader must transfer leadership before leaving**.
- A leader who is the **only member** can't transfer, so they may **delete the group from this phone**. This is local
  only: other phones that have the group keep their copy, and anything not yet synced is lost.

## How sync works

One phone at a time (C14). Both people tap **Start Sync**; one taps the other in the list.

1. The phones swap a `Hello` (who they are, which groups they have, the app's protocol version).
2. **Which groups take part.** A group is exchanged only if both phones have it and *each side's own data* shows the
   other as an active member. A group where the other person has already left only gets a departure confirmation, with
   no group data. Any other group is skipped, and the skipped person is told: *"This group wasn't synced because the
   other device doesn't recognize you as an active member. Sync with the group leader first."*
3. Both send their data for the groups taking part, then an `Ack` that lists the groups they merged.
4. **Only after both acks** does each phone apply everything in one database transaction: merge, clear "Not yet
   synced" for groups both sides merged, and (for a handover) record the new leader. The checks run again on the merged
   data, and if they fail nothing at all is applied.

### How edits merge

Every task keeps separate pieces, each with its own version (who wrote it, under which leader, when):
title and description, assignee, deletion, and status. Pieces merge independently, so a rename and a status change made
apart both survive, in any order, on every phone.

- Leader-only pieces count only if they were written by the leader of the period they were written in. A member can't
  forge a rename, reassignment or deletion.
- **Deletion wins** over any concurrent edit, and stays.
- A status counts if the leader or the current assignee wrote it. A status written by the previous assignee without
  knowing about a reassignment is discarded and the leader's last-seen status shows.
- **Leaving unassigns your tasks** on every phone, worked out from the departure, not written separately. Being added
  back later does not give the old tasks back.

### Leadership transfers

The new leader must be nearby, and must **explicitly agree** on their own phone. Both phones check the same facts
(the sender leads, the group is up to date on both, the new leader is an active member, the new leader answered) and
record the identical entry, so leadership moves only if the sync finishes. A wrong phone is refused before it is ever
asked. Each group keeps a chain of its leaders; phones that weren't there learn the change from any groupmate.

### Invitations

The invited person stores the group first and confirms. The leader writes the new member **only after that
confirmation**, so a failed invitation leaves no ghost member. Inviting someone who already has the group is refused,
except that a leader can re-invite a member whose last confirmation was lost: nothing is asked and nothing is
overwritten.

## Known limitations

These are accepted trade-offs, not bugs to be found later.

- **Not secure against a malicious phone.** Identities are self-asserted: a phone says who it is and nothing proves it.
  Leadership changes relayed through a third phone are accepted on trust. A phone that lies about identity can mislead
  its groupmates. There is no cryptographic authentication.
- **Phone clocks.** Versions use each phone's clock. Within one leader's period, a phone with a wrong clock can win or
  lose a tie it shouldn't. A new leader's work always outranks an old leader's, whatever the clocks say.
- **Two phones can't commit at the same instant over Bluetooth.** If the last message of a sync, handover or
  invitation is lost, one side may have applied it and the other not, until the next sync. "A dropped connection changes
  nothing" holds until both acks are exchanged, not after.
- **Competing handovers.** If a handover's last message is lost and the old leader then hands over again, the group has
  two leaders for a moment. Every phone then settles on one, chosen by the earlier timestamp (then the smaller user ID).
  This is a timestamp rule, not proof of which handover really finished first. Work the losing leader did meanwhile is
  discarded everywhere else, and tasks created entirely under the losing branch stay on that leader's own phone.
- **A late edit from a former leader** that reaches a phone only after it learned the handover can be refused. This is
  believed rare because the former leader's data reaches the new leader during the handover itself.
- **Members' own details aren't authenticated.** Anyone can claim to have renamed or removed a member row; Phase 1
  doesn't check who wrote them.
- **A forged status** by a member who isn't assigned can hide a valid one, in which case the leader's last-seen status
  shows. It can never set an arbitrary value.
- **Reassigned back and forth.** If a task goes A → B → A while A's status update is still in flight, that update can be
  lost.
- **"Not yet synced" means "not synced with anyone yet"**, cleared after the first confirmed groupmate, not per person.
- **Leaving needs a phone that knows you.** A departure completes only with a groupmate who merged it or already had it.
  Until then the group stays on your phone, read-only.
- **Deleting a group from this phone doesn't delete it anywhere else.** There is no way to delete a group for everyone.
- **Both phones must run the same version of the app.** Different versions refuse to sync ("Update Liveroo on both
  phones") and don't exchange anything.
- **No cloud backup.** Everything lives on the phones, so sync often.

## Still to do (marked `TODO` in the code)

- **T8** – automatic retries with backoff ("Retrying (2 of 3)").
- **T15** – secure vs insecure RFCOMM (insecure skips the pairing-code dialog).
- **P10** – send a changed display name in the next sync. The Settings screen already says it does; it doesn't yet.
- **Join screen** – after a declined invitation it goes back to "Ready to join", but the phone is no longer listening.
- **Lint** reports one error that was already in the starter project: the manifest asks for fine location without coarse
  location (needed only on Android 10–11), which lint flags. It also reports 46 warnings (mostly outdated library
  versions, no app icon, no data-extraction rules). The error is not fixed because changing permissions deserves its own
  decision.
- The release build works (an unsigned APK).
- **Real-device testing is in progress, not complete.** A first round on two phones passed (install, launch and basic group
  functionality, reported 2026-11-24). Still to do: a third phone, connection interruptions, reconnection, and syncing
  after being offline. See `docs/REAL_DEVICE_TESTING.md` for the status, the next recommended test and a results log.
- **Known and not fixed** (found before Phase 1 and outside its scope): rotating the screen during a sync cancels it;
  reads have no timeout, so a phone can wait indefinitely if the other goes silent; on Android 10–11 discovery needs the
  system Location switch on, and the app doesn't check for it.

## Fonts

`res/font/luckiest_guy.ttf` (Apache License 2.0) and `res/font/comfortaa_variable.ttf` (SIL Open Font License 1.1) come
from Google Fonts. Their licences are in `licenses/`.
