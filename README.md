# Omeron

Omeron is a fork of [**Stealth**](https://gitlab.com/cosmosapps/stealth) (a.k.a. unReddit),
the account-free, privacy-oriented Reddit client, with a set of extra features added on top.

It needs **no Reddit account and no official Reddit API**. All content is fetched by
**web scraping `old.reddit.com`**, so there are no API keys, logins, or rate-limit tokens
to configure.

## Added over Stealth

- **Compact, Card & Gallery** post layouts (toggle in the app bar)
- **Search** and **Popular** in the bottom navigation
- Profile pages with **Posts** and **Comments** tabs
- **Web-scraping post search** (no Reddit API)
- **Handle Link** — open/share any Reddit link straight into Omeron
- **Per-subreddit hide** on the home feed
- **Local multireddits** — group subreddits *and* users, with hide toggle and a dedicated feed page
- **Follow users** locally
- Home **Multis** tab with per-multireddit sub-tabs
- Reddit **video playback fix** (signed DASH/HLS manifests)

Plus everything inherited from Stealth: browsing, comments, sort, history, saved posts,
multiple profiles, NSFW toggle, awards, light/dark theme.

## Download

Grab the latest APK from the **[Releases page](https://github.com/shourovrm/Omeron/releases)**.

- The release APK is built for **arm64-v8a (ARMv8) devices only**.
- It is a self-signed release build — install directly, no bundle or extra setup needed:
  ```
  adb install omeron-<version>-arm64-release.apk
  ```

## Build

Requires **JDK 17** (Gradle 7.5.1 does not run on newer JDKs) and the Android SDK.

```
JAVA_HOME=/path/to/jdk-17 ./gradlew assembleRelease
```

Release signing reads `keystore.properties` + a keystore (both git-ignored). Without them,
Gradle falls back to an unsigned build.

## License

Omeron is licensed under the **GNU General Public License v3.0** — see [LICENSE](LICENSE).
It inherits this license from the original Stealth app by **Cosmos**. Fork developed by
**Riad Mashrub Shourov**.
