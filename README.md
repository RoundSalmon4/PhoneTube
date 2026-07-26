# PhoneTube

A YouTube phone app built on [SmartTube](https://github.com/yuliskov/SmartTube)'s MediaServiceCore.

> **Note:** This is a personal project and is not intended for general use.

## Features

| Feature | Description |
|---------|-------------|
| Home Feed | Browse YouTube recommendations, trending, music, sports, live, news, gaming, and kids content |
| Search | Search YouTube with autocomplete suggestions and configurable result limits |
| Video Playback | Play videos with DASH and HLS streaming support |
| Background Play | Continue listening with the mini player or notification controls |
| Mini Player | Persistent playback bar with play/pause, seek, and rewind controls |
| SponsorBlock | Skip sponsor segments and other interruptions automatically |
| Local Playlists | Create and manage playlists without a Google account |
| Watch History | Your watch history is saved locally on your device |
| Channel Pages | Browse channel videos and subscribe locally |
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
