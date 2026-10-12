# DPM

Discord Presence Manager. It shows what you're doing on your Android phone (music, video, games, apps) as your Discord activity.

<p align="center"><img src="docs/assets/icon-512.png" alt="DPM icon" width="120"></p>

> [!WARNING]
> Sending presence from a user account is against Discord's Terms of Service and can get the account limited or banned. Use an alternate account if you can. DPM isn't affiliated with Discord.

## What it does

- Follows what you do: playing media first, then the game or app on screen
- Static mode where you write the activity yourself
- Live preview that looks like the Discord card
- Allow or ignore each app, or give an app its own text
- Schedule by weekday and hour, plus an online-only rule
- Several accounts (browser login, token or QR code) with instant switching
- App icons and cover art through your own Discord application ID
- Runs in the background with Pause and Stop in the notification
- Checks GitHub for new releases and installs them from inside the app

Needs Android 10 or newer. Wallpaper colors need Android 12 and the themed icon needs Android 13.

## Install

Grab the APK from the [latest release](https://github.com/DinoCDX/discord-presence-manager/releases/latest) and install it.

## Setup

1. Log in. The Browser tab is the easiest because captchas, 2FA and passkeys work there.
2. Go through the intro and grant the permissions you want. Any of them can be skipped.
3. Turn on Share my presence on the Presence tab.
4. Choose which apps can show up on the Apps tab.
5. For images, create an application in the [Discord developer portal](https://discord.com/developers/applications) and paste its Application ID in Settings.

More detail is in the [docs](docs/index.html).

## Permissions

| Permission | Used for |
| --- | --- |
| Internet | Discord and GitHub |
| Notifications | Pause and Stop in the notification |
| Usage access | Seeing which app or game is on screen |
| Notification access | Reading what media is playing |
| Battery optimization | Keeping Android from stopping the app |
| Start at boot | Restoring presence after a restart |
| Installed apps | The app list and icons |
| Install packages | Installing downloaded updates |

The token is stored in encrypted preferences and only sent to Discord.

## Build

You need JDK 17 and Android SDK platform 34.

```bash
git clone https://github.com/DinoCDX/discord-presence-manager.git
cd discord-presence-manager
./gradlew assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`.

## Releases

Pushing a tag like `v1.0.0` runs `.github/workflows/release.yml`, which builds a signed APK and publishes a GitHub release. The in-app updater reads the latest release.

Updates only install over the existing app when every release is signed with the same key, so create one keystore and keep it safe:

```bash
keytool -genkeypair -v -keystore dpm-release.jks -alias dpm -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 dpm-release.jks
```

Add these repository secrets: `KEYSTORE_BASE64` (the base64 output), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`dpm` above) and `KEY_PASSWORD`. Then:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The tag sets the version: `v1.2.3` becomes version name 1.2.3 and version code 10203.

## Docs on GitHub Pages

The site in `docs/` is ready to publish. In the repository settings open Pages, choose Deploy from a branch, then select your main branch and the `/docs` folder.

## Credits

Made by [DinoCDX](https://github.com/DinoCDX). Built with Kotlin, Jetpack Compose, Material 3, OkHttp and ZXing.
