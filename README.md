# StreamVault — Clean Android Video & Channel Client

StreamVault is an open-source Android application built with **Kotlin**, **Jetpack Compose**, **AndroidX Media3 (ExoPlayer)**, and **Room**. It allows you to browse YouTube channels via URL, watch non-DRM public or self-hosted streams with full resolution and playback speed controls, save favorites offline, and download permitted video streams for offline playback—with zero third-party ad SDKs.

## Features

- **Channel & Video URL Input**: Supports `youtube.com/@handle`, `youtube.com/channel/UC...`, `youtube.com/watch?v=...`, `youtu.be/...`, `youtube.com/shorts/...`, as well as direct `.m3u8` / `.mpd` / `.mp4` stream URLs.
- **Channel Video Feed**: Automatically resolves channel handles and fetches channel uploads using public YouTube Atom XML feeds (zero API key required) with optional **YouTube Data API v3** support for extended metadata.
- **Full-Control Media3 Video Player**:
  - **Quality / Resolution Selector**: Switch between Auto, 2160p, 1440p, 1080p, 720p, 480p, 360p, 240p, and 144p via `DefaultTrackSelector`.
  - **Playback Speed Control**: Adjust playback speed seamlessly (`0.25x`, `0.5x`, `0.75x`, `1.0x`, `1.25x`, `1.5x`, `1.75x`, `2.0x`).
  - **Zero Ad SDKs**: Clean, distraction-free custom Compose player UI without third-party ad networks.
- **Offline Downloads**: Download available non-DRM video streams in your preferred quality using Android's `DownloadManager` and manage/play them in the **Downloads** tab.
- **Local Favorites**: Bookmark any video into a local **Room** SQLite database for instant access.
- **Pluggable Stream Backend**: Configure an optional self-hosted Piped-compatible API endpoint in **Settings** or a YouTube Data API v3 key in `local.properties`.

## Getting Started

1. **Push to a GitHub Repository**:
   Extract `streamvault-android-repo.zip` and push all files to a new GitHub repository:
   ```bash
   git init
   git add .
   git commit -m "Initial commit: StreamVault Android app"
   git branch -M main
   git remote add origin https://github.com/<your-username>/<your-repo>.git
   git push -u origin main
   ```

2. **Open in Android Studio**:
   - Open the extracted root folder in **Android Studio Ladybug (2024.2+)** or newer.
   - Let Gradle sync dependencies (JDK 17 recommended).
   - *(Optional)* Add your YouTube Data API v3 key to `local.properties`:
     ```properties
     YOUTUBE_API_KEY=your_api_key_here
     ```
   - Click **Run** on an Android device or emulator (API 26+).

3. **Automated GitHub APK Builds**:
   Every push to `main` triggers `.github/workflows/android-ci.yml`, which runs unit tests and builds an installable `app-debug.apk` artifact automatically under the **Actions** tab.
