# Meteocool - Android
The official android application of the [meteocool project](https://github.com/v4lli/meteocool)

Requires Android 16 (API 36) or newer. The shared weather map is accompanied by
native rain status, alert history and muting, home-screen widgets, and an Android
Auto weather screen. Experimental mode adds storm logging, flash-to-bang, and
camera-based storm visualization. See [platform parity](docs/platform-parity.md).

Build with JDK 17 or newer and Android SDK 36:

```sh
./gradlew testFdroidDebugUnitTest assembleFdroidDebug lintFdroidDebug
```

For Google Play builds, provide the project's `app/google-services.json`, then
run the corresponding `GmsDebug` tasks in a separate Gradle invocation. F-Droid
builds have no Firebase push; their widgets, rain monitoring, and car screen use
the native API directly.

[<img src="metadata/en_fdroid.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/com.meteocool/)
[<img src="metadata/en_google_play.png"
    alt="Download on Google Play Store"
    height="80">](https://play.google.com/store/apps/details?id=com.meteocool)
