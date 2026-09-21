# PhoneTube

A YouTube phone app built on [SmartTube](https://github.com/yuliskov/SmartTube)'s MediaServiceCore.

> **Note:** This is a personal project and is not intended for general use.

## Features

| Feature | Description |
|---------|-------------|
| Home Feed | Browse YouTube recommendations, subscriptions, trending, music, sports, live, news, gaming, and kids content |
| Search | Search YouTube with autocomplete suggestions and configurable result limits. Long press video to add to playlist or go to channel. Channel results include subscribe button |
| Video Playback | Play videos with DASH and HLS streaming, quality picker, subtitle support, and audio track selection. View count, like count, and subscriber count are shown below the title. Keeps the screen awake while playing |
| Cast to TV (PhoneTV) | Cast to the companion [PhoneTV](https://github.com/RoundSalmon4/PhoneTV) receiver over a direct local connection. While casting, the phone works as a remote/navigator: playing another video, or changing speed, quality, captions, chapters, or volume mirrors to the TV. SponsorBlock skips are applied on the TV and their notice is shown on the TV's screen. Opening a casted video again resumes at the TV position; the TV's Back button ends the cast and playback picks back up on the phone. Connect attempts retry automatically when the network path is flaky |
| Background Play | Continue listening with a persistent notification (with Previous, Play/Pause, and Next controls) and mini player controls. Playback stops when the app is swiped away from recents |
| Picture-in-Picture | Floating video window when leaving the player during playback (Android 8+), with a dedicated PiP button in the player controls and reliable auto-entry when leaving the app mid-playback |
| Mini Player | Persistent playback bar with play/pause, rewind, forward, close, and progress bar |
| Continue Playing | Automatically plays the next suggested video when one ends (optional) |
| Description | Expand/collapse video description with clickable timestamps and URL links |
| Video Chapters | Timestamped chapters parsed from the description. Chapter chips seek directly, a Chapters list highlights the current chapter, and the playbar is segmented with chapter boundaries |
| SponsorBlock | Skip sponsor segments and other interruptions automatically with per-category skip/toast/none controls. Skip notices also appear on the TV while casting |
| Local Playlists | Create and manage playlists without a Google account, add from home feed, player screen, or watch history. Drag-to-reorder videos within a playlist |
| YouTube Playlist Viewer | Browse YouTube playlists from search, channel pages, or playlist links, with Play All and save-to-library. Play All queues the full playlist and advances through it automatically |
| Saved Playlist Indicator | Playlists already in your library show a "Saved" state. Save again for a duplicate with a configurable confirmation prompt |
| Import/Export | Backup and restore settings, playlists, subscriptions, PeerTube instances, IPTV providers, IPTV favorites, and cast devices to/from JSON files |
| Local Subscriptions | Subscribe to channels locally. Latest videos appear on the home feed. Subscribe directly from search results |
| Watch History | Your watch history is saved locally with resume position, playback speed, and progress indicators |
| Channel Pages | Browse channel videos and playlists with subscriber count display, save them locally, and subscribe |
| Deep Linking | Open YouTube video, playlist (into the playlist viewer), channel, handle (`/@handle`), and shorts links, plus bare-host links such as `m.youtube.com/?v=...`, Reddit video (v.redd.it), and Streamable links, directly in PhoneTube. Supports `vnd.youtube:` URI scheme |
| Streamable Video | Open `streamable.com` links and play them in the in-app player |
| PeerTube Support | Add PeerTube instances in Settings (e.g. neat.tube). The add dialog verifies the server exposes a valid PeerTube API before saving. Configured instances provide a PeerTube home-feed section and search results with a source badge showing which instance each result came from. Channel pages are browsable for federated channels, and PeerTube channels can be subscribed to so their latest videos appear in the subscriptions feed (channels hosted on disabled instances are hidden) |
| IPTV (Xtream Codes) | Add Xtream Codes providers (server, username, password) from the IPTV tab. Browse live channels by category in a readable list with a LIVE badge. HTTP and HTTPS providers are supported; the working scheme is stored per provider |
| IPTV Channel Search | Type in the search box to filter live channels across the whole provider by name. Channels are matched against a locally cached list that loads instantly, so search stays fast even for large providers |
| IPTV Now Playing | Shows the currently airing program on each channel using the short EPG endpoint (with a loading indicator while it fetches). Program end times are computed using the provider's stored server timezone |
| IPTV Favorites | Star channels to save them to a Favorites list. Favorites are stored locally and included in Import/Export |
| IPTV Playback | Live channels play via HLS through the existing player. Live playback always runs at 1x (speed controls are hidden) and keeps the screen awake while playing |
| Video Card Dates | Video cards show when the video was published across home feeds, search, and channel pages |
| Speed Control | Adjust playback speed from 0.25x to 3.0x. The Default Speed setting applies to every video; changing speed in the player lasts only for that video |
| Volume & Mute | Per-video volume slider with a mute toggle in the player controls |
| Quality Picker | Choose video quality from available formats with current resolution shown |
| Smart Quality (AUTO) | AUTO starts from a realistic bandwidth estimate, enforces a 360p floor, and uses tuned adaptive thresholds so quality stays stable across the video. A specific Default Quality setting is honored reliably on both DASH and HLS streams |
| Audio Track Picker | Select between available audio tracks when multiple are present |
| Subtitles | Toggle and select subtitle tracks; captions render over the video with size-adaptive text |
| Feed Toggle | Enable or disable individual feed sources |
| Feed Order | Drag-to-reorder feed sections in settings |
| Feed Cache | Feed data is cached with watch progress for instant loading on return |
| Manual Refresh | Pull down on the home feed to reload every feed section from the source |
| Open Links | Choose between in-app WebView or system browser for opening links |
| Customization | Choose theme colors, enable AMOLED dark mode, or use your wallpaper colors on Android 12+. Picture-in-Picture, landscape lock, screen protection, and incognito mode toggles |
| Privacy | Screen protection blocks screenshots and screen recording. Incognito mode skips watch history. No Persistent Visitor mode clears YouTube visitor data on each launch. Clear Cached Images button |
| Settings | Configure playback defaults, SponsorBlock categories, search limits, feed order, link opening mode, duplicate playlist warning, data import/export, and privacy options |

## Casting to TV (PhoneTV)

Pair with the [PhoneTV](https://github.com/RoundSalmon4/PhoneTV) app on an Android TV or Fire TV from **Settings -> Cast (PhoneTV)** (or the cast icon in the player). In **Settings** you can add / removed saved devices.

While a cast is active:

- The phone becomes a remote: open any video and it plays on the TV, with the phone's own player kept ready (paused) so controls respond and disconnecting resumes instantly from where the TV was
- Speed, quality, subtitles, chapters, volume, and SponsorBlock all mirror to the TV (SponsorBlock skips run on the TV and its notice is shown there)
- Browsing away and returning to the same video resumes at the TV's position instead of restarting

The cast relies on a direct connection to the TV on your local network, so a phone VPN needs local-network access enabled ("local network traffic", with VPN lockdown off). If the connection fails, PhoneTube retries a couple of times and then explains what to check.

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
