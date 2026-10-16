# DPM

DPM (Discord Presence Manager) puts what you're doing on your Android phone into your Discord status: the music you're playing, the video you're watching, the game or app you have open.

<p align="center"><img src="docs/assets/icon-512.png" alt="DPM icon" width="120"></p>

<p align="center">
<a href="https://github.com/DinoCDX/discord-presence-manager/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/DinoCDX/discord-presence-manager?style=flat-square&color=4752c4"></a>
<a href="https://dinocdx.github.io/discord-presence-manager/"><img alt="Docs" src="https://img.shields.io/badge/docs-website-4752c4?style=flat-square"></a>
</p>

<p align="center">
<img src="docs/assets/screenshots/presence.png" alt="Presence preview" width="260">
<img src="docs/assets/screenshots/apps.png" alt="Apps tab" width="260">
</p>

## Documentation

Setup, features, permissions, images, troubleshooting and build instructions live on the **[DPM docs](https://dinocdx.github.io/discord-presence-manager/)**.

## What you get

- It follows along. Whatever's playing comes first, then the game or app on your screen.
- A static mode, where you write the status yourself, with presets and optional timers and buttons.
- A live preview that looks like the card Discord shows.
- Per-app control: let an app show automatically, always, or never, or give it its own text.
- Music and video are separate sources, so you can rank them differently.
- A schedule by day and hour, plus an option to only share while you're online.
- Several accounts. Log in with Discord and switch instantly.
- Album art, video thumbnails and static pictures from direct image links, with no setup.
- A notification with Pause and Stop, so you can control it from anywhere.
- Updates from GitHub that you can install without leaving the app.
- A Material 3 Expressive interface that follows your wallpaper colors.

You need Android 10 or newer. Wallpaper colors need Android 12 and the themed icon needs Android 13.

## Getting it

Download the APK from the [latest release](https://github.com/DinoCDX/discord-presence-manager/releases/latest) and install it.

## First run

1. Go through the intro and log in with Discord.
2. Allow the permissions you're happy with. Every permission can be skipped.
3. On the Presence tab, switch on Share my presence.
4. On the Apps tab, choose which apps are allowed to show up.
5. Optional: for album art, turn on Send pictures under Settings, then Pictures. For your own pictures, see the [Features page](https://dinocdx.github.io/discord-presence-manager/features.html#pictures).

The [docs](https://dinocdx.github.io/discord-presence-manager/) go into more detail.

## Permissions

| Permission | What it's for |
| --- | --- |
| Internet | Talking to Discord and GitHub |
| Notifications | Pause and Stop in the notification |
| Usage access | Seeing which app or game is on screen |
| Notification access | Reading what media is playing |
| Background activity | Keeping Android from putting the app to sleep |
| Start at boot | Bringing presence back after a restart |
| Installed apps | The app list and icons |
| Install packages | Installing downloaded updates |

Your login is kept in encrypted preferences on the phone and is only ever sent to Discord.

## Credits

Made by [DinoCDX](https://github.com/DinoCDX) with ❤️.
