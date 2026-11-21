# DomoWallDock

<p align="center">
  <a href="README.md"><img src="docs/images/flags/fr.svg" width="32" alt="Français"></a>&nbsp;
  <a href="README.en.md"><img src="docs/images/flags/gb.svg" width="32" alt="English"></a>&nbsp;
  <a href="README.de.md"><img src="docs/images/flags/de.svg" width="32" alt="Deutsch"></a>&nbsp;
  <a href="README.es.md"><img src="docs/images/flags/es.svg" width="32" alt="Español"></a>
</p>

[![License GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-64748b)](LICENSE)
[![Android 7+](https://img.shields.io/badge/Android-7%2B-3ddc84)](#download)
[![Latest version](https://img.shields.io/github/v/release/McFlyPartages/DomoWallDock?include_prereleases&label=version)](https://github.com/McFlyPartages/DomoWallDock/releases)

**Turn an Android tablet into a wall panel for [Home Assistant](https://www.home-assistant.io/) or [Gladys Assistant](https://gladysassistant.com/).**

DomoWallDock (formerly GladysWallDock) is a fork of [WallDock](https://github.com/psycho1807/WallDock) by psycho1807. WallDock is built for Home Assistant; everything it does is still here, and Gladys Assistant is added. GPL-3.0 licence, like the original.

> 📘 **Illustrated guide: [using DomoWallDock with Gladys Assistant](docs/TUTO-GLADYS.en.md)** — installation, connection, MQTT, charge management, screensaver.
> The app is available in French, English, German and Spanish: the tablet's language by default, or your choice in the settings.

## Download

Signed APKs are on the [**Releases**](https://github.com/McFlyPartages/DomoWallDock/releases) page (take the latest; they are pre-releases).

| Your tablet | File to download | Branch |
|---|---|---|
| **Android 8 or newer** (usual case) | `DomoWallDock-x.y.z.apk` | `main` |
| **Android 7.x** (older tablets) | `DomoWallDock-x.y.z-android7.apk` | `android7` |

- The `android7` version is a **frozen** branch: it only gets fixes, backported by hand. On Android 8+, always use the standard version.
- Both versions are signed with the **same key**: a new version installs over the previous one and the settings are kept.
- To install: allow "unknown sources" for the browser or file manager you use, open the file, then follow [Installation](#installation).

## Home Assistant / Gladys Assistant compatibility

| Feature | Home Assistant | Gladys Assistant |
|---|:---:|:---:|
| Full-screen dashboard, kiosk mode, app dock, automatic return | ✅ | ✅ |
| Automatic sign-in (user and password) | ✅ ¹ | ✅ |
| Local, external or both access (automatic switch) | ✅ | ✅ |
| Screensaver: clock, Immich album, photo folder, platform page | ✅ | ✅ |
| Screen off every night, turned on by the front camera | ✅ | ✅ |
| Full-screen calendar (day, week, month, agenda) | ✅ | ✅ |
| Alarms on the tablet | ✅ (also driven from HA) | ✅ (set in the app) |
| Tablet sensors: battery, charging, motion, screen | ✅ (ESPHome or MQTT) | ✅ (MQTT) |
| Charge management with a smart plug | ✅ (MQTT + automation) | ✅ (MQTT + scenes) |
| Synchronised Music Assistant player (Sendspin) | ✅ | ✅ ² |
| Full control from the platform (brightness, open an app or page, alerts, full-screen cameras, sounds, text-to-speech…) | ✅ (ESPHome) | ➖ screen, reload, back to dashboard |
| Jarvis voice assistant ("Hey Jarvis") | ✅ | ❌ |
| ESPHome Bluetooth proxy | ✅ | ❌ |

¹ Not with two-factor authentication. ² Music Assistant is a separate server: it works with or without Home Assistant.
When you choose Gladys, the app unticks Jarvis, the Bluetooth proxy and Music Assistant by itself (you can tick them again, for instance if you also use Home Assistant or a Music Assistant server).

## What DomoWallDock does

**Home Assistant or Gladys, your choice**
- Settings → "Dashboard shown": a radio button picks the platform to launch. Each one has its column (side by side on a wide screen or a tablet in landscape, one above the other otherwise): address, account, password and default dashboard (optional, e.g. `/lovelace/0?kiosk` or `/dashboard/living-room`).
- **Local / External / Both access** for each platform: local address (HTTP or HTTPS, IP and port) and external address (HTTPS). With "Both", the app uses the local address when it answers, otherwise the external one: the tablet also works away from home, for instance behind a reverse proxy. A health check tests each address.
- **Automatic sign-in**: with a user and a password, the tablet signs in by itself when the login page shows (Home Assistant: like "Keep me logged in"; not with two-factor authentication). Tip: an account dedicated to the tablet, without admin rights.
- Features that go through Home Assistant (Jarvis, Bluetooth proxy, cameras, sounds) stay available while Gladys is shown, as long as the HA address is filled in.

**ESPHome Bluetooth proxy** (Home Assistant)
- Settings → "Bluetooth proxy (ESPHome)": the tablet listens to the Bluetooth Low Energy devices around it (Xiaomi thermometers, BTHome sensors, beacons…) and forwards their advertisements to Home Assistant through the ESPHome integration, exactly like an ESPHome `bluetooth_proxy` module. Devices show up in HA as if they were in range of its own Bluetooth.
- Listening only: HA cannot *connect* to devices through the tablet (devices needing a GATT connection, pairing).
- Bluetooth and location permissions are asked when you save (without location, Android hides beacons). Scanning only runs while HA is subscribed.
- Gladys cannot use a Bluetooth proxy: this option only helps with Home Assistant.

**With Gladys Assistant**
- **MQTT connection** (shared by both platforms): settings → "MQTT", with the broker address, user and password (in Gladys: Integrations → MQTT → Configuration). The tablet announces itself (Home Assistant MQTT discovery format, understood by recent Gladys 5 versions): Integrations → MQTT → discovered devices, add "DomoWallDock …".
- In Gladys: battery, battery temperature, charging, motion, screen (on / off), "Reload" and "Back to the dashboard" buttons.
- **Charge management** for an always-plugged tablet: settings → "Battery". Android does not let an app cut charging, so DomoWallDock publishes a "Charge wanted" sensor (yes below the low threshold, 30 % by default; no above the high threshold, 80 % by default) and two Gladys scenes drive the charger's smart plug:
  - "Charge wanted" goes to 1 → turn the plug on;
  - "Charge wanted" goes to 0 → turn the plug off.

  If the tablet drops off the network (crash, Wi-Fi down), the broker sets "Charge wanted" back to yes by itself (MQTT last will), so the battery does not run flat.
- **Screen driven by the front camera**: the screen turns on when someone passes and off after a delay without motion or touch (settings → motion detection → delay in seconds). Images are analysed on the tablet and are never sent or recorded.

**Kiosk** (inherited from WallDock)
- Full screen, system bars hidden, screen always on, settings protected by a code.
- Dock of allowed apps, automatic return to the dashboard after inactivity.
- Can become the tablet's home screen; restarts on boot (power cut).
- Dashboard zoom (50 to 200 %), screensaver (clock, any page, Immich slideshow or photo folder).
- Schedule: screen off every night. Address allow-list. Fallback screen when the server does not answer.
- Settings backup and restore to a file (WallDock backups are accepted).

**With Home Assistant** (inherited from WallDock, still working)
- Encrypted native ESPHome integration, Jarvis voice assistant, media player, synchronised Music Assistant player (Sendspin), alarms, calendar. These features need Home Assistant; when you choose Gladys, Jarvis, the Bluetooth proxy and Music Assistant are unticked automatically (you can tick them again).

## MQTT topics

`<device>` is `domowalldock-xxxxxx` (shown in the settings).

| Topic | Direction | Values |
|---|---|---|
| `domowalldock/<device>/battery` | state | 0 to 100 |
| `domowalldock/<device>/battery_temperature` | state | °C |
| `domowalldock/<device>/charging` | state | `ON` / `OFF` |
| `domowalldock/<device>/charge_wanted` | state | `ON` / `OFF` (when charge management is on) |
| `domowalldock/<device>/motion` | state | `ON` / `OFF` |
| `domowalldock/<device>/screen` | state | `ON` / `OFF` |
| `domowalldock/<device>/screen/set` | command | `ON` / `OFF` |
| `domowalldock/<device>/reload/set` | command | any value |
| `domowalldock/<device>/dashboard/set` | command | any value |

## Installation

1. Download the APK for your tablet (see [Download](#download)) and install it (allow installing from this source). The app has its own id (`fr.mcflypartages.domowalldock`): it can live alongside WallDock.
2. On first start: platform (Home Assistant or Gladys), its address (and if needed account, password, dashboard), admin code, dock apps, then **Save**.
3. In the settings: home screen, display over other apps, camera (motion detection), then the MQTT part.
4. Turn off Android battery optimisation for DomoWallDock, otherwise the system may kill the app ([dontkillmyapp.com](https://dontkillmyapp.com) covers each brand).

**Updates**: by hand, installing the new APK over the old one (same signing key, settings are kept). The app does not update itself.

## Building

`gradle assembleGithubRelease` with Gradle 8.14, JDK 17 and Android SDK 36. To sign, give the keystore path (PKCS12, alias `gladyswalldock`, the key's original name) in `WALLDOCK_KEYSTORE` and its password in `WALLDOCK_KEY_PASSWORD`; the key is not in the repository. Without them the APK is unsigned (`assembleGithubDebug` gives a test APK).

The code keeps the original `app.walldock` package, to make merging upstream changes easier. The MQTT client (`app/src/main/java/app/walldock/mqtt`) and the ESPHome server (`…/esphome`) are plain Java and can be tested on a JVM. `./gradlew testGithubDebugUnitTest` renders the settings screen to PNG (Robolectric) in `app/build/preview`.

## Source code and repositories

Development happens on a personal Forgejo; this GitHub repository is a **mirror** (`main` and `android7` branches, and tags). Release APKs are published on both. Changes go through pull requests on the original forge, then land here.

## Credits

- **DomoWallDock**: adapted and maintained by **McFlyPartages**.

  <a href="https://mcflypartages.fr"><img src="docs/images/social/web.svg" width="36" alt="Website mcflypartages.fr" title="Website mcflypartages.fr"></a> <a href="https://mastodon.social/@McFlyPartages"><img src="docs/images/social/mastodon.svg" width="36" alt="Mastodon @McFlyPartages@mastodon.social" title="Mastodon @McFlyPartages@mastodon.social"></a> <a href="https://www.facebook.com/McFlyPartages"><img src="docs/images/social/facebook.svg" width="36" alt="Facebook McFlyPartages" title="Facebook McFlyPartages"></a> <a href="https://x.com/McFlyPartages"><img src="docs/images/social/x.svg" width="36" alt="X @McFlyPartages" title="X @McFlyPartages"></a> <a href="https://fr.tipeee.com/mcflypartages"><img src="docs/images/social/tipeee.svg" width="36" alt="Support on Tipeee" title="Support on Tipeee"></a> <a href="https://ko-fi.com/mcflypartages"><img src="docs/images/social/kofi.svg" width="36" alt="Support on Ko-fi" title="Support on Ko-fi"></a>

  Enjoying the app? A little support on Tipeee or Ko-fi is always appreciated.

- **[WallDock](https://github.com/psycho1807/WallDock)** by psycho1807 (GPL-3.0): the whole base of the app. DomoWallDock is neither affiliated with nor endorsed by its author; WallDock Pro is a separate product.
- "Stop" word: [microWakeWord](https://github.com/kahrendt/microWakeWord) model by Kevin Ahrendt (Apache 2.0); feature extraction ported from the TensorFlow Lite Micro micro frontend (Apache 2.0).
- Wake word: [openWakeWord](https://github.com/dscripka/openWakeWord) models (downloaded on first use, CC BY-NC-SA 4.0), run with TensorFlow Lite.
- Protocols: [ESPHome native API](https://esphome.io/components/api.html), Home Assistant MQTT discovery.
- Icons: [Simple Icons](https://simpleicons.org/) (CC0), [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache 2.0), [flag-icons](https://github.com/lipis/flag-icons) (MIT).
