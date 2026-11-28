# Liveroo Redesign — Addendum 01: In-app dark mode and screen transitions

Date: 2026-11-28 · Approved by the project owner in chat.
This addendum extends `docs/REDESIGN_HANDOFF.md`. Where they disagree, **this addendum wins** for the topics below.

All the handoff's rules still apply: presentation layer only; no changes to `data/`, `rules/`, `sync/`, `bluetooth/`, `LiverooApp.kt`, `MainActivity.kt`, Gradle files, Room schemas or tests; routes and navigation logic stay identical; no Git commands that change anything (the owner handles Git).

---

## Part A — In-app dark mode (replaces "follows the system setting")

### A1. Behaviour

| Rule | Detail |
| --- | --- |
| Independent of the phone | The app **ignores** the phone's system dark-mode setting. Remove every use of `isSystemInDarkTheme()`. |
| Default | **Light**, on first install and whenever no choice has been saved. |
| Control | A single **"Dark mode" switch** in Settings (S17). On = dark theme, Off = light theme. |
| Applies | Immediately when toggled, on every screen, without restarting the app. |
| Remembered | The choice is saved and restored on next launch. |
| Splash (S01) | The Liveroo splash is dark only when the user has switched Dark mode on in the app. It follows `LiverooTheme` automatically, so no special code is needed. |
| Launch frame (known limitation, accepted) | Before the splash, Android shows its own launch frame drawn from the app theme (cream). Users with Dark mode on see a brief cream frame first. This is accepted; do **not** change `MainActivity` or the manifest to work around it. |

### A2. Saved setting (UI-only)

- New file: `ui/theme/ThemePreference.kt`.
- Storage: a **separate** `SharedPreferences` file named `liveroo_ui_prefs`, key `dark_mode` (Boolean, default `false`).
- Do **not** use or modify `data/prefs/IdentityStore.kt`, Room, the repository, or anything that syncs. This value never leaves the phone and is never sent to groupmates.
- Expose it as Compose state, read synchronously on first composition so the first frame already uses the right theme. Suggested shape:

```kotlin
// ui/theme/ThemePreference.kt
object ThemePreference {
    private const val FILE = "liveroo_ui_prefs"
    private const val KEY = "dark_mode"
    private val state = mutableStateOf<Boolean?>(null)

    fun isDark(context: Context): Boolean =
        state.value ?: context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY, false).also { state.value = it }

    fun setDark(context: Context, dark: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, dark).apply()
        state.value = dark
    }
}

@Composable
fun rememberDarkMode(): Boolean {
    val context = LocalContext.current
    return ThemePreference.isDark(context) // reads the snapshot state, so toggling recomposes
}
```

### A3. Theme wiring (`ui/theme/Theme.kt`)

- Keep the `LiverooTheme { }` call signature so `MainActivity` needs no change: `fun LiverooTheme(darkTheme: Boolean = rememberDarkMode(), content: @Composable () -> Unit)`.
- Remove the `isSystemInDarkTheme` import and use.
- **System bar icons** must follow the in-app setting (dark icons on light, light icons on dark). `MainActivity`'s `enableEdgeToEdge()` picks icon colours from the *system* setting, so override them from the theme:

```kotlin
val view = LocalView.current
if (!view.isInEditMode) {
    SideEffect {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}
```

### A4. Window theme resources

- `res/values-night/themes.xml` (added in stage 2) made the launch window dark when the *phone* is in dark mode. That contradicts A1, so it must be removed. **The owner deletes it** (`git rm app/src/main/res/values-night/themes.xml`). Do not delete it yourself; if it still exists when you start, stop and remind the owner.
- `res/values/themes.xml` stays as it is (cream window).

### A5. Settings UI (S17)

Add an **"Appearance"** section between the profile card and the "Bluetooth" section. It uses the existing components and tokens.

```
Appearance                              ← section label (label style, textSecondary)
┌────────────────────────────────────────────┐
│ [moon tile]  Dark mode             [ ◯━━ ] │  ← GroupedCard, one row, min 64 dp
│              Use a dark theme in Liveroo   │
└────────────────────────────────────────────┘
```

| Part | Spec |
| --- | --- |
| Section label | "Appearance" (label: Nunito Bold 15/20, textSecondary), 8 dp above the card |
| Card | `GroupedCard` (surface, radius 20) with one row |
| Leading | 40 dp icon tile (surfaceTile, radius 12) with `Icons.Rounded.DarkMode`, 20 dp, textPrimary |
| Title / subtitle | "Dark mode" (itemTitle) / "Use a dark theme in Liveroo" (caption, textSecondary) |
| Trailing | Material 3 `Switch`. Checked: track accent, thumb onAccent. Unchecked: track surfaceTile, thumb outlineControl, border outlineControl |
| Interaction | The **whole row** toggles (`Modifier.toggleable(value, role = Role.Switch, onValueChange = …)`); the Switch's own `onCheckedChange = null`. Tapping calls `ThemePreference.setDark(context, it)`. Applies instantly |
| Accessibility | One TalkBack node: "Dark mode, Use a dark theme in Liveroo, switch, on/off". Row ≥ 48 dp tall |
| New copy | "Appearance", "Dark mode", "Use a dark theme in Liveroo" |

---

## Part B — Screen transitions

### B1. Rules (approved)

| Navigation | Transition | Timing |
| --- | --- | --- |
| **Between tab roots**: Groups (`groups`) ↔ My Tasks (`my_tasks`) ↔ Sync (`sync?…`) | **Horizontal slide** in tab order. Index: Groups 0, My Tasks 1, Sync 2. Going to a higher index: new screen enters from the right, old exits to the left; lower index: the reverse. Slide full width, no fade. The floating nav **does not move** (see B2) | 250 ms, `FastOutSlowInEasing` |
| **Opening deeper screens** (group detail, members, task detail, task form, create group, invite, join, transfer, group settings, settings) and Back from them | **Fade + slight zoom**: enter = fadeIn + scaleIn(initialScale 0.96); exit = fadeOut. Back: popEnter = fadeIn; popExit = fadeOut + scaleOut(targetScale 0.96) | 200 ms, `FastOutSlowInEasing` |
| **Onboarding** (`splash` → `permissions` → `bt_off` → `name` → `groups`) | Crossfade | 200 ms |
| **Sync flow** (`sync` ↔ `sync_result`) | Crossfade | 150 ms |
| **Dialogs and bottom sheets** | Unchanged (Material defaults) | — |
| **In-screen** (Done section, member rows, filter arrow) | Unchanged (as already implemented) | — |

- Do **not** slide anything except tab-root ↔ tab-root.
- A tab tap from a deeper screen (e.g. from Task detail to My Tasks) counts as a tab switch only if both the source and target are tab roots; otherwise use the fade rule.
- "Remove animations" (system animator scale 0) must make all transitions instant. Compose follows the system animation scale; confirm on a device and, if it doesn't, return `EnterTransition.None` / `ExitTransition.None` when `Settings.Global.ANIMATOR_DURATION_SCALE` is 0.

### B2. Hoisting the floating nav (required for B1)

So the nav stays still while tab content slides, draw it **once, above the NavHost**, instead of inside each screen.

- In `ui/nav/LiverooNavHost.kt`: wrap the `NavHost` in a `Box`. Observe `nav.currentBackStackEntryAsState()`. Overlay `LiverooFloatingNav` at the bottom (16 dp side inset, 16 dp above `WindowInsets.navigationBars`), shown with `AnimatedVisibility` (fade, 150 ms).
- **Visible on exactly the same routes as today:** `groups`, `group/{groupId}`, `members/{groupId}`, `task/{taskId}`, `my_tasks`, `sync?…`, `sync_result`. Hidden everywhere else.
- **Active tab mapping (same as today):** `groups`, `group/…`, `members/…`, `task/…` → Groups; `my_tasks` → My Tasks; `sync?…`, `sync_result` → Sync.
- **Tab click behaviour unchanged:** `navigate(route) { popUpTo(Routes.GROUPS) { saveState = true }; launchSingleTop = true; restoreState = true }`.
- **Badge:** the same unsynced-count flow the nav uses today.
- In `LiverooScaffold` (`ui/components/Components.kt`): stop drawing the nav. Keep the `tab` parameter (or an equivalent flag) so screens that show the nav still get the 120 dp bottom content padding and the FAB stays 96 dp from the bottom. Snackbars must still sit above the nav.
- Routes, arguments and screen Composables' logic stay identical.

---

## Files

| File | Change |
| --- | --- |
| `ui/theme/ThemePreference.kt` | **Add** (A2) |
| `ui/theme/Theme.kt` | Edit (A3) |
| `ui/screens/SettingsScreen.kt` | Edit: Appearance section (A5). Nothing else on the screen changes |
| `ui/nav/LiverooNavHost.kt` | Edit: transitions (B1) + nav overlay (B2). Routes unchanged |
| `ui/components/Components.kt` | Edit: `LiverooScaffold` no longer draws the nav (B2) |
| `ui/components/FloatingNav.kt` | Edit only if needed to be callable from the NavHost |
| `docs/REDESIGN_HANDOFF.md` | Edit: theme section now says "in-app setting, default light" and points to this addendum |
| `res/values-night/themes.xml` | **Deleted by the owner** (A4) |

**Not touched:** `MainActivity.kt`, `AndroidManifest.xml`, `res/values/themes.xml`, `data/` (incl. `data/prefs`), `rules/`, `sync/`, `bluetooth/`, `LiverooApp.kt`, Gradle files, schemas, tests.

## Acceptance checks (owner tests in Android Studio)

1. Fresh install: app is light, even with the phone in dark mode.
2. Settings → Dark mode on: every screen turns dark immediately; status-bar icons turn light.
3. Close and reopen: app opens dark (after the brief cream launch frame); the Liveroo splash is dark.
4. Dark mode off: light again, including the splash on next launch.
5. Groups → My Tasks → Sync: content slides left; back the other way slides right; the floating nav stays still.
6. Opening a group or task: fade + slight zoom, no slide. Back reverses it.
7. Onboarding and Sync → Sync result: crossfade.
8. Floating nav appears on exactly the same screens as before; tab taps behave as before; badge still shows waiting changes.
9. "Remove animations" on: no transitions.
10. `:app:assembleDebug` and `:app:testDebugUnitTest` succeed; `git status` shows no changes outside `ui/`, `docs/` (and the owner's `values-night` deletion).
