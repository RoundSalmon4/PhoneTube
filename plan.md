# SmartTube Phone Port -- Implementation Plan

## Quick Start for New Assistant

**Repository:** `https://github.com/RoundSalmon4/SmartTube`
**Branch:** `phone-port` (reset to commit `f431d87d0`)
**Goal:** Port SmartTube (an Android TV YouTube client) into a YouTube app for phones
**Current state:** Basic phone UI works (browse, player, search, channels, sign-in, settings). Needs UX improvements to match modern phone YouTube apps.

### Before doing anything
1. Read this entire document
2. Run `git log --oneline -5` to verify HEAD is at `f431d87d0`
3. Read the key files listed in the "Key Files Map" section
4. **Do NOT try to build locally** -- there is no Android SDK. Code-only commits.
5. **Commits as roundsalmon4 / 209016228+RoundSalmon4@users.noreply.github.com**
6. **Never use the user's real name or personal info**
7. **All code/commits/comments in solo-dev style, not AI style**

---

## 1. Repository Architecture

SmartTube is a multi-module Android app (`app.smarttube`) using **MVP pattern** with a custom **ViewManager** navigation controller. The phone port creates parallel `Phone*Activity`/`Phone*Fragment` classes that implement the same View interfaces as the TV Leanback versions, sharing the same presenter/data layer.

### Module Structure

| Module | Purpose |
|---|---|
| `smarttubetv/` | Main app module (contains both TV and phone UI) |
| `common/` | Shared MVP presenters, views interfaces, ExoPlayer integration, data models |
| `MediaServiceCore/` | YouTube API integration (git submodule) |
| `leanback-1.0.0/` | Forked `androidx.leanback` (TV-only, phone port doesn't use) |
| `fragment-1.1.0/` | Forked `androidx.fragment` (global replacement) |
| `exoplayer-amzn-2.10.6/` | Amazon's ExoPlayer 2.10.6 fork + custom SABR protocol |
| `SharedModules/` | Utilities, helpers, shared prefs (git submodule) |
| `doubletapplayerview/` | Third-party double-tap-to-seek library |

### Key Build Constraints
- **Gradle 7.4.2**, Java 8 source/target, AGP 7.4.2
- **compileSdkVersion 34**, targetSdkVersion 27, minSdkVersion 17
- **ExoPlayer 2.10.6 (Amazon fork)** -- uses `PlayerView` (NOT `StyledPlayerView` which came in 2.12+)
- **RxJava 2**, Retrofit 2, OkHttp 3.12.13, Glide 4.11.0, Kotlin 1.8.10
- Root `build.gradle` globally EXCLUDES official `androidx.leanback` and `androidx.fragment`, replacing with local forks

### Critical Architecture: ViewManager

`ViewManager` is a singleton that maps View interfaces to Activity classes and manages a navigation stack.

**File:** `common/src/main/java/com/liskovsoft/smartyoutubetv2/common/app/views/ViewManager.java`

Key facts:
- Uses `Stack<Class<? extends Activity>>` to track navigation
- `startView(PlaybackView.class)` launches the mapped Activity via Intent with `FLAG_ACTIVITY_NEW_TASK`
- `startParentView(activity)` pops the stack and starts the parent Activity
- `blockTop(activity)` prevents an activity from being re-shown (used for PIP/background)
- `mRootActivity` is the launcher activity
- `mParentMapping` maps child→parent Activity classes

**Registration in `MainApplication.setupViewManager()`** (`smarttubetv/.../ui/main/MainApplication.java:85-98`):
```java
viewManager.setRoot(PhoneBrowseActivity.class);
viewManager.register(BrowseView.class, PhoneBrowseActivity.class);
viewManager.register(PlaybackView.class, PhonePlaybackActivity.class, PhoneBrowseActivity.class);
viewManager.register(AppDialogView.class, PhoneAppDialogActivity.class, PhoneBrowseActivity.class);
viewManager.register(SearchView.class, PhoneSearchActivity.class, PhoneBrowseActivity.class);
viewManager.register(SignInView.class, PhoneSignInActivity.class, PhoneBrowseActivity.class);
viewManager.register(AddDeviceView.class, PhoneAddDeviceActivity.class, PhoneBrowseActivity.class);
viewManager.register(ChannelView.class, PhoneChannelActivity.class, PhoneBrowseActivity.class);
viewManager.register(ChannelUploadsView.class, PhoneChannelUploadsActivity.class, PhoneBrowseActivity.class);
viewManager.register(WebBrowserView.class, WebBrowserActivity.class, PhoneBrowseActivity.class);
```

**CRITICAL CONSTRAINT:** ViewManager, PIP (`onPictureInPictureModeChanged`), and background playback (`onUserLeaveHint`, `blockTop()`) are all Activity-based. You CANNOT eliminate `PhonePlaybackActivity` without major ViewManager refactoring. The BottomSheetDialogFragment must live INSIDE the activity.

### Activity Inheritance

```
FragmentActivity
  -> MotherActivity (common -- DPI scaling, theme, locale, edge-slide)
      -> PhoneActivity (smarttubetv -- double-back exit, ViewManager integration)
          -> PhoneBrowseActivity, PhonePlaybackActivity, PhoneChannelActivity, etc.
```

**MotherActivity** (`common/.../misc/MotherActivity.java`): Base for all activities. Handles DPI scaling (normalizes to 1920px width), theme init, locale, screensaver, fullscreen, edge-slide swipe-back.

**PhoneActivity** (`smarttubetv/.../ui/common/PhoneActivity.java`): Base for all phone activities. Handles double-back-to-exit and `finishReally()` which calls `getViewManager().startParentView(this)`.

### MVP Pattern

- **Presenters** are singletons holding weak references to View interfaces
- **View interfaces** (`BrowseView`, `PlaybackView`, `SearchView`, etc.) in `common/.../app/views/`
- Presenters push data to views via direct method calls (NOT LiveData/observables)
- `PlaybackPresenter` is the central playback controller -- manages a chain of 11 sub-controllers (state, suggestions, loader, error fixer, UI, remote, sponsorblock, AFR, HQ dialog, chat, comments)

---

## 2. Key Files Map

### Phone UI Files (all in `smarttubetv/src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/phone/`)

| File | Lines | Purpose |
|---|---|---|
| `PhoneBrowseActivity.java` | 419 | Main browse screen, BottomNavigationView, routes section data to tab fragments |
| `PhoneBrowseFragment.java` | 475 | Home tab: vertical RecyclerView of horizontal video rows |
| `PhoneSubscriptionsFragment.java` | 203 | Subscriptions tab: 2-column grid |
| `PhoneLibraryFragment.java` | 203 | History/Library tab: 2-column grid (95% identical to Subscriptions) |
| `PhoneSettingsFragment.java` | 144 | Settings tab: vertical list |
| `PhonePlaybackActivity.java` | 192 | Player activity: PIP, background playback, touch forwarding |
| `PhonePlaybackFragment.java` | 1030 | Player UI: ExoPlayer, controls, double-tap, media session, player menu |
| `PhoneSearchActivity.java` | 37 | Search host activity |
| `PhoneSearchFragment.java` | 606 | Search: EditText + suggestions + results with channels separated |
| `PhoneChannelActivity.java` | 46 | Channel host activity |
| `PhoneChannelFragment.java` | 394 | Channel page: header + horizontal video rows |
| `PhoneChannelUploadsActivity.java` | 46 | Channel uploads host activity |
| `PhoneChannelUploadsFragment.java` | 249 | Channel uploads: vertical video list |
| `PhoneAppDialogActivity.java` | 74 | Settings dialog host (transparent, custom finish()) |
| `PhoneAppDialogFragment.java` | 169 | Settings dialog: PreferenceFragmentCompat |
| `PhoneSignInActivity.java` | 33 | Sign-in host activity |
| `PhoneSignInFragment.java` | 99 | Sign-in: device code display |
| `PhoneAddDeviceActivity.java` | 33 | Add-device host activity |
| `PhoneAddDeviceFragment.java` | 71 | Device pairing code display |

### Layout Files (in `smarttubetv/src/main/res/layout/`)

| Layout | Purpose |
|---|---|
| `fragment_phone_browse.xml` | FrameLayout container + BottomNavigationView |
| `fragment_phone_browse_content.xml` | Home tab: search bar + SwipeRefreshLayout + RecyclerView |
| `fragment_phone_tab_list.xml` | Reusable tab: SwipeRefreshLayout + RecyclerView + progress/empty |
| `fragment_phone_playback.xml` | Player: ExoPlayer PlayerView + custom controls overlay + YouTubeOverlay |
| `fragment_phone_search.xml` | Search: EditText + suggestion/results lists |
| `fragment_phone_channel.xml` | Channel: header + video rows |
| `fragment_phone_channel_uploads.xml` | Channel uploads: video list |
| `fragment_phone_signin.xml` | Sign-in: code + buttons |
| `fragment_phone_add_device.xml` | Add device: code + button |
| `activity_phone_*.xml` | Simple FrameLayout containers (7 files) |
| `item_phone_video_card.xml` | Video card: thumbnail + title + channel + views |
| `item_phone_section.xml` | Section row: title + horizontal RecyclerView |
| `item_phone_section_settings.xml` | Settings section: title + vertical RecyclerView |
| `item_phone_settings.xml` | Settings item: icon + title |
| `item_phone_channel_card.xml` | Channel card: circular avatar + name + subscribers |
| `item_phone_channel_row.xml` | Channel row: title + horizontal RecyclerView |
| `item_phone_search_group_header.xml` | Search group header |
| `item_phone_search_suggestion.xml` | Search suggestion row |

### Other Key Files

| File | Purpose |
|---|---|
| `smarttubetv/src/main/AndroidManifest.xml` | All activities registered with themes/orientations |
| `smarttubetv/src/main/res/values/styles.xml` | Phone themes (lines 252-315) |
| `smarttubetv/src/main/res/menu/menu_bottom_nav.xml` | Bottom nav: Home, Subscriptions, History, Settings |
| `smarttubetv/src/main/res/color/bottom_nav_colors.xml` | Selected/unselected nav colors |
| `common/.../app/views/ViewManager.java` | Navigation controller |
| `common/.../app/views/PlaybackView.java` | Player view interface (extends PlayerManager) |
| `common/.../app/views/BrowseView.java` | Browse view interface |
| `common/.../app/presenters/PlaybackPresenter.java` | Player logic controller (singleton) |
| `common/.../app/presenters/BrowsePresenter.java` | Browse logic (singleton, has `loadSectionData()` added by phone port) |
| `common/.../misc/MotherActivity.java` | Base activity for all activities |
| `smarttubetv/.../ui/common/PhoneActivity.java` | Base phone activity |
| `smarttubetv/.../ui/main/MainApplication.java` | App class, ViewManager setup, crash fixes |
| `exoplayer-amzn-2.10.6/library/ui/src/main/res/layout/exo_playback_control_view.xml` | Default ExoPlayer control layout (reference for custom layout) |
| `exoplayer-amzn-2.10.6/library/ui/src/main/java/.../PlayerView.java` | ExoPlayer PlayerView (supports `use_controller`, `controller_layout_id`) |
| `exoplayer-amzn-2.10.6/library/ui/src/main/java/.../PlayerControlView.java` | ExoPlayer PlayerControlView (customizable via layout) |

---

## 3. Phone Port -- What Changed vs Upstream

The phone-port branch has 54 commits (+5,945 lines, 54 files). Key changes:

### Manifest Changes
- `android.software.leanback` changed from `required="true"` to `required="false"`
- `android.hardware.touchscreen` added as `required="true"`
- 9 new phone activities registered with AppCompat themes and phone-appropriate orientations

### ViewManager Routing
All view interfaces route to `Phone*Activity` variants (see registration above).

### BrowsePresenter Modification
Added `loadSectionData(int sectionId)` method for parallel section loading (phone loads all tabs simultaneously vs TV's sequential focus model). Also added `appendLocalHistory()` to merge local video state history when not signed in.

### MotherActivity Modification
Added `initEdgeSlide()` for swipe-from-left-edge back navigation on touch devices.

---

## 4. Audit Findings -- Non-Standard Patterns

### Finding 1: Video Player Not Using BottomSheetDialogFragment
**File:** `PhonePlaybackFragment.java` (1030 lines), `PhonePlaybackActivity.java` (192 lines)
**Issue:** Player is a regular Fragment in a full Activity. On phones, users expect YouTube-like swipe-down-to-minimize behavior. Currently, back press either kills playback or enters PIP as a workaround.
**Reference:** NewPipe uses `BottomSheetBehavior` on the player container. LibreTube uses `MotionLayout` for mini↔full transitions.
**Constraint:** ViewManager requires Activity-based navigation. PIP needs Activity callbacks. Player must stay in an Activity, but the Fragment inside can be a `BottomSheetDialogFragment`.

### Finding 2: Player Menu Uses AlertDialog Instead of BottomSheet
**File:** `PhonePlaybackFragment.java:538-578`
**Issue:** `showPlayerMenu()` uses `android.app.AlertDialog` with `Theme_Material_Dialog`. This is non-standard for phone UX -- all reference apps use `BottomSheetDialog`/`BottomSheetDialogFragment` for player options.

### Finding 3: Custom Player Controls Instead of ExoPlayer Built-in
**File:** `fragment_phone_playback.xml`, `PhonePlaybackFragment.java`
**Issue:** The layout builds controls from scratch: manual `ImageButton` for play/pause/rewind/forward, raw `SeekBar` (not ExoPlayer's `DefaultTimeBar`), manual time formatting, 500ms `Handler` polling for seekbar updates. ~300 lines of code that ExoPlayer's `PlayerControlView` handles automatically.
**Fix:** Set `app:use_controller="true"` on `PlayerView` and provide a custom `controller_layout_id`. ExoPlayer handles seek bar, time display, buffered progress, auto-hide, and animations.

### Finding 4: `notifyDataSetChanged()` Everywhere
**Files:** All adapters in all phone fragments
**Issue:** Every adapter calls `notifyDataSetChanged()` on any data change. Kills RecyclerView performance and prevents animations. Should use `DiffUtil`.

### Finding 5: VideoCardListAdapter Duplicated 5 Times
**Files:** `PhoneBrowseFragment` (inner `VideoCardListAdapter`), `PhoneSubscriptionsFragment` (inner `VideoListAdapter`), `PhoneLibraryFragment` (inner `VideoListAdapter`), `PhoneChannelFragment` (inner `HorizontalVideoAdapter`), `PhoneSearchFragment` (inner `ResultsAdapter` with `VideoViewHolder`)
**Issue:** Same ViewHolder pattern (thumbnail + title + channel + views + Glide + click handlers) is copy-pasted. ~400 lines of duplication.

### Finding 6: Glide Loading Anti-Pattern
**Files:** Every adapter that loads images
**Issue:** Every adapter manually casts `holder.itemView.getContext()` to `Activity`, checks `isDestroyed()`, then calls `Glide.with(activity)`. Standard pattern is `Glide.with(holder.itemView)` -- Glide handles lifecycle automatically.

### Finding 7: No Mini-Player
**Issue:** When navigating away from the player, playback ends or enters PIP. No persistent mini-player like YouTube/NewPipe/LibreTube. This is a natural improvement once the player is a BottomSheetDialogFragment.

### Finding 8: `singleInstance` Launch Mode on All Phone Activities
**File:** `AndroidManifest.xml`
**Issue:** Every phone activity uses `launchMode="singleInstance"`, which puts each in its own task. This is a TV-ism. Prevents standard Android transitions and back stack. Should be `singleTop` or default.

### Finding 9: PhoneSubscriptionsFragment and PhoneLibraryFragment Are Nearly Identical
**Files:** `PhoneSubscriptionsFragment.java` (203 lines), `PhoneLibraryFragment.java` (203 lines)
**Issue:** 95% identical code. Same layout, same adapter, same grid manager, same click handling. Only difference: empty text ("No subscriptions" vs "No watch history"). Should be merged into one `PhoneVideoGridFragment`.

### Finding 10: No ViewBinding
**Issue:** All fragments/activities use `findViewById()`. ViewBinding would be type-safe and eliminate null risks. Low priority but worth noting.

---

## 5. Implementation Plan -- 4 Commits

### Commit 1: "phone: deduplicate video card adapters, fix Glide lifecycle, merge identical tab fragments"

**Scope:** Pure refactor, zero behavior change. ~800 lines of duplication removed.

**Changes:**

1. **Create `PhoneVideoCardAdapter.java`** (new file in `ui/phone/`)
   - Static inner `VideoViewHolder` class with: thumbnail (`ImageView`), title, channelName, viewsDate (all `TextView`)
   - `setVideos(List<Video>)` method
   - Constructor takes a click listener interface: `interface OnVideoClickListener { void onClick(Video video); void onLongClick(Video video); }`
   - `onBindViewHolder` uses `Glide.with(holder.itemView)` (NOT casting to Activity)
   - Also includes `ChannelViewHolder` for channel cards with `setItems(List<Object>)` that handles mixed video/channel lists

2. **Create `PhoneVideoGridFragment.java`** (new file, replaces both `PhoneSubscriptionsFragment` and `PhoneLibraryFragment`)
   - Extends `Fragment`
   - Uses `fragment_phone_tab_list.xml` layout
   - `GridLayoutManager(2)` for 2-column grid
   - Configurable empty message via `setEmptyMessage(String)`
   - Methods: `addSection(BrowseSection)`, `updateSection(VideoGroup)`, `clearSection()`, `showProgressBar(boolean)`, `showError(String)`
   - Uses `PhoneVideoCardAdapter` for the grid

3. **Update `PhoneBrowseFragment.java`**
   - Remove inner `VideoCardListAdapter` class
   - Import and use `PhoneVideoCardAdapter` instead
   - Keep the outer `PhoneSectionAdapter` (it's unique to the home feed's horizontal-row-of-cards layout)

4. **Update `PhoneChannelFragment.java`**
   - Remove inner `HorizontalVideoAdapter` class
   - Use `PhoneVideoCardAdapter` for horizontal video rows

5. **Update `PhoneSearchFragment.java`**
   - Fix all Glide calls to use `Glide.with(holder.itemView)`
   - The `ResultsAdapter` is complex enough (mixed video/channel/header types) to keep as a separate inner class, but use `PhoneVideoCardAdapter.VideoViewHolder` for the video items

6. **Update `PhoneBrowseActivity.java`**
   - Replace `PhoneSubscriptionsFragment` → `PhoneVideoGridFragment` (with subscriptions-specific setup)
   - Replace `PhoneLibraryFragment` → `PhoneVideoGridFragment` (with library-specific setup)
   - Update `restoreFragments()` to use the new fragment class tags
   - The two instances are distinguished by tag and empty message

7. **Delete `PhoneSubscriptionsFragment.java` and `PhoneLibraryFragment.java`**

**What to test on device:**
- [ ] Home feed loads with horizontal video rows, scrolling works
- [ ] Tapping a video card opens the player
- [ ] Long-pressing shows the context menu
- [ ] Subscriptions tab loads and displays 2-column grid (if signed in)
- [ ] Library/History tab loads and displays 2-column grid
- [ ] Channel page shows horizontal video rows
- [ ] Channel uploads shows vertical list
- [ ] Search results display correctly (video cards, channel cards, group headers)
- [ ] Swipe-to-refresh works on Home, Subscriptions, Library tabs
- [ ] Pull-to-refresh on Home refreshes the first visible section

---

### Commit 2: "phone: use ExoPlayer built-in controls, convert player menu to bottom sheet"

**Scope:** Player UI overhaul. Replaces hand-rolled controls with ExoPlayer's native `PlayerControlView`. Replaces `AlertDialog` menu with `BottomSheetDialogFragment`.

**Changes:**

1. **Create `res/layout/exo_phone_control_view.xml`** (new custom controller layout)
   - Based on `exoplayer-amzn-2.10.6/library/ui/src/main/res/layout/exo_playback_control_view.xml`
   - Top bar (LinearLayout horizontal, gravity center_vertical):
     - `@id/exo_back` (ImageButton, back arrow)
     - `@id/exo_title` (TextView, weight=1, white, 16sp, single line, ellipsize end)
     - `@id/exo_more` (ImageButton, three dots)
   - Center (LinearLayout horizontal, gravity center, match_parent height):
     - `@id/exo_prev` (ImageButton, previous)
     - `@id/exo_rew` (ImageButton, rewind)
     - `@id/exo_play` (ImageButton, play)
     - `@id/exo_pause` (ImageButton, pause)
     - `@id/exo_ffwd` (ImageButton, fast forward)
     - `@id/exo_next` (ImageButton, next)
   - Bottom bar (LinearLayout vertical):
     - SeekBar row: `@id/exo_position` (TextView) + `@id/exo_progress_placeholder` (View, weight=1) + `@id/exo_duration` (TextView)
   - Background: `#CC000000` (semi-transparent black)
   - Use standard ExoPlayer IDs so wiring is automatic

2. **Create `res/layout/fragment_phone_player_menu.xml`** (new bottom sheet menu layout)
   - `LinearLayout` vertical, match_parent width, wrap_content height
   - Background: `@color/shelf_background_dark`
   - Corner radius top: 16dp (via `BottomSheetDialog` default)
   - `RecyclerView` for menu items

3. **Create `res/layout/item_phone_player_menu_option.xml`** (new menu item layout)
   - `LinearLayout` horizontal, padding 16dp
   - `ImageView` (icon, 24dp)
   - `TextView` (title, 16sp, marginStart 16dp)

4. **Create `PhonePlayerMenuBottomSheet.java`** (new `BottomSheetDialogFragment`)
   - Takes a list of menu items (icon res + title string + click callback)
   - Uses `RecyclerView` with `LinearLayoutManager`
   - Item click invokes the callback and dismisses the sheet
   - Items: Speed, Subtitles, Zoom, PIP, Share, Info, Playlist Add, Playback Queue

5. **Update `fragment_phone_playback.xml`**
   - Change `app:use_controller="false"` to `app:use_controller="true"`
   - Add `app:controller_layout_id="@layout/exo_phone_control_view"`
   - Add `app:show_timeout="4000"` (4 second auto-hide)
   - Remove the entire `controls_container` FrameLayout (the manual controls)
   - Remove the manual `ProgressBar` (ExoPlayer handles buffering indicator)
   - Keep: `btn_back_persistent` (always-visible back button), `YouTubeOverlay`, `SubtitleView`

6. **Major simplification of `PhonePlaybackFragment.java`** (~net -200 lines)
   - **Remove:** `mSeekBar`, `mTimeCurrent`, `mTimeTotal`, `mBtnPlayPause`, `mBtnRewind`, `mBtnForward`, `mControlsContainer`, `mIsOverlayShown`, `mIsSeeking`, `CONTROLS_TIMEOUT_MS`, `SEEKBAR_MAX`, `mHideControlsRunnable`
   - **Remove methods:** `updateSeekbar()`, `updatePlayPauseIcon()`, `formatTime()`, `showControlsOverlay()`, `hideControlsOverlay()`, `scheduleHideControls()`
   - **Remove:** the manual `Handler.post()` periodic seekbar update runnable (lines 314-322)
   - **Remove:** the `SeekBar.OnSeekBarChangeListener` setup (lines 181-212)
   - **Remove:** manual click listeners for play/pause/rewind/forward buttons
   - **Add:** `mPlayerView.setControllerVisibilityListener(visibility -> ...)` for managing persistent back button visibility
   - **Add:** `mPlayerView.setControllerVisibilityListener()` to update title text when controls show
   - **Update `showPlayerMenu()`** to show `PhonePlayerMenuBottomSheet` instead of `AlertDialog`
   - **Keep:** `YouTubeOverlay` / double-tap setup (unchanged), media session, `ExoPlayerController` delegation, all `PlaybackView` interface methods

**What to test on device:**
- [ ] Player opens, ExoPlayer controls appear on tap
- [ ] Play/pause works (center button)
- [ ] Rewind 10s and forward 10s work
- [ ] Previous/Next buttons work (for playlists)
- [ ] Seek bar shows position, duration, and buffered progress (shaded region)
- [ ] Dragging seek bar seeks correctly
- [ ] Controls auto-hide after ~4 seconds
- [ ] Tapping player surface shows controls again
- [ ] "More" button opens bottom sheet menu with all options
- [ ] Speed selection: tap Speed in menu -> speed picker opens -> selection applies
- [ ] Subtitles: tap Subtitles -> subtitle picker opens -> subtitles toggle
- [ ] Zoom: tap Zoom -> zoom mode cycles
- [ ] PIP: tap PIP -> enters picture-in-picture
- [ ] Share: tap Share -> system share sheet opens
- [ ] Info: tap Info -> video info dialog shows
- [ ] Playlist Add: tap -> add to playlist works
- [ ] Playback Queue: tap -> queue shows
- [ ] Back button (persistent, top-left) closes player
- [ ] Double-tap left/right to seek still works
- [ ] Media notification shows with play/pause/skip controls

---

### Commit 3: "phone: convert player fragment to BottomSheetDialogFragment"

**Scope:** Architectural change. The player fragment becomes a `BottomSheetDialogFragment` that slides up from the bottom over the browse screen.

**IMPORTANT:** This does NOT remove `PhonePlaybackActivity`. ViewManager, PIP, and background playback require an Activity. Instead:
- `PhonePlaybackActivity` becomes a **transparent shell** (just hosts the dialog fragment)
- `PhonePlaybackFragment` extends `BottomSheetDialogFragment` instead of `Fragment`
- The visual effect is a bottom sheet sliding up from the bottom

**Changes:**

1. **Update `PhonePlaybackFragment.java`**
   - Change `extends Fragment` to `extends BottomSheetDialogFragment`
   - Add `onCreateDialog()`:
     ```java
     @Override
     public Dialog onCreateDialog(Bundle savedInstanceState) {
         BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
         dialog.setOnShowListener(d -> {
             BottomSheetBehavior behavior = BottomSheetBehavior.from(
                 (View) dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet));
             behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
             behavior.setHideable(true);
             behavior.setPeekHeight(0);
             // Swipe down dismisses the player
             behavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                 @Override
                 public void onStateChanged(@NonNull View bottomSheet, int newState) {
                     if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                         dismiss();
                         if (getActivity() != null) getActivity().finish();
                     }
                 }
                 @Override
                 public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
             });
         });
         return dialog;
     }
     ```
   - Override `getTheme()` to return a transparent-dialog theme that allows full-screen expansion
   - Move `onCreateView()` content into the dialog's content view
   - Update `finish()` and `finishReally()` to call `dismissAllowingStateLoss()` in addition to activity finish
   - `onDismiss()` triggers activity finish

2. **Update `PhonePlaybackActivity.java`**
   - `initTheme()` sets a transparent/translucent theme:
     ```java
     @Override
     protected void initTheme() {
         setTheme(R.style.App_Theme_Phone_Player_Transparent);
     }
     ```
   - `onCreate()` creates `PhonePlaybackFragment` and shows it via `getSupportFragmentManager().beginTransaction().add(...)` (fragment will create its own dialog)
   - Keep all PIP logic unchanged (it's Activity-based and must stay)
   - Keep `onUserLeaveHint()`, `dispatchTouchEvent()`, `finish()` overrides

3. **Add transparent player theme** to `res/values/styles.xml`:
   ```xml
   <style name="App.Theme.Phone.Player.Transparent" parent="App.Theme.Phone.Player">
       <item name="android:windowIsTranslucent">true</item>
       <item name="android:windowBackground">@android:color/transparent</item>
       <item name="android:windowNoTitle">true</item>
       <item name="android:backgroundDimEnabled">false</item>
   </style>
   ```

4. **Update `res/layout/activity_phone_playback.xml`**
   - Change root to a simple transparent `FrameLayout` (no visual content -- the dialog fragment provides everything)

**What to test on device:**
- [ ] Tapping a video opens the player with bottom-sheet slide-up animation
- [ ] Player is full-screen when expanded (not peeking)
- [ ] Swiping down on the player dismisses it and returns to browse
- [ ] Back button works to close player
- [ ] PIP: pressing Home enters PIP mode (player minimizes to floating window)
- [ ] PIP: tapping PIP button enters PIP mode
- [ ] Background playback: pressing Home with sound-only background keeps audio playing
- [ ] Returning to app from background resumes player correctly
- [ ] `PhonePlaybackActivity` is properly cleaned up (no leaked activities in recents)
- [ ] Search, channel, and other screens still work (ViewManager not broken)
- [ ] Player menu bottom sheet still works on top of the player bottom sheet
- [ ] Double-tap to seek still works

---

### Commit 4: "phone: add mini-player bar, replace notifyDataSetChanged with DiffUtil, clean up launch modes"

**Scope:** Adds a persistent mini-player in the browse screen when playback is backgrounded. Replaces `notifyDataSetChanged()` with `DiffUtil`. Cleans up manifest launch modes.

**Changes:**

1. **Add mini-player bar to `PhoneBrowseActivity`**
   - New layout: `res/layout/mini_player_bar.xml`
     - `LinearLayout` horizontal, height ~64dp, background dark
     - `ImageView` (thumbnail, 64x36dp, centerCrop)
     - `TextView` (video title, weight=1, single line)
     - `ImageButton` (play/pause)
     - `ImageButton` (close)
   - Add this bar to `res/layout/fragment_phone_browse.xml` ABOVE the `BottomNavigationView`
   - Default visibility: `GONE`
   - Show/hide based on `PlaybackPresenter.instance(this).isRunningInBackground()`

2. **Update `PhoneBrowseActivity.java`**
   - In `onResume()`, check if playback is running in background:
     ```java
     boolean bgPlaying = PlaybackPresenter.instance(this).isRunningInBackground();
     mMiniPlayer.setVisibility(bgPlaying ? View.VISIBLE : View.GONE);
     if (bgPlaying) {
         Video video = PlaybackPresenter.instance(this).getVideo();
         if (video != null) {
             mMiniPlayerTitle.setText(video.getTitle());
             Glide.with(mMiniPlayerThumbnail).load(video.getCardImageUrl()).into(mMiniPlayerThumbnail);
         }
         mMiniPlayerPlayPause.setOnClickListener(v -> {
             PlaybackPresenter presenter = PlaybackPresenter.instance(this);
             if (presenter.isPlaying()) {
                 presenter.onPauseClicked();
             } else {
                 presenter.onPlayClicked();
             }
             updateMiniPlayerIcon();
         });
         mMiniPlayerClose.setOnClickListener(v -> {
             PlaybackPresenter.instance(this).onCloseClicked();
         });
     }
     ```
   - Tap on mini-player bar → opens full player: `getViewManager().startView(PlaybackView.class)`

3. **Add `DiffUtil.ItemCallback` to `PhoneVideoCardAdapter`**
   - Implement `areItemsTheSame(Video a, Video b)` using `a.getVideoId().equals(b.getVideoId())`
   - Implement `areContentsTheSame(Video a, Video b)` comparing title, author, imageUrl, views
   - Change `setVideos()` to compute diff and dispatch: `DiffUtil.calculateDiff(callback).dispatchUpdatesTo(this)`

4. **Update manifest launch modes** in `smarttubetv/src/main/AndroidManifest.xml`
   - Change `android:launchMode="singleInstance"` to `android:launchMode="singleTop"` for all phone activities:
     - `PhoneBrowseActivity`
     - `PhonePlaybackActivity`
     - `PhoneSearchActivity`
     - `PhoneChannelActivity`
     - `PhoneChannelUploadsActivity`
     - `PhoneAppDialogActivity`
     - `PhoneSignInActivity`
     - `PhoneAddDeviceActivity`
   - Keep `singleInstance` only for TV activities (BrowseActivity, PlaybackActivity)

**What to test on device:**
- [ ] Start a video, press Home → mini-player bar appears in browse screen
- [ ] Mini-player shows thumbnail, title, play/pause, close button
- [ ] Play/pause on mini-player toggles playback
- [ ] Close on mini-player stops playback and hides bar
- [ ] Tap on mini-player bar → opens full player
- [ ] Resume from background → mini-player disappears, bar hidden
- [ ] Video list updates show smooth animations (items slide in/out instead of full refresh)
- [ ] Navigate back to browse after playing → correct activity stack
- [ ] Search → back → browse still works correctly
- [ ] Channel → back → browse still works correctly
- [ ] Deep link (YouTube URL) still opens player correctly

---

## 6. Reference App Patterns

### NewPipe (`github.com/TeamNewPipe/NewPipe/`)
- Single-activity architecture with `MainActivity` hosting fragments
- `VideoDetailFragment` in a container with `BottomSheetBehavior` (NOT `BottomSheetDialogFragment`)
- `PlayerHolder` singleton manages `PlayerService` (extends `MediaBrowserServiceCompat`)
- `LocalBinder` pattern for direct service access
- RxJava for reactive data streams

### LibreTube (`github.com/libre-tube/LibreTube`)
- MVVM with `ViewModels` and `by viewModels()` delegation
- `PlayerFragment` + `CustomExoPlayerView` for rendering
- MotionLayout for mini-player ↔ full-screen transitions
- Service-based playback (`OnlinePlayerService`/`OfflinePlayerService`)
- `MediaController` pattern for UI-to-service communication
- ViewBinding throughout

### Grayjay (`github.com/futo-org/grayjay-android`)
- `VideoDetailView` extends `ConstraintLayout` (custom view pattern)
- `StatePlayer` singleton for player state management
- Plugin-based architecture with JavaScript sources
- LifecycleScope coroutines for async operations

---

## 7. Git / Commit Conventions

- **Author:** `roundsalmon4 <209016228+RoundSalmon4@users.noreply.github.com>`
- **Never commit with user's real name**
- **All code comments, commit messages, changelog entries** should read like a solo developer wrote them, not AI
  - Bad: "Refactored the VideoCardViewHolder to utilize a shared adapter pattern for improved code maintainability"
  - Good: "pulled out the video card view holder into a shared adapter. was tired of copy-pasting it between 5 fragments"
- **Commit messages** use prefix: `phone:` for phone-specific changes
- **Always provide commit summary and get go-ahead before committing**
- **Push via PAT** (already configured in remote `fork`)
- **Do not build locally** -- no Android SDK available

---

## 8. Session State

- **Repo location:** `C:\Users\cwekselblatt\Downloads\OpenCode\Smarttube`
- **Branch:** `phone-port`
- **HEAD:** `f431d87d0` ("phone: fix player menu dialog and local history merge")
- **Remote:** `fork` points to `https://github.com/RoundSalmon4/SmartTube.git` with PAT auth
- **Remote:** `origin` points to same URL without PAT
- **Working tree:** Clean (only submodule content modified)
- **No local changes pending**
