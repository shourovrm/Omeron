# Omeron

Omeron is a fork of [**Stealth**](https://gitlab.com/cosmosapps/stealth) (a.k.a. unReddit),
the account-free, privacy-oriented Reddit client, with a set of extra features added on top.

It uses **no official Reddit API**. All content is fetched by **web scraping
`old.reddit.com`**, so there are no API keys or rate-limit tokens to configure.

Since August 2026 Reddit only serves `old.reddit.com` to logged-in accounts, so a one-time
login is required — see [Setup](#setup) below.

## Added over Stealth

- **Compact, Card & Gallery** post layouts (toggle in the app bar)
- **Search** and **Popular** in the bottom navigation
- Profile pages with **Posts** and **Comments** tabs
- **Web-scraping post search** (no Reddit API)
- **Reddit account login** via WebView cookie capture (required by Reddit for old.reddit since Aug 2026)
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

## Setup

Reddit now puts `old.reddit.com` behind a login wall, so Omeron shows nothing until you
log in once. Only the resulting `reddit_session` cookie is kept on the device and sent with
the scraping requests — no API, no OAuth, nothing else about the account is touched.

1. **Create a throwaway account.** Nothing in Omeron needs your identity, so sign up at
   [reddit.com/register](https://www.reddit.com/register/) with a privacy-friendly mailbox
   such as [Proton Mail](https://proton.me/mail) (Tuta, addy.io aliases, etc. work too).
2. **Log in inside Omeron**: Settings → Data → **Reddit account**, then use the
   email/username + password form (or *Email me a one-time link*). *Continue with Google*
   does not work inside a WebView. **Log out** in the same place deletes the cookie.
3. **Tune the account at [old.reddit.com/prefs/](https://old.reddit.com/prefs/)** (open it
   in a browser logged in with the same account). Once logged in, Reddit ignores the app's
   NSFW toggle and uses these account preferences instead:
   - **NSFW content** — tick *show mature (18+) content* and *include mature content in
     search results*; untick *Hide images for NSFW/18+ content*. Without the search one,
     NSFW subreddits and posts never show up in search.
   - **Less email & tracking** — tick *unsubscribe from all emails*; untick *send email
     digests*, *receive welcome messages from moderators*, *notify me when people say my
     username*, *show me links I've recently viewed*, *let others see my online status*,
     *make my votes public* and *Use new Reddit as my default experience*. Hit
     **save options**, then on [prefs/privacy](https://old.reddit.com/prefs/privacy/)
     untick the personalization and ad options.

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

---

_Developed with the help of Claude._
