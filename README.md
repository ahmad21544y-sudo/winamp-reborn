# WINAMP REBORN — Android foundation

A clean-room Android recreation inspired by the classic Winamp 2.x player. This is the first runnable project foundation, not a claim of feature parity with the original.

## Included now
- Native Android app using Kotlin + Jetpack Compose.
- Media3 ExoPlayer playback engine hosted in a `MediaSessionService` for background playback and system media controls.
- Multi-file audio picker and an in-memory playlist.
- Play/pause, previous/next, seek progress display, shuffle and repeat.
- Winamp-inspired dark/green visual language and a preliminary equalizer panel.

## Build
Open this folder in Android Studio (Giraffe or newer), allow Gradle sync, then run the `app` configuration on an Android device (min SDK 23).

## Honest implementation status
The equalizer panel is currently a visual/control prototype; it is not yet connected to Android's audio effects. Skins, persistent playlists, full classic pixel-accurate window layout, visualizations, tag editing, crossfade, and Windows edition remain to be implemented. No APK is included because this environment has no Android SDK/Gradle installation and therefore cannot compile or device-test the project here.

## Next engineering milestones
1. Bind a real Android `Equalizer` effect to the active audio session and expose all bands/presets.
2. Persist playlists, queue state, and settings with Room/DataStore.
3. Add classic Main Player / Equalizer / Playlist skin layouts and skin import support.
4. Add audio visualizations, metadata editing, sleep timer, crossfade, and robust library scanning.
5. Device test on multiple Android versions and produce a signed APK.
