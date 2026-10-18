System: material3

Every number below is copied from Material Design 3 (androidx material3 1.4.0 tokens,
m3.material.io). This file wins over any layout; change it in the same commit as the UI.

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
| Card (filled) | radius 12dp, elevation 0dp, background surfaceContainer |
| List item | 56dp one line, 72dp two lines, 88dp three lines, 16dp side padding |
| Bottom navigation bar | 80dp high, 4 destinations, active pill 64dp by 32dp secondaryContainer |
| Top app bar (small) | 64dp high, titleLarge, icons in 48dp targets |

One primary action per screen, filled button, always on screen. Destructive actions (log out,
delete profile, clear history) use an outlined button in `error` colour and a confirm dialog.

## Colour

Light scheme, seeded from the Omeron cobalt `#1D3FBF` with the Material tonal palette:
primary `#1D3FBF`, onPrimary `#FFFFFF`, primaryContainer `#DEE1FF`, onPrimaryContainer `#00105C`,
secondaryContainer `#DDE1F9`, onSecondaryContainer `#161B2C`, surface `#FBF8FF`, onSurface
`#1A1B21`, onSurfaceVariant `#45464F`, surfaceContainerLow `#F5F3FA`, surfaceContainer `#EFEDF4`,
surfaceContainerHigh `#E9E7EF`, outline `#767680`, outlineVariant `#C6C5D0`, error `#BA1A1A`,
errorContainer `#FFDAD6`.
Dark scheme: primary `#B9C3FF`, onPrimary `#002386`, primaryContainer `#0A2EA8`,
onPrimaryContainer `#DEE1FF`, secondaryContainer `#414659`, onSecondaryContainer `#DDE1F9`,
surface `#121318`, onSurface `#E3E1E9`, onSurfaceVariant `#C6C5D0`, surfaceContainerLow `#1A1B21`,
surfaceContainer `#1E1F25`, surfaceContainerHigh `#292A2F`, outline `#90909A`, outlineVariant
`#45464F`, error `#FFB4AB`. AMOLED theme keeps surface `#000000` and surfaceContainer `#121318`.
Rules: `#` literals live only in `colors.xml`; layouts and styles use `?attr/` or `@color/` roles.
Status colour (NSFW, spoiler, stickied, OP, locked) lives on pills and swatches, never on body
text. Identities (subreddit, user) carry a filled block in the raw colour. Contrast 4.5:1 for text
under 18sp, 3:1 for larger text and icons.

## Layout of the Feed page

Small top app bar (64dp): menu icon, OMERON wordmark, sort icon, layout icon. Below it a vertical
list of filled cards with 16dp side margin and 8dp gap. Card content, top to bottom: header row
(40dp avatar, subreddit as titleMedium, author and age as bodyMedium), media at 12dp side inset
with radius 12dp and height 200dp (card layout) or edge-to-edge (feed layout), title as
titleMedium at 16dp padding, flair chips, action row of 48dp targets (vote with score, comments
with count, save, more) and the domain as bodySmall. Bottom navigation bar 80dp with Feed, Popular,
Multis, Subs. Error and login states use the info bar: text wraps beside a 40dp text button.

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
account row holds the single primary action of the page (Log in) as a filled button.

## Gates

A JVM test (`DesignTokenGateTest`) scans `res/layout/**/*.xml` and fails on: any `dp` literal
other than 0, 1, 2, 4, 6, 10; any `sp` literal; any `#` colour literal; any `android:text` or
`android:hint` literal that is not a `@string` reference. Lint keeps `HardcodedText` and
`ContentDescription` at error. A second test (`CopyGateTest`) scans `strings.xml` for the words
in `docs/copy.md`. Both run in `./gradlew :app:testReleaseUnitTest`.
