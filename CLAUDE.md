# meteocool Android — agent notes

Kotlin app. The map is a `WebView` showing [meteocool/core](https://github.com/meteocool/core)'s
`android.html`. Everything else is native: settings, onboarding, location and push. It follows
the iOS app (`meteocool/ios`) closely, and its `CLAUDE.md` explains the reasons behind most of
what is described here.

## Build

Use JDK 17 or later (21 is known to work) and Android SDK platform 36.

```bash
./gradlew assembleFdroidDebug testFdroidDebugUnitTest lintFdroidDebug
./gradlew assembleGmsDebug          # needs app/google-services.json (gitignored)
```

There are two flavors:

- **`gms`**: Firebase Cloud Messaging for rain alerts, and fused location from Play services.
  The google-services plugin is only applied to tasks whose name contains "Gms".
- **`fdroid`**: no Google code. It has no push, so the Notifications section, the
  onboarding alerts page and the rain-alert feature row are hidden, and
  `app/src/fdroid/AndroidManifest.xml` drops the notification and background-location
  permissions. Location comes from the platform `LocationManager`.

Flavor-specific code lives in `app/src/{gms,fdroid}` behind two objects with the same
names in both flavors:

- `push/PushSupport`
- `location/service/LocationProviders`

## Releasing on F-Droid

F-Droid builds the `fdroid` flavor from a git tag and signs it with its own key; the recipe is
`metadata/com.meteocool.yml` in [fdroiddata](https://gitlab.com/fdroid/fdroiddata). The 2.2
build was disabled there (#84) because a location library pulled in Play services; check
`./gradlew :app:dependencies --configuration fdroidReleaseRuntimeClasspath` has no
`gms`/`firebase` before tagging.

The store listing comes from `metadata/<locale>/` in this repo: title, descriptions,
`images/phoneScreenshots/` and `changelogs/<versionCode>.txt` (500 characters at most).

## Environments

[`environment/MeteocoolEnvironment.kt`](app/src/main/java/com/meteocool/environment/MeteocoolEnvironment.kt)
holds every host. Never hardcode one at a call site.

| | App (default) | Staging (Experimental Features) | Demo (Demo Mode) |
| --- | --- | --- | --- |
| Web map | `app.meteocool.com/android.html` | `next.meteocool.com/android.html` | `demo.meteocool.com/android.html` |
| Native API | `app.meteocool.com` | `api-next.meteocool.com` | `api-demo.meteocool.com` |

- **Selection rules.** The environment is picked once per process, so a switch only takes effect
  after a restart. The two switches are mutually exclusive.
- **The one runtime switch** is the demo launch alert's "Disable Demo Mode".
- **Moving registrations.** `registration_origin` records where the push registration lives. When
  it differs from the current API, the registration is removed there before registering again.

## Backend contract (v4 legacy router)

`post_location`, `clear_notification` and `unregister` all take a JSON POST.

- **Success needs `{"success": true}` in the body.** The router answers a payload it rejects with
  `200 {"success": false}`.
- For `unregister`, a message of `"not registered"` also counts as success.

Constraints on the `post_location` fields, from
[`notifications/Registration.kt`](app/src/main/java/com/meteocool/notifications/Registration.kt):

- `lang` must be `de` or `en`.
- `ahead` must be between 5 and 45 minutes.
- `intensity` must be one of 14, 20, 26, 36, 41 dBZ.
- `details` and `withDBZ` are both sent.
- `timestamp` is in Unix **seconds**.
- `token` must be 32–192 characters.
- `source` is `"android"`.
- `pressure` is in hPa, or -1.

`RegistrationManager` owns every request. Requests run one at a time, and a fix older than
five minutes is never sent. In the foreground a fix is only posted if it is:

- more accurate than the last posted one, or
- more than 500 m away after at least a minute.

In the background, `BackgroundLocationWorker` posts a fix every 15 minutes, provided alerts
are on and the app has "Allow all the time".

ng delivers Android alerts through the FCM HTTP v1 API (`libs/meteocool-push` there), with a
Firebase service account from the same project as `app/google-services.json`. Visible alerts go
on the `rain_alerts` channel; the clear message is data-only, with `clear_all` set to `"true"`.

## Web bridge

The page calls the `Android` JavaScript interface:

- `requestSettings()` when it is ready;
- `postMessage(msg)`, from core's `postToNative()`, with `layerSwitcherOpened`/`Closed`,
  `detailSheetExpanded`/`Collapsed`, `drawerOpened`/`Closed` and `impactLight`/`Medium`/`Heavy`.
  The native buttons stay hidden while any of the three pairs is open: `drawerOpened` covers
  every sheet and panel at any height, the other two are all that older frontends send.

Messages are ignored unless the WebView's URL is on the current environment's web host.

The app calls into the page with:

- `window.settings.injectSettings({mapRotation, radarColorMapping, mapBaseLayer, experimentalFeatures})`;
- `window.lm.updateLocation(lat, lon, accuracy, zoom, focus)`, where `-1, -1, -1` hides the dot;
- `window.openLayerswitcher()`;
- `window.enterForeground()` and `window.leaveForeground()`.

Geolocation requests from the page itself are granted only for the map's origin, and only
when the app already holds location permission. WebView has no permission prompt of its own.

## Testing on an emulator

Debug builds accept launch extras (see
[`app/DebugHooks.kt`](app/src/main/java/com/meteocool/app/DebugHooks.kt)):

- `mc_test_api_url` and `mc_test_token` point the native API at the iOS repo's
  `tests/mobile-api-recorder.mjs`, which only accepts the token `"a" * 64`;
- `mc_test_map_url` loads a local core build, e.g. from `vite preview`;
- `mc_run_background_worker` runs the background worker once.

```bash
node ../ios/tests/mobile-api-recorder.mjs &
adb shell am start -n com.meteocool/.ui.SplashActivity \
    --es mc_test_api_url http://10.0.2.2:18765/ --es mc_test_token $(printf 'a%.0s' {1..64})
curl -s localhost:18765/requests
```

The page's console reaches logcat under the tag `WebConsole`, and WebView debugging is on,
so `chrome://inspect` works.

## Languages

English, German, French, Italian, Czech and Polish: the languages of the countries with radar
coverage. `localeFilters` in `app/build.gradle` keeps libraries from adding others. Wording follows
core's `src/locale/*.json` where the two overlap: formal "vous" in French, informal in the rest.
The web map has no Italian yet, so Italian users see an Italian app around an English map. The
backend only sends push text in `de` or `en` (`Registration.lang`).

Every string in `values/strings.xml` needs all five translations, and the store listing lives in
`metadata/{en-US,de-DE,fr-FR,it-IT,cs-CZ,pl-PL}/`.

## Preferences

Everything lives in the default `SharedPreferences`, accessed through
[`preferences/Prefs.kt`](app/src/main/java/com/meteocool/preferences/Prefs.kt).
`Prefs.migrate()` folds in the file named `"default"` that pre-4.0 builds also wrote to.
