System: material3
World: contact sheet (variation 4, chosen 2026-10-20)

Every number below is copied from Material Design 3 (androidx material3 1.4.0 tokens,
m3.material.io) unless it is listed under Departures. This file wins over any layout; change it in
the same commit as the UI.

## Departures

Deliberate changes to Material 3, applied everywhere: post media is full width with square
corners, never inside a card; posts are separated by space, not cards or dividers; a saved post
carries the marker tick on its picture; text posts are a full-width surfaceContainerHigh frame with
the title in headlineSmall. Kept from Material without exception: 48dp targets, contrast, sp text
that follows font scale, system Back, window insets, standard controls.

## Type

Font is the device sans-serif (Roboto on stock Android) at weights 400 and 500; Inter Bold stays only
for the OMERON wordmark. Every TextView takes one role from `styles.xml`; layouts never set
`textSize`, `fontFamily` or `textStyle`.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| headlineSmall | 24sp / 32sp | 400 | subreddit and user name on their header |
| titleLarge | 22sp / 28sp | 400 | app bar title, dialog title, sort sheet title |
| titleMedium | 16sp / 24sp | 500 | post title, list item headline, settings row title |
| titleSmall | 14sp / 20sp | 500 | comment author, metric value |
| bodyLarge | 16sp / 24sp | 400 | post self-text, comment body, settings category label |
| bodyMedium | 14sp / 20sp | 400 | post info line, settings row summary, comment info |
| bodySmall | 12sp / 16sp | 400 | domain, flair text, metric label, timestamps |
| labelLarge | 14sp / 20sp | 500 | button label, tab label, chip label |
| labelMedium | 12sp / 16sp | 500 | bottom navigation label, award count |

Minimum text size is 11sp. Layouts must survive font scale 1.3: fixed heights only on icons and images.

## Space

Base unit 8dp. Tokens in `dimens.xml`: `space_4`, `space_8`, `space_12`, `space_16`, `space_24`,
`space_32`, `space_48`, `space_56`, `space_64`. Nested units 2dp, 4dp, 6dp and 10dp may appear raw
inside a component. Page margin 16dp under 600dp, 24dp above. Card gap 8dp, label-to-control gap 4dp.

## Controls

| Control | Value |
|---|---|
| Touch target | 48dp by 48dp minimum, including icon buttons |
| Filled, tonal, outlined and text buttons | 40dp high, full-radius shape, labelLarge, 24dp horizontal padding |
| Icon button | 40dp visual, 48dp target |
| Text field (outlined) | 56dp high, 1dp outline, 2dp when focused, label never truncated |
| Chip | 32dp high, radius 8dp, 1dp outline or tonal fill, labelLarge |
| Card (filled) | radius 12dp, elevation 0dp, background surfaceContainer; not used for posts |
| List item | 56dp one line, 72dp two lines, 88dp three lines, 16dp side padding |
| Bottom navigation bar | 80dp high, 5 destinations, active pill 64dp by 32dp primaryContainer, active icon in marker |
| Marker tick | 24dp vector, 3dp round-capped stroke in marker, on a 36dp circle of black at 62%, 8dp inset from the picture's top end |
| Top app bar (small) | 64dp high, titleLarge, icons in 48dp targets |

One primary action per screen, filled button, always on screen. Destructive actions (log out,
delete profile, clear history) use an outlined button in `error` colour and a confirm dialog.

## Colour

The theme follows the system setting (light, dark, or the AMOLED choice in Settings).
Light scheme (paper): primary `#A8321A`, onPrimary `#FFFFFF`, primaryContainer `#FFDBCF`,
onPrimaryContainer `#3A0A00`, secondaryContainer `#F2DED5`, onSecondaryContainer `#2C160E`,
surface `#F7F3EE`, onSurface `#1F1B17`, onSurfaceVariant `#5E554C`, surfaceContainerLow `#F2EDE7`,
surfaceContainer `#EDE7E0`, surfaceContainerHigh `#E4DDD4`, outline `#857A70`, outlineVariant
`#D6CCC2`, error `#BA1A1A`, errorContainer `#FFDAD6`, marker `#E2451B`.
Dark scheme (warm black): primary `#FF8A65`, onPrimary `#3A0E00`, primaryContainer `#5A1E0B`,
onPrimaryContainer `#FFDBCF`, secondaryContainer `#4A3A33`, onSecondaryContainer `#F2DED5`,
surface `#141210`, onSurface `#EEE8E0`, onSurfaceVariant `#B7AEA3`, surfaceContainerLow `#1A1714`,
surfaceContainer `#1E1B18`, surfaceContainerHigh `#29251F`, outline `#9C9188`, outlineVariant
`#4A433C`, error `#FFB4AB`, marker `#FF6B3D`. AMOLED keeps the dark roles with surface `#000000`,
surfaceContainerLow `#0D0C0B`, surfaceContainer `#121110`, surfaceContainerHigh `#1E1B18`.
The marker colour (`?attr/omeronMarker`) appears only on the marker tick, the marker loop in the
filmstrip, a selected row's outline and box, and the active bottom-bar icon.
Rules: `#` literals live only in `colors.xml`; layouts and styles use `?attr/` or `@color/` roles.
Status colour (NSFW, spoiler, stickied, OP, locked) lives on pills and swatches, never on body
text. Identities (subreddit, user) carry a filled block in the raw colour. Contrast 4.5:1 for text
under 18sp, 3:1 for larger text and icons.

## Layout of the Feed page

Small top app bar (64dp): menu icon, OMERON wordmark, sort icon, layout icon. Below it the posts,
16dp of space between them, no cards and no dividers. Card layout, top to bottom: edge print
(community and age as bodySmall in onSurfaceVariant, 16dp side padding), media full width with
square corners at 4:3 (portrait pictures up to 4:5), title as titleMedium at 16dp padding, author
as bodyMedium, flair chips, action row of 48dp targets (vote with score, comments with count, save,
more) and the domain as bodySmall. A saved post shows the marker tick on its picture. A text post is
a full-width surfaceContainerHigh frame with the title in headlineSmall. Compact rows keep their
thumbnail (8dp corners) and show the tick on it; the filmstrip grid marks saved tiles with the
marker loop. Bottom navigation bar 80dp with Feed, Popular, Search, Multis, You. Error and login
states use the info bar: text wraps beside a 40dp text button.

## Layout of the You page

Small top app bar titled You. The profile row (40dp avatar, profile name with a down chevron,
Reddit status) opens the switch-profile sheet. Then list items: Saved posts, Saved comments and
History with their counts, Communities and Multis with a "Manage" supporting line, and after 24dp
of space Settings and About. The drawer keeps only the community filter, multis and communities.

## Layout of the Post page

The post card as on the Feed, then comments. Each comment: 16dp side padding, 8dp vertical, depth
indent 8dp per level with a 2dp outlineVariant rail, author titleSmall, info bodyMedium, body
bodyLarge. "Load more" rows are 48dp text buttons.

## Layout of the Search page

Outlined text field 56dp at 16dp margin, tabs (Posts, Communities, Users) with labelLarge and a
3dp primary indicator, then the same post cards as the Feed. Community and user results are
72dp two-line list items with a 40dp avatar.

## Layout of the Settings page

Category labels as bodyLarge in primary at 16dp side padding. Rows are 56dp one-line or 72dp
two-line list items with titleMedium and bodyMedium summary; switches right-aligned. The Reddit
account row is the page's primary action: the whole row opens Log in and its summary shows the
current state. No filled button, because Preference rows cannot host one without a custom layout.

## Gates

A JVM test (`DesignTokenGateTest`) scans `res/layout/**/*.xml` and fails on: any `dp` literal
other than 0, 1, 2, 4, 6, 10; any `sp` literal; any `#` colour literal; any `android:text` or
`android:hint` literal that is not a `@string` reference. Lint keeps `HardcodedText` and
`ContentDescription` at error. A second test (`CopyGateTest`) scans `strings.xml` for the words
in `docs/copy.md`. Both run in `./gradlew :app:testReleaseUnitTest`.
