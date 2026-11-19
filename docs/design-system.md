System: material3
World: contact sheet (variation 4, chosen 2026-10-20)

Every number below is copied from Material Design 3 (androidx material3 1.4.0 tokens,
m3.material.io) unless it is listed under Departures. This file wins over any layout; change it in
the same commit as the UI.

## Departures

Deliberate changes to Material 3, applied everywhere: post media is full width with square
corners, never inside a card; posts are separated by space, not cards or dividers; the title sits
above the picture and the community, age, counts and actions sit on one tinted rebate line under
it; a saved post carries the marker tick on its picture; text posts are a full-width
surfaceContainerHigh frame with the title in headlineSmall; compact rows are a strip of square
pictures flush with the screen edge. Kept from Material without exception: 48dp targets, contrast, sp text
that follows font scale, system Back, window insets, standard controls.

## Type

Font is the device sans-serif (Roboto on stock Android) at weights 400 and 500; Inter Bold stays only
for the OMERON wordmark. Every TextView takes one role from `styles.xml`; layouts never set
`textSize`, `fontFamily` or `textStyle`.

| Role | Size / line | Weight | Used for |
|---|---|---|---|
| headlineSmall | 24sp / 32sp | 400 | title of a text post |
| titleLarge | 22sp / 28sp | 400 | app bar title, community and user name on their header, dialog title, sort sheet title |
| titleMedium | 16sp / 24sp | 500 | post title, list item headline, settings row title |
| titleSmall | 14sp / 20sp | 500 | comment author, metric value |
| bodyLarge | 16sp / 24sp | 400 | post self-text, comment body, settings category label |
| bodyMedium | 14sp / 20sp | 400 | post info line, settings row summary, comment info |
| bodySmall | 12sp / 16sp | 400 | domain, flair text, rebate line, compact info line, timestamps |
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
Light scheme (cobalt, chosen 2026-10-20 over the warm contact-sheet palette): primary `#1D3FBF`,
onPrimary `#FFFFFF`, primaryContainer `#DEE1FF`, onPrimaryContainer `#00105C`, secondaryContainer
`#DDE1F9`, onSecondaryContainer `#161B2C`, surface `#F7F7F9`, onSurface `#1A1B21`, onSurfaceVariant
`#4A4C55`, surfaceContainerLow `#F1F1F4`, surfaceContainer `#ECECF1`, surfaceContainerHigh `#E2E2E9`,
outline `#767680`, outlineVariant `#C6C5D0`, error `#BA1A1A`, errorContainer `#FFDAD6`, marker
`#2F55E0`.
Dark scheme: primary `#B9C3FF`, onPrimary `#002386`, primaryContainer `#0A2EA8`, onPrimaryContainer
`#DEE1FF`, secondaryContainer `#414659`, onSecondaryContainer `#DDE1F9`, surface `#121316`, onSurface
`#E4E4EA`, onSurfaceVariant `#A9ABB5`, surfaceContainerLow `#17181B`, surfaceContainer `#1C1D21`,
surfaceContainerHigh `#26272C`, outline `#90909A`, outlineVariant `#45464F`, error `#FFB4AB`, marker
`#7C97FF`. AMOLED keeps the dark roles with surface `#000000`, surfaceContainerLow `#0B0B0D`,
surfaceContainer `#121316`, surfaceContainerHigh `#1C1D21`.
The marker colour (`?attr/omeronMarker`) appears only on the marker tick, the marker loop in the
filmstrip, a selected row's outline and box, and the active bottom-bar icon.
Rules: `#` literals live only in `colors.xml`; layouts and styles use `?attr/` or `@color/` roles.
Status colour (NSFW, spoiler, stickied, OP, locked) lives on pills and swatches, never on body
text. Identities (subreddit, user) carry a filled block in the raw colour. Contrast 4.5:1 for text
under 18sp, 3:1 for larger text and icons.

## Layout of the Feed page

Small top app bar (64dp): menu icon, OMERON wordmark, sort icon, layout icon. Below it the posts,
16dp of space between them, no cards and no dividers. Card layout, top to bottom: title as
titleMedium at 16dp side padding and 8dp above and below; one row of status and flair chips, and
for a link post its domain as bodySmall, only when there is any; media full width with square
corners at 4:3 (a link post without a picture has no media block); the rebate line. The rebate
line is one 48dp row in surfaceContainerLow with 16dp start padding: "community, age, author" as
bodySmall on one ellipsized line (community in onSurface, weight 500), then score, comment count,
save and more in 48dp targets. A saved post shows the marker tick on its picture. A text post
replaces the title and media with a full-width surfaceContainerHigh frame with the title in
headlineSmall. Compact rows are at least 72dp high with 2dp between them: a 72dp square picture
with square corners flush with the left edge and the tick on it (a post without a picture shows
the community's colour and first letter), then the title as titleMedium on at most two lines and
one bodySmall line with community and age, score and comment count; the saved lists add a more
button to that line. The filmstrip grid marks saved tiles with the marker loop. Bottom navigation bar 80dp with Feed, Popular, Search, Multis, You. Error and login
states use the info bar: text wraps beside a 40dp text button.

## Layout of the You page

Small top app bar titled You. The profile row (40dp avatar, profile name with a down chevron,
Reddit status) opens the switch-profile sheet. Then list items: Saved posts, Saved comments and
History with their counts, Communities and Multis with a "Manage" supporting line, and after 24dp
of space Settings and About. The drawer keeps only the community filter, multis and communities.

## Layout of the Community and User pages

The header is a 144dp banner: the top app bar (64dp) and under it one 80dp row. The banner picture
fills the block behind both, drawn at 150 of 255 over surface with a fade to surface at the bottom,
so bar icons and text keep their contrast on any picture; without a banner the block is the marker
colour treated the same way. The row holds a 56dp round picture at 16dp, then the name as
titleLarge and one bodyMedium line in onSurfaceVariant (members for a community; karma and account
age for a user, on up to two lines), and for a community the filled Join button at the end. On
scroll the block folds into the small top app bar with a 42dp picture and the name; the numbers
and Join are not shown there.

## Layout of the Post page

The top app bar has two lines after a 40dp round community picture: the community as titleMedium,
then the author in primary and the post's age as bodySmall. The picture and community open the
community, the author opens the user. Then the post card as on the Feed, then comments. Each
comment: 16dp side padding, 8dp vertical, depth indent 8dp per level with a 2dp outlineVariant
rail, and one header line: a 20dp round author picture, author titleSmall, status pills, then
score and age as bodyMedium; a long author is cut, score and age are not. The picture and author
open the user. Body bodyLarge, 4dp under the header. "Load more" rows are 48dp text buttons.

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
