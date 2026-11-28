# Liveroo Developer Handoff Spec (UI/UX redesign)

Exported 2026-11-28 from the approved handoff spec. Online copy: https://claude.ai/artifact/7jgezV2RRSLg7f4E1ApGVm

## Overview

This spec turns the approved designs into implementation instructions for Liveroo's Jetpack Compose UI. It changes presentation only: every screen keeps its routes, actions, data sources and outcomes.

**Source of truth, in order**

1. The approved design canvas, "Liveroo Redesign" (https://claude.ai/artifact/5HdiNM8RdzYHz1pmPogTeM): pages Round 1 · Key screens, Round 2 · All other screens, Round 3 · States, dialogs, flows. Each frame is named by screen ID (S01–S18) and state.
2. This handoff spec (exact values).
3. "Liveroo Design System & Screen Plan" (https://claude.ai/artifact/PnPs7csbvndBpF9HUtozpc) (rationale and decisions).

If the canvas and this spec disagree, the canvas wins and the spec is corrected.

**Implementation rules (non-negotiable)**

- Only the presentation layer changes: `ui/theme`, `ui/components`, `ui/screens`, and `res/` (fonts, theme XML, strings).
- `data/`, `rules/`, `sync/`, `bluetooth/`, `LiverooApp.kt`, Room schemas and tests are not touched.
- Inside screen files, everything that is not layout stays word for word: repository calls, coroutines, `LaunchedEffect` and `DisposableEffect` blocks, permission and Bluetooth launchers, navigation calls, dialog confirm/dismiss handlers (including tap-outside = decline on sync and invite requests).
- Routes in `ui/nav` stay identical.
- No design changes beyond the approved frames. Anything that needs more is raised with the project owner before it is built.
- Git is handled by the project owner only. No branches, commits, pushes, merges, or changes to Git files by any assistant.

## Stages and status

| Stage | Scope | Status |
| --- | --- | --- |
| 1 | This handoff spec | Done, approved |
| 2 | Theme: light/dark tokens (`Color.kt`), Nunito + Luckiest Guy (`Type.kt`), `Theme.kt`, `Dimens.kt`, `values-night/themes.xml`, Nunito fonts and licence | Done; built successfully (`:app:assembleDebug`) |
| 3 | Shared components in `ui/components`: rewrite `Components.kt`; add `FloatingNav.kt` and `Inputs.kt` (see "Components" and "Screen frame and navigation"). Screens keep compiling; screen layouts are not redesigned yet | Next |
| 4 | Screens S01–S18 in `ui/screens`, layout only (see "Screens S01–S18") | Pending |
| 5 | Accessibility pass (see "Accessibility requirements") | Pending |
| 6 | Final scope audit and changed-file comparison | Pending |

Each stage is reviewed and built by the project owner before the next one starts.

## Colour and theme tokens

Tokens live in a `LiverooColors` data class with a light and a dark instance, provided through a `CompositionLocal` (`LocalLiverooColors`) and read as `LiverooTheme.colors.x`. The Material 3 `colorScheme` is filled from the same values so stock components (dialogs, menus, text fields, snackbars) match. The theme is an **in-app setting, default light**, switched with the "Dark mode" switch in Settings; the phone's own dark-mode setting is ignored. See `docs/REDESIGN_ADDENDUM_01.md` (Part A), which replaces the earlier "follows the system setting" rule.

| Token | Light | Dark | Used for |
| --- | --- | --- | --- |
| background | #F4EBD0 | #121813 | Screen background |
| surface | #FFFFFF | #1B221C | Cards, grouped lists, dialogs, sheets |
| surfaceTile | #FAF6EA | #232B24 | Icon tiles, info banners, avatar fallback |
| textPrimary | #1F2A1C | #EEF2EA | Titles, body, icons |
| textSecondary | #4A5547 | #A9B5A6 | Subtitles, captions, section labels |
| accent | #2A7C13 | #76C457 | Primary buttons, selection, active nav, links, brand wordmark |
| onAccent | #FFFFFF | #10200B | Text and icons on accent |
| accentContainer | #E8F2E0 | #1E3318 | Selected segment, Leader tag, tonal buttons |
| onAccentContainer | #1F2A1C | #9BDB7F | Text on accentContainer |
| statusProgress | #2F6B57 | #90BEAB | In progress icon |
| statusProgressFill | #90BEAB | #90BEAB | MutedSage fill (legacy) |
| statusDone | #76C457 | #76C457 | Done icon fill |
| onStatusDone | #1F2A1C | #10200B | Check mark on Done fill |
| pendingSurface | #FFF1D6 | #3A2C0E | Waiting-to-sync chips, banners, icon tiles |
| pendingText | #7A4F00 | #F2C46B | Text and icons on pendingSurface |
| error | #A3402B | #F09A85 | Destructive actions, field errors, failures |
| errorSurface | #FBEBE7 | #3A1A14 | Error banners and tiles |
| divider | #EDE5CF | #2A332B | Row dividers, progress-ring track |
| outlineControl | #8A8F7E | #6B786E | Borders of fields, unselected chips and segments (3:1) |
| navBar | #FFFFFF | #263027 | Floating nav pill |
| navBorder | #E3D9BC | #2E3A2F | 1 dp nav pill border |
| navInactive | #4A5547 | #C9D3C6 | Inactive nav icon and label |
| navBadge | #F2C46B | #F2C46B | Sync badge fill (text #3A2C0E) |
| snackbar | #1F2620 | #EEF2EA | Snackbar fill (text #F4EBD0 light, #1B221C dark) |
| scrim | #0A0E0A at 48% | same | Behind dialogs and sheets |

**Avatar colours (both themes, initials always #1F2A1C):** #F6C9A8, #F3B9B0, #F5D98C, #E3C6E8, #B9D9EE, #B8E0CF, #CFE3A6, #D4CCF2. Pick by a stable hash of the display name (`Math.floorMod(name.hashCode(), 8)`, implemented as `avatarColor(name)`), so a person keeps one colour.

**Window theme:** `res/values/themes.xml` keeps cream; `res/values-night/themes.xml` is **removed** (addendum 01, A4), because the app no longer follows the phone's dark mode. Users with Dark mode on see a brief cream launch frame before the splash; this is accepted.

**Legacy colour names** (`WarmCream`, `ForestGreen`, `Ink`, `White`, etc.) remain as `@Composable` getters mapped to the tokens so un-migrated code compiles. They are removed once stages 3 and 4 no longer reference them.

**Rules:** accent marks one primary action per screen; amber means "waiting to be shared", never an error; status is always icon + label; buttons on FreshGreen use dark text.

## Typography

Two families. **Nunito** (four static weights 500, 600, 700, 800, OFL) for all regular UI text, headings, buttons and navigation. **Luckiest Guy** (existing) only for brand moments. Comfortaa is no longer referenced. All sizes in sp so they scale with the phone's font setting. Named styles are read as `LiverooTheme.type.x`.

| Style (Compose) | Family | Size / line height | Weight | Used for |
| --- | --- | --- | --- | --- |
| brandDisplay | Luckiest Guy | 56 / 60 sp | Regular | Splash wordmark |
| brandHeadline | Luckiest Guy | 28 / 32 sp, letter-spacing 0.5 sp | Regular | Onboarding headlines (S02, S03, S04), "Synced with …", "You joined" |
| titleLarge | Nunito | 28 / 34 sp | ExtraBold (800) | Tab screen titles: Your groups, My Tasks, Sync with a groupmate |
| titleMedium | Nunito | 22 / 28 sp | ExtraBold (800) | Top-bar titles, group name, task title, centered-state titles |
| dialogTitle | Nunito | 20 / 26 sp | ExtraBold (800) | Dialog titles, empty-state titles |
| heading | Nunito | 17 / 24 sp | Bold (700) | Section headers (To Do, Groupmates) |
| cardTitle | Nunito | 17 / 24 sp | ExtraBold (800) | Group names on cards |
| itemTitle | Nunito | 16 / 22 sp | Bold (700) | Task, member, device and setting row titles |
| bodyLarge | Nunito | 16 / 24 sp | Medium (500) | Descriptions, centered-state body, field values |
| body | Nunito | 14 / 20 sp | Medium (500) | Subtitles, banners, assignee names |
| label | Nunito | 15 / 20 sp | Bold (700) | Section labels ("Status", "Assignee"), in textSecondary |
| caption | Nunito | 13 / 18 sp | Medium (500) | Counts, helper text, timestamps, group name above a task |
| button | Nunito | 15 / 20 sp | Bold (700) | All buttons and the FAB |
| chip | Nunito | 13 / 18 sp | Bold (700) | Chips, tags, nav labels, status labels |
| micro | Nunito | 12 / 16 sp | Bold (700) | "Not yet synced" chip in rows, field labels on the border |

**Rules**

- Luckiest Guy never appears in lists, buttons, tags, dialogs or text longer than about 6 words.
- Section labels are sentence case ("Assignee", not "ASSIGNEE").
- Nothing smaller than 12 sp; 12 sp only for micro labels.
- Titles wrap to 2 lines; no single-line ellipsis on group or task names.
- Avatar initials: Nunito ExtraBold, about 32% of the avatar size.

## Spacing, sizing, radii, elevation, icons, motion

All spacing is on a 4 dp grid and comes from `LiverooDimens` / `LiverooRadius` (`ui/theme/Dimens.kt`); no one-off values in screens.

| Token | Value | Used for |
| --- | --- | --- |
| space4 / 8 / 12 | 4 / 8 / 12 dp | Icon-to-text gaps, chip padding, gaps inside rows |
| space16 | 16 dp | Screen side margin (tab and detail screens), card padding, gap between stacked blocks |
| space24 | 24 dp | Onboarding and centered-state side margin, bottom button inset |
| rowMin | 56 dp one line, 64 dp with avatar/tile, 72 dp two lines | List rows |
| touchMin | 48 dp | Every tappable element (icon buttons 48 × 48) |
| buttonHeight | 52 dp primary, 48 dp tonal/text | Buttons |
| fieldHeight | 56 dp (multi-line min 104 dp) | Text and select fields |
| segmentHeight | 60 dp min, grows with text | Status selector |
| chipHeight | 40 dp visible, 48 dp touch | Filter chips |
| iconTile | 40 dp (radius 12), 56/64 dp hero tiles (radius 16–20), 96 dp centered-state tiles (radius 28) | Leading icons |
| avatar | 22, 28, 40, 64, 72 dp | Rows, stacks, profile |
| navBar | 64 dp tall, 16 dp side inset, 16 dp above the gesture area | Floating nav |
| fabOffset | 16 dp right, 96 dp from bottom (above nav) | FAB |
| listBottomPadding | 120 dp on screens with nav (FAB + nav clearance) | Lazy lists |

| Radius | Value | Used for |
| --- | --- | --- |
| small | 12 dp | Icon tiles, fields, banners, segmented control |
| medium | 20 dp | Grouped cards, group cards |
| large | 24 dp | Hero cards (sync status, sync result), dialogs |
| sheet | 28 dp top corners | Bottom sheet |
| full | 50% | Buttons, chips, tags, nav pill, avatars, FAB |

**Elevation:** flat by default (cards have no shadow and no border). Shadow only on floating elements: nav bar, FAB, dialogs, sheet, snackbar (`0 6dp 20dp`, 25% black in light, 50% in dark; Compose `shadowElevation` about 6 dp).

**Icons:** Material Icons Rounded (`material-icons-extended`, Rounded set), 24 dp default, 20 dp in tiles and buttons, 14–16 dp in chips. Status icons: To Do = outlined circle; In progress = half-filled circle; Done = filled circle with check. Decorative icons get `contentDescription = null`.

**Motion:** 150–250 ms, standard easing. Nav indicator slides between tabs; Done section and member rows expand/collapse; filter-chip arrow rotates 180° at the end of the row. No looping animation except existing progress spinners. Respect the system "Remove animations" setting.

## Components

Each component is a Composable in `ui/components`. Screens use only these, so look and spacing stay consistent. All take colours from `LiverooTheme.colors`.

| Composable | Anatomy and sizes | States / variants | Interaction and accessibility |
| --- | --- | --- | --- |
| `LiverooFloatingNav` | Pill 64 dp, navBar fill, 1 dp navBorder, shadow; 3 equal items (icon 22 dp over chip label); active item = accent pill behind icon and label, onAccent content | Groups / My Tasks / Sync active; Sync badge (navBadge, count, hidden at 0) | Same navigate() calls as today's bar. Item: role Tab, selected state, label "Sync, 3 changes waiting" |
| `GroupedCard` | surface, radius 20, no border; children separated by `RowDivider` (1 dp divider, inset 16 dp, or 68 dp when rows have a leading tile/avatar) | Optional header row (label + count pill) | Container only |
| `ListRow` | Leading slot (tile, avatar, ring) · title (itemTitle) · subtitle (body/caption, textSecondary) · trailing slot · optional chevron 20 dp; padding 12/16 dp; min height per rowMin | Tappable / static / disabled (55% opacity) / destructive (error text, errorSurface tile) | Whole row is one click target and one TalkBack node (mergeDescendants) |
| `TaskRow` | ListRow: title, assignee (avatar 22 dp + name or "Unassigned"), optional `PendingChip("Not yet synced")`, trailing `StatusIndicator`, chevron. My Tasks: group name (caption) above title | To Do / In progress / Done; unsynced | Reads "Title, assignee, status, not yet synced" |
| `GroupCard` | Card radius 20, padding 14/16: name (cardTitle) + `RoleTag`; row with 40 dp `ProgressRing`, "5 of 9 tasks done" + "3 To Do · 1 In progress" (caption), `AvatarStack`; `SyncStateChip` below | Leader / member / left ("Read-only until your departure syncs", Left tag, no ring) | Tap opens group detail |
| `ProgressRing` | Track divider, arc accent, round cap, starts at 12 o'clock; 40 dp (stroke 5) in cards, 64 dp (stroke 7) with "5/9" centered in Group detail | 0, partial, complete; no tasks = empty track | Decorative; the text next to it carries the meaning |
| `StatusSummary` | Read-only line: To Do icon + count, In progress icon + count, Done icon + count, then "To Do · In progress · Done" (caption) | — | Not clickable; no border or fill |
| `StatusIndicator` | Status icon 16 dp + label (chip style); no border, no fill | To Do, In progress, Done | Read-only |
| `StatusSelector` | Segmented control: outlineControl 1.5 dp border, radius 12; 3 equal segments, icon 20 dp over label; min 60 dp, grows with text | Selected = accentContainer fill + 2 dp inset accent ring + ExtraBold label; unselected = surface; read-only screens show StatusIndicator + banner instead | Radio group; announces "In progress, selected" |
| `SyncStateChip` / `PendingChip` | Pill, pendingSurface + pendingText, clock icon 12–14 dp; shared variant: accentContainer + check-circle in accent | "N changes waiting", "1 change waiting to sync", "Not yet synced", "Left · not yet synced", "All changes shared" | Read-only |
| `SavedOnPhoneCard` | Row card: 40 dp pendingSurface tile with phone icon, "Saved on this phone" / "N changes waiting to sync", chevron; 1 dp divider border | Shown only when N > 0 | Tap navigates to Sync (same route as the Sync tab) |
| `Avatar` / `AvatarStack` | Circle, colour from name hash, initials ExtraBold; stack overlaps −8 dp with 2 dp ring in the card colour, max 3 + "+N" | Sizes 22/24/28/40/64/72 | Stack is one node: "4 members" |
| `RoleTag` | Pill: Leader = accentContainer + crown 14 dp; Member = 1.5 dp textSecondary outline; Left = divider fill | — | Read-only |
| `SectionHeader` | heading + count (textSecondary); collapsible variant has a 22 dp chevron and is a full-width 48 dp button | Static / expanded / collapsed | `semantics { heading() }`; collapsible announces expanded/collapsed |
| `FilterChipRow` | Horizontally scrolling chips All / To Do / In progress / Done with counts; hidden scrollbar; 64 dp right fade to background with a 40 dp round arrow button (surface, outlineControl border, shadow) | Chip selected = accent fill, onAccent text, check icon; unselected = surface + outlineControl border. Arrow points right, rotates 180° at the end and scrolls back | Filters the already-loaded task list in memory (rememberSaveable, default All, resets when leaving). Chip = toggle with selected state; arrow label "More filters" / "Scroll filters back" |
| `Banner` | Icon 20 dp + body text, radius 12, padding 12/14; optional action button below | Info (surfaceTile), waiting (pending), error (errorSurface/error) | Icon decorative |
| `PrimaryButton` | Full width, 52 dp, pill, accent / onAccent, optional 20 dp icon | Default, pressed, disabled (divider fill, textSecondary) | Disabled create button shows helper text above |
| `TonalButton` | 48 dp pill, accentContainer / onAccentContainer, optional icon | — | — |
| `TextActionButton` | 48 dp, transparent, textPrimary, Bold | Destructive variant uses error | — |
| `LiverooFab` | Extended pill, 56 dp, accent, plus icon + label ("Group", "Task"), shadow | — | Same visibility rules as today |
| `LiverooTextField` | Outlined, 56 dp, radius 12, 1.5 dp outlineControl (2 dp accent focused, 2 dp error); floating label on the border; helper left + counter right below | Empty, filled, focused, error (icon + message) | Error announced; counter kept |
| `LiverooSelectField` | Same frame as text field with leading 28 dp avatar, value, chevron; Material ExposedDropdownMenu with the same options as today | Closed, open | — |
| `LiverooDialog` | Wraps M3 `AlertDialog`: surface, radius 24, optional 48 dp icon tile, dialogTitle, body (body, textSecondary), right-aligned text buttons; confirm in accent, destructive confirm in error | Confirm, destructive, with icon | onDismissRequest unchanged per dialog (sync/invite request: tap outside still declines) |
| `LiverooBottomSheet` | M3 `ModalBottomSheet`, surface, top radius 28, drag handle; rows with 40 dp accent tiles | — | Same two actions as today |
| `LiverooSnackbar` | Floating, radius 14, snackbar fill, check icon; placed by the Scaffold above the floating nav | — | Polite live region |
| `EmptyState` / `CenteredState` | 80–96 dp icon tile, title (dialogTitle or titleMedium; brandHeadline for success moments), body (bodyLarge, textSecondary), actions pinned 24 dp from bottom | Leader/member variants as today | — |
| `SyncSteps` | Card with 3 rows (48 dp): done = Done-fill circle + check; current = spinner; pending = dashed 28 dp circle | Connecting → Exchanging updates → Saving | List with progress semantics |
| `VisibilityTimerChip` | accentContainer pill, eye icon, "Visible to nearby phones · 1:58" | Counting / "No longer visible" | Existing countdown logic unchanged |

## Screen frame and navigation

`LiverooScaffold` keeps its current parameters (title, nav, showBack, tab, actions, floatingActionButton, content) so screen call sites barely change. What changes is how it draws.

**Top bars**

- Tab screens (Your groups, My Tasks, Sync idle): no back arrow; large title (titleLarge) left-aligned, 20 dp from the top inset; actions on the right (Your groups: Settings gear, 48 dp).
- Detail and form screens: 48 dp back button, title (titleMedium, wraps to 2 lines), actions on the right. Background = screen background, no divider.
- Group detail: back + ⋮ only in the bar; the group name, role and sync state sit in the content header below it.

**Floating nav**

- Drawn as an overlay at the bottom of the Scaffold (not `bottomBar`), 16 dp from the sides and 16 dp above the navigation-bar inset (`WindowInsets.navigationBars`).
- Shown on exactly the screens that show the bar today: Your groups, Group detail, Members, Task detail, My Tasks, Sync (including transfer mode), Sync result. Not shown on onboarding, Create group, Invite, Join, Transfer, Group settings, Task form, Settings.
- Content gets 120 dp bottom padding when the nav is shown, so the last row and the FAB never sit under it.
- Tab navigation code (popUpTo GROUPS, saveState, launchSingleTop, restoreState) is unchanged.

**FAB and bottom actions**

- FAB ("+ Group", "+ Task") sits 16 dp from the right and 96 dp from the bottom (above the nav). Visibility rules unchanged.
- Screens with a main action and no nav (Create group, Transfer, Task form, onboarding) pin it 24 dp from the bottom, above the keyboard (`imePadding`).
- Screens with a main action and the nav (Sync idle, Sync result) pin it 96 dp from the bottom.

**Snackbar:** one `SnackbarHost` in the Scaffold, positioned above the floating nav (fixes today's snackbar inside the scroll content).

**Edge-to-edge:** already enabled in `MainActivity`; status-bar icons follow the theme.

## Screens S01–S18

Each screen keeps its Composable name, route, parameters and logic. The hierarchy below is the new layout, top to bottom. Every state listed has a light and dark frame on the canvas (page in brackets: R1, R2, R3).

### Onboarding (no nav)

| Screen | Layout and component hierarchy | States and dialogs |
| --- | --- | --- |
| S01 Splash | Centered column: "Liveroo" brandDisplay in accent; tagline bodyLarge SemiBold textSecondary. Timing and routing logic unchanged | Default (R2) |
| S02 Permissions | 24 dp margins, 40 dp top: 64 dp accentContainer tile with Bluetooth icon → brandHeadline title → GroupedCard of 3 ListRows (accent tiles: phone, link, cloud-off; title + body) → (denied: error Banner). Bottom: PrimaryButton Allow/Try again + TextActionButton Skip for now | First ask (R2), denied once (R3) |
| S03 Bluetooth off | Centered: 112 dp pending tile (Bluetooth-off) → brandHeadline → bodyLarge. Bottom: PrimaryButton Turn on (Bluetooth icon) + Not now | Default (R2) |
| S04 Name setup | brandHeadline → 72 dp Avatar (live initials and colour, "?" when empty) → LiverooTextField "Display name" (helper + 0/30) → info Banner (database icon). Bottom: Continue | Filled (R2), empty-name error (R3) |

### Groups and members

| Screen | Layout and component hierarchy | States and dialogs |
| --- | --- | --- |
| S05 Your groups | titleLarge + gear → SavedOnPhoneCard (when changes waiting) → LazyColumn of GroupCards (12 dp gap). FAB "Group" opens LiverooBottomSheet (Create a group / Join a group). Nav: Groups | With groups (R1), empty: CenteredState with Create a group (primary) + Join a group (tonal) and no FAB (R3), add sheet (R3) |
| S06 Create group | Top bar → LiverooTextField "Group name" (Required, 0/40) → info Banner (crown). Bottom: Create; when empty, disabled with "Enter a group name to continue" above | Filled (R2), empty (R3) |
| S07 Group detail | Bar: back + ⋮ (Group settings) → header: titleMedium name, RoleTag + leader name, SyncStateChip → progress card (64 dp ring + StatusSummary) → row of TonalButtons: "Members · N" with AvatarStack (all) and "Invite" (leader only) → FilterChipRow → sections To Do / In progress / Done (Done collapsible, open by default), each a GroupedCard of TaskRows. FAB "Task" for leader. Nav: Groups | Leader (R1), member (R3), left read-only: pending Banner with "Go to Sync", no actions/FAB (R3), no tasks leader: EmptyState + Add task (R3), no tasks member: EmptyState + Go to Sync (R3) |
| S08 Members | Top bar with TonalButton "Invite" (leader) → caption "Group · N members" → GroupedCard of member rows (40 dp Avatar, name, Leader/You tags, counts caption, sync chip, chevron), tap expands to show their tasks as 48 dp rows (surfaceTile, StatusIndicator) → GroupedCard "Unassigned · N tasks have no assignee". Nav: Groups | One row expanded (R2) |
| S09 Invite members | Top bar → info Banner → status row (spinner + "Looking for nearby phones…" / "Search finished") → GroupedCard of device rows (avatar, name, "Nearby" / "Already in group" disabled, chevron) → caption → Scan again when finished | Scanning (R2); waiting, joined, declined, failed as CenteredState (R3) |
| S10 Join a group | Top bar → CenteredState: 96 dp tile, "Ready to join", body, card "Your phone appears as / Galaxy A14", VisibilityTimerChip | Waiting (R2); asking, invited dialog, joining (SyncSteps), joined (brandHeadline), visibility denied, timed out, failed (R3) |
| S11 Transfer leadership | Top bar → body "Choose who will lead …" → GroupedCard of radio rows (avatar, name, 22 dp radio) → info Banner. Bottom: Continue (disabled until one is selected) | Selected (R2), confirm dialog (R3) |
| S12 Group settings | Top bar → GroupedCard: Group name; Your role + RoleTag → GroupedCard: Transfer leadership row (leader; disabled "Invite a member first." when alone) + destructive row Leave group / Delete group from this phone → leader caption | Leader (R2), member, only member (R3); dialogs: Transfer leadership first, Leave group, Delete from this phone (R3) |

### Tasks

| Screen | Layout and component hierarchy | States and dialogs |
| --- | --- | --- |
| S13 Task detail | Bar: back + (leader) edit + ⋮ Delete → caption group name → titleMedium task title → "Status" label + PendingChip when unsynced → StatusSelector, or StatusIndicator + lock Banner ("Only Coleen or the leader can change this status.") → GroupedCard: Assignee (40 dp avatar + name) / Description (bodyLarge or "No description.") → caption with clock "Last updated by …" → snackbar "Status updated. Sync to share it." above nav. Nav: Groups | Can change (R1), can't change (R1 tweak), delete dialog, deleted (R3) |
| S14 Create / edit task | Top bar "New task" / "Edit task" → (warning/error Banners) → LiverooTextField Title (Required, n/60) → multi-line Description (Optional, n/500) → LiverooSelectField Assignee → "Status" + StatusSelector. Bottom: Save | Edit (R2), new with title error, assignee cleared (R3) |
| S18 My Tasks | titleLarge + caption subtitle → sections To Do / In progress / Done with GroupedCards of TaskRows (group name caption above title). Nav: My Tasks | With tasks (R2), empty (R3) |

### Sync and settings

| Screen | Layout and component hierarchy | States and dialogs |
| --- | --- | --- |
| S15 Sync idle | titleLarge → hero card (radius 24): 56 dp pending tile + "N changes not yet synced" + body → GroupedCard "Waiting to sync" with count pill, rows per group (users tile, name, "1 change" / "You left" in pending) → Bluetooth hint row. Bottom: Start Sync (sync icon). Nav: Sync | Waiting (R1), everything synced, Bluetooth off, permission needed (R3) |
| S15 Sync scanning / working | Status row (spinner or "Search finished") + VisibilityTimerChip or waiting Banner "You can still find others, but they can't find you." → "Groupmates" heading + GroupedCard of device rows (avatar, name, shared groups) or empty text → collapsible "Other nearby devices" (count, caption; open by default when no groupmates found) → Scan again / Cancel. Working: SyncSteps + Cancel | Groupmates found, none found, not visible, in progress (R3) |
| S15 Transfer mode | Top bar "Transfer to Coleen" → crown Banner → hint → Start Sync + Cancel transfer; in scanning, other people disabled with "Only Coleen can be selected" | Idle (R3) |
| S15 Dialogs | LiverooDialog "Coleen Dichoso wants to sync / Allow?" (Sync, Decline) and "… wants to make you the leader …" (crown, Become leader, Decline). Dismiss behaviour unchanged | Both (R3) |
| S16 Sync result | Top bar "Sync result" → hero card: 64 dp Done circle (check, or crown after a transfer), brandHeadline "Synced with Coleen" in accent, "Sent N updates · Received N updates" → "Changes" GroupedCard of note rows. Bottom (above nav): Done + Sync with someone else | Success (R2), transfer done, sync didn't finish, transfer didn't finish, declined, transfer declined (R3) |
| S17 Settings | Top bar → profile card (64 dp avatar, "Your name", name, TonalButton Edit, caption) → "Bluetooth" GroupedCard (Bluetooth On/Off, Nearby devices Allowed/Not allowed, each with check or cross icon) → info Banner → "About" GroupedCard | Normal (R2), Bluetooth off / not allowed, edit-name dialog (R3) |

## Accessibility requirements

Target: WCAG 2.1 AA and Android accessibility guidance, in light and dark. Each item is checked per screen in stage 5.

- [ ] Text contrast ≥ 4.5:1 (≥ 3:1 for text ≥ 24 sp); all token pairs above meet this.
- [ ] Control boundaries and selected states ≥ 3:1 (outlineControl, accent ring, filled chips).
- [ ] Every tappable element ≥ 48 × 48 dp (`minimumInteractiveComponentSize`), including icon buttons, chips, the filter arrow, Done header and expanded member task rows.
- [ ] Section headers use `semantics { heading() }`.
- [ ] Collapsible sections and member rows expose expanded/collapsed state (`stateDescription` + click label).
- [ ] Status, role and sync state always include text; never colour alone.
- [ ] Icons that carry meaning have `contentDescription`; decorative icons use `null`.
- [ ] Rows merge into one TalkBack item (`Modifier.semantics(mergeDescendants = true)`), e.g. "Write test cases for sync, Coleen, To Do, not yet synced".
- [ ] Floating nav items expose role Tab, selected state and the badge count.
- [ ] Status selector exposes a radio group; filter chips expose selected state.
- [ ] No fixed heights on text containers; layouts checked at 200% font size (titles wrap, segments grow).
- [ ] Bottom actions stay above the keyboard (`imePadding`); lists keep clearance above the nav.
- [ ] Focus order follows visual order; dialogs keep focus inside.
- [ ] Snackbars and scan/timer updates are polite live regions.
- [ ] Animations respect the system "Remove animations" setting.

## Approved copy changes

All other text stays exactly as it is in the code today. These are the only new or changed strings, all approved on the canvas.

| Where | Text |
| --- | --- |
| Sync idle hero, under "N changes not yet synced" | Saved on this phone. Sync with a groupmate to share them. |
| Your groups, sync card | Saved on this phone · N changes waiting to sync |
| Group cards and group header | "N change(s) waiting" / "1 change waiting to sync" (sync state chip) |
| Group settings (leader), caption | As the leader, you'll be asked to transfer leadership before you can leave. |
| Create group, empty field hint / disabled helper | e.g. CS312 Final Project · Enter a group name to continue |
| New task, description hint | Optional details |
| Sync, everything synced, body | You can still sync to get your groupmates' changes. |
| Sync in progress, heading | Syncing with {name} |
| Section labels | Sentence case instead of caps: Status, Assignee, Description, Group name, Your role, Bluetooth, About, Changes |
| Status label | "In progress" (lowercase p) as on the canvas — pending owner confirmation, see Questions |
| Filter arrow (TalkBack only) | More filters · Scroll filters back |

## File-change plan

About 14 files change and 6–8 are added, all under `app/src/main/java/com/group4/liveroo/ui/` and `app/src/main/res/`, plus one licence file and this document. Nothing is deleted.

| Stage | File | Change |
| --- | --- | --- |
| 2 Theme | `res/font/nunito_medium.ttf`, `nunito_semibold.ttf`, `nunito_bold.ttf`, `nunito_extrabold.ttf` | Added: Nunito as four static weights 500–800 (Google Fonts, OFL) |
| 2 Theme | `licenses/Nunito-OFL.txt` | Added: licence, next to the existing Comfortaa and Luckiest Guy licences |
| 2 Theme | `ui/theme/Color.kt` | Rewritten: light and dark token values, `LiverooColors` class, avatar palette, legacy aliases |
| 2 Theme | `ui/theme/Type.kt` | Rewritten: Nunito family, type styles above, brand styles; Comfortaa no longer referenced |
| 2 Theme | `ui/theme/Theme.kt` | Rewritten: light/dark `colorScheme`, `LocalLiverooColors`, shapes; dark mode now comes from `ThemePreference` (addendum 01) |
| 2 Theme | `ui/theme/Dimens.kt` | Added: spacing, sizes and radii |
| 2 Theme | `res/values-night/themes.xml` | Added in stage 2, then removed by the owner (addendum 01, A4) |
| 3 Components | `ui/components/Components.kt` | Rewrite: scaffold, rows, cards, chips, tags, buttons, banners, empty and centered states, avatars, rings |
| 3 Components | `ui/components/FloatingNav.kt` | Add: floating nav (replaces `LiverooBottomBar`; same navigation calls) |
| 3 Components | `ui/components/Inputs.kt` | Add: text field, select field, status selector, filter chip row |
| 4 Screens | `ui/screens/OnboardingScreens.kt` | Layout only (S01–S04) |
| 4 Screens | `ui/screens/GroupScreens.kt` | Layout only (S05–S08, S11, S12) + filter-chip state |
| 4 Screens | `ui/screens/TaskScreens.kt` | Layout only (S13, S14, S18) |
| 4 Screens | `ui/screens/BluetoothScreens.kt` | Layout only (S09, S10, S15, S16). Sync, invite and join logic copied unchanged |
| 4 Screens | `ui/screens/SettingsScreen.kt` | Layout only (S17) |
| 5 A11y | Same UI files | Semantics, touch targets, font-scale fixes |
| — | `docs/REDESIGN_HANDOFF.md` | Added: this document |

**Not touched**

- `data/`, `rules/`, `sync/`, `bluetooth/`, `LiverooApp.kt`, `MainActivity.kt`
- `ui/nav/Routes.kt`, `ui/nav/LiverooNavHost.kt` (routes and arguments stay identical)
- `ui/components/DenyMessages.kt` (refusal texts)
- `AndroidManifest.xml`, Gradle files, `app/schemas`, everything under `app/src/test`
- `res/font/comfortaa_variable.ttf` stays in the folder (unused) so no file is deleted
- `.git`, `.git-old`, `.gitignore`, `.idea`, `.gradle`

**Stage 6 audit:** run `git status` and `git diff --stat`, plus a check that lists any changed file outside `ui/`, `res/`, `licenses/` and `docs/REDESIGN_HANDOFF.md`. That list must be empty.

## Build notes

- Build with Gradle JDK 21 (Android Studio: Settings → Build Tools → Gradle → Gradle JDK). The bundled JDK 25 cannot run Gradle 8.9.

## Questions open with the project owner

- [ ] "In Progress" → "In progress": change the label everywhere, or keep the app's capital P?
- [ ] Move UI text into `strings.xml` during stage 4, or leave text where it is for now?
- [ ] Comfortaa font file: leave it in place, unused (current plan), or will the owner delete it after the redesign?
