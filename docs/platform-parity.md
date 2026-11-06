# iOS features on Android

The Android app requires API 36 for both installation and its target behavior.
The map uses Meteocool's shared frontend; native Android screens provide the
platform integrations below.

| iOS feature | Android implementation |
| --- | --- |
| Rain Live Activity | User-started rain monitoring with an ongoing notification and Android 16 Live Update request; Stop ends the session. |
| Radar, forecast, clock, and places widgets | Four configurable Android home-screen widgets, with current or chosen locations, refresh, and map links. |
| CarPlay rain status | Android Auto weather app with current rain status, observation time, and refresh. |
| Past alerts and rich radar notifications | Local bounded alert history; Google Play data notifications attach a radar preview without delaying the text alert. |
| Pause alerts | Until tomorrow, for a week, or through the current/next rain spell; the server also enforces the pause. |
| Alert coverage warning | Native notification settings show when the registered position is outside alert coverage. |
| Storm log | Experimental mode: explicit consent, clear/disable, five sorts, details, approximate storm tracks, sharing, and map links. |
| Flash to bang | Experimental mode: native timer, distance, recent-strike matching, and thunderstorm safety text. |
| AR storms | Experimental mode: Camera2 and orientation sensors; nine volume modes; two-hour scan history; optional storm-motion extrapolation; lightning and mesocyclone overlays; compass/manual/Sun calibration; cloud marking; cell tracks and forecast uncertainty; map markers and links. |
| Map sharing, screenshot sharing, map links | Native Android share sheet and capture callback; verified HTTPS links and `meteocool://map` links. |
| Radar data credits and logo confetti | Native settings credits and a three-tap animation respecting disabled system animations. |

New controls use the same 14 app languages as the existing Android interface.
Storm logging requires push registration and is therefore available in the
Google Play build. Its collection defaults to off. Alert history and widget
locations are excluded from Android backups.

## Platform differences

- Android Live Updates are for user-initiated ongoing activities. Monitoring is
  explicitly started by **Track this rain**, lasts at most two hours, and ends
  when the forecast becomes dry. Notification promotion remains under Android
  and user control; it is not an automatic background Live Activity. See
  [Android's Live Update requirements](https://developer.android.com/develop/ui/views/notifications/live-update).
- Widget refresh follows Android's background scheduling. It is not guaranteed
  to run at an exact time; stale or incomplete data is shown as unavailable.
  Current-location widgets need a recent location from the app or background
  location permission. Fixed-place widgets do not need location permission.
- The car screen uses Android's [weather app category](https://developer.android.com/training/cars/apps/weather).
  It deliberately matches the current iOS rain status screen. Car hosts control
  availability and presentation; store approval and real-car testing remain
  release requirements.
- Storm camera positioning uses device sensors and explicit calibration. It does
  not reproduce ARKit world tracking. Sensor quality, camera field of view, and
  alignment require physical-device checks. Rendered volumes are sampled and
  bounded; the interface identifies partial volume loading.

## Server contract

Current Android registrations send `androidDataAlerts: true` so the app handles
alerts even while closed, and an explicit `stormLog` consent value. Older clients
keep their existing notification delivery. The corresponding API and
notification-worker changes must be deployed together for these behaviors.
An older server may still deliver text notifications, but cannot provide the
same background history and preview behavior. Keep the native API and map on
the same deployment.
