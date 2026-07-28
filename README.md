# PhoneTube

A YouTube phone app built on [SmartTube](https://github.com/yuliskov/SmartTube)'s MediaServiceCore.

> **Note:** This is a personal project and is not intended for general use.

## Features

| Feature | Description |
|---------|-------------|
| Home Feed | Browse YouTube recommendations, subscriptions, trending, music, sports, live, news, gaming, and kids content |
| Search | Search YouTube with autocomplete suggestions and configurable result limits |
| Video Playback | Play videos with DASH and HLS streaming, quality picker, subtitle support, and audio track selection |
| Background Play | Continue listening with a persistent notification and mini player controls |
| Mini Player | Persistent playback bar with play/pause, rewind, forward, close, and progress bar |
| SponsorBlock | Skip sponsor segments and other interruptions automatically with per-category skip/toast/none controls |
| Local Playlists | Create and manage playlists without a Google account, add from home feed or watch history |
| Local Subscriptions | Subscribe to channels locally — latest videos appear on the home feed |
| Subscriptions Feed | Home screen shows recent uploads from your subscribed channels |
| Watch History | Your watch history is saved locally with resume position and progress indicators |
| Channel Pages | Browse channel videos and subscribe locally |
| Deep Linking | Open YouTube video, playlist, channel, and shorts links directly in PhoneTube |
| Speed Control | Adjust playback speed from 0.25x to 3.0x |
| Quality Picker | Choose video quality from available formats with current resolution shown |
| Audio Track Picker | Select between available audio tracks when multiple are present |
| Subtitles | Toggle and select subtitle tracks |
| Feed Toggle | Enable or disable individual feed sources for faster loading |
| Feed Cache | Feed data is cached with watch progress for instant loading on return |
| Customization | Choose theme colors, enable AMOLED dark mode, or use your wallpaper colors on Android 12+ |
| Settings | Configure playback defaults, SponsorBlock categories, search limits, and which feeds to show |

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
