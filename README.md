# PhoneTube

A YouTube phone app built on [SmartTube](https://github.com/yuliskov/SmartTube)'s MediaServiceCore.

> **Note:** This is a personal project and is not intended for general use.

## Features

| Feature | Description |
|---------|-------------|
| Home Feed | Browse YouTube recommendations, subscriptions, trending, music, sports, live, news, gaming, and kids content |
| Search | Search YouTube with autocomplete suggestions and configurable result limits. Long press video to add to playlist or go to channel. Channel results include subscribe button |
| Video Playback | Play videos with DASH and HLS streaming, quality picker, subtitle support, and audio track selection |
| Background Play | Continue listening with a persistent notification (with media controls) and mini player controls. Playback stops when the app is swiped away from recents |
| Picture-in-Picture | Floating video window when leaving the player during playback (Android 8+) |
| Mini Player | Persistent playback bar with play/pause, rewind, forward, close, and progress bar |
| Continue Playing | Automatically plays the next suggested video when one ends (optional) |
| Description | Expand/collapse video description with clickable timestamps and URL links |
| SponsorBlock | Skip sponsor segments and other interruptions automatically with per-category skip/toast/none controls |
| Local Playlists | Create and manage playlists without a Google account, add from home feed, player screen, or watch history. Drag-to-reorder videos within a playlist |
| YouTube Playlist Viewer | Browse YouTube playlists from search, channel pages, or playlist links, with Play All and save-to-library. Loads the full playlist, not just the first page |
| Saved Playlist Indicator | Playlists already in your library show a "Saved" state. Save again for a duplicate with a configurable confirmation prompt |
| Import/Export | Backup and restore settings, playlists, and subscriptions to/from JSON files |
| Local Subscriptions | Subscribe to channels locally. Latest videos appear on the home feed. Subscribe directly from search results |
| Watch History | Your watch history is saved locally with resume position, playback speed, and progress indicators |
| Channel Pages | Browse channel videos and playlists, save them locally, and subscribe |
| Deep Linking | Open YouTube video, playlist (into the playlist viewer), channel, and shorts links, plus Streamable video links, directly in PhoneTube |
| Streamable Video | Open `streamable.com` links and play them in the in-app player |
| Video Card Dates | Video cards show when the video was published across home feeds, search, and channel pages |
| Speed Control | Adjust playback speed from 0.25x to 3.0x, persisted across videos |
| Quality Picker | Choose video quality from available formats with current resolution shown |
| Audio Track Picker | Select between available audio tracks when multiple are present |
| Subtitles | Toggle and select subtitle tracks; captions render over the video with size-adaptive text |
| Feed Toggle | Enable or disable individual feed sources |
| Feed Order | Drag-to-reorder feed sections in settings |
| Feed Cache | Feed data is cached with watch progress for instant loading on return |
| Open Links | Choose between in-app WebView or system browser for opening links |
| Customization | Choose theme colors, enable AMOLED dark mode, or use your wallpaper colors on Android 12+. Picture-in-Picture and landscape lock toggles |
| Settings | Configure playback defaults, SponsorBlock categories, search limits, feed order, link opening mode, duplicate playlist warning, and data import/export |

## Installation

PhoneTube is not available on the Google Play Store. Download the latest APK from [GitHub Releases](https://github.com/RoundSalmon4/PhoneTube/releases/latest).

1. Download the `arm64-v8a` APK (most modern phones)
2. Open the APK file to install
3. You may need to enable "Install from unknown sources" in your device settings

### Verifying the APK

Verify the APK was signed with the correct certificate:

```bash
# Linux/macOS
apksigner verify --print-certs phonetube.apk | grep SHA-256

# Windows (PowerShell)
apksigner verify --print-certs phonetube.apk | Select-String "SHA-256"
```

Expected SHA-256 certificate fingerprint:
```
91:1E:92:BB:BC:1A:58:65:8C:0A:8D:2B:D5:E8:CF:F0:71:00:01:C8:32:9B:DB:AB:B6:22:16:D3:E9:10:4D:1F
```

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.

PhoneTube uses [SmartTube](https://github.com/yuliskov/SmartTube)'s MediaServiceCore as its YouTube data engine, and [Nuvio Mobile](https://github.com/NuvioMedia/NuvioMobile) as a design reference for the player.
