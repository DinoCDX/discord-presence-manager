# Contributing to DPM

Thanks for wanting to help. DPM is a small app, so clear bug reports, focused changes and honest docs go a long way.

## Ways to help

- **Report a bug.** Open an issue with your phone model, Android version, DPM version (Settings, About), what you did, what you expected and what happened. If it's something DPM does wrong, attach logs: in Settings, tap **Advanced**, open **Logs**, and copy them.
- **Suggest a feature.** Describe the problem it would solve before the solution. Small ideas are welcome, but check open issues first.
- **Fix something.** Look for issues labelled as good first issues, or anything that bothers you in the app.
- **Improve the docs.** The website lives in `docs/`. Keep it in step with what the app actually shows.

For anything larger than a small fix, open an issue first so we can agree on the approach before you spend time on it.

## Setting up

You need:

- JDK 17
- Android SDK platform 36
- Android Studio, or just the command line tools

Then:

```sh
git clone https://github.com/DinoCDX/discord-presence-manager.git
cd discord-presence-manager
./gradlew assembleDebug
```

The APK ends up in `app/build/outputs/apk/debug/app-debug.apk`. To use your own Discord application ID for login, set `DISCORD_APP_ID` or pass `-PdiscordAppId=...`. The default is in `gradle.properties`.

DPM targets Android 10 (API 29) and newer. Test on a device or emulator at that level if you can, and on a Samsung or Xiaomi phone if your change touches background behaviour, because those phones handle it differently.

## Making a change

1. Create a branch from `main` with a short name that says what it does, such as `fix-reconnect-backoff` or `apps-tab-empty-state`.
2. Keep each pull request to one topic. Separate refactors from behaviour changes.
3. Match the code around your change. The app is Kotlin with Jetpack Compose and Material 3, so use the existing components and theme tokens rather than new one-off styles.
4. Make sure `./gradlew assembleDebug` passes before you open the pull request.
5. If your change alters text, layout or a setting that the docs describe, update the matching page in `docs/` too.

## Pull requests

- Describe what changed and why. Link the issue if there is one.
- For anything visual, include before and after screenshots, and say which screen and which theme (light or dark) you tested.
- Say how you tested it: device, Android version, and what you checked.
- Keep the pull request in draft until it's ready for review.

## Docs

The site in `docs/` is plain HTML, CSS and JavaScript with no build step, so you can open the pages directly in a browser to check them.

- `docs/index.html` is the home page. Its styles are in `docs/assets/home.css` and its scripts in `docs/assets/home.js`.
- The other pages use the shared stylesheet, `docs/assets/style.css`.
- Images for the docs live in `docs/assets/screenshots/`.

The app loads `docs/assets/pause.png` by URL, so don't rename or remove it.

## License

DPM is released under the [MIT License](LICENSE). By contributing, you agree that your contribution is released under the same license.