System: Material 3 copy rules plus the Omeron vocabulary below. `CopyGateTest` enforces the word rules.

## Vocabulary

| Word | Means | Never say |
|---|---|---|
| post | a submission in a community | thread, submission |
| comment | a reply under a post | |
| community | a subreddit, written `r/name` | subreddit (except in `r/` names), subscription |
| join, leave | add or remove a community from your list | subscribe, unsubscribe, follow |
| multi | a named group of communities | multireddit |
| feed | posts from your communities | timeline |
| profile | a local set of communities, history and saved items | user (a user is a Reddit account) |
| saved | posts and comments you kept | bookmarks, favourites |
| history | posts you opened | |
| account | the throwaway Reddit login | session, cookie, token |
| backup | a file with your profiles, communities and saved items | export (except Reddit data export) |
| filter | narrows a list by typed text | |
| sort | the order of posts or comments | |
| layout | card, gallery, compact or filmstrip | view mode |
| viewer | the full-screen media screen | lightbox |
| menu | the left-hand navigation panel | drawer, sidebar (sidebar is a community's info) |
| award, flair | Reddit terms, kept as is | |

## Rules

1. A label is a noun phrase of at most three words and never a question.
2. A button is a verb plus its object, one to three words, in sentence case, never truncated. "Yes" and "No" are not buttons.
3. A hint is one line of at most twelve words that states the effect. It exists only when the label is not enough.
4. A status line says what happened, then what the user can do.
5. An error names what failed and offers one action. "Something went wrong" alone is not allowed.
6. One calm voice: no jokes, no exclamation marks, no "please", no "oops", no "sorry" unless the app is at fault.
7. Sentence case on all text, titles and tabs included. Brand names and Reddit terms such as NSFW and OC keep their casing.
8. Implementation words never appear: session, cookie, token, API, scrape, cache, OAuth, HTTP, endpoint, exception, null, JSON, WebView, DataStore, thread, source, instance, Teddit. Say what the user sees instead.
9. A number the user cannot act on stays out of the primary UI.
10. Material 3: errors appear next to the thing that failed and say how to fix it; general failures use a basic dialog; button labels describe the action and do not wrap.

Reddit login, stated the same way everywhere: the user logs in once with a throwaway account, Omeron keeps only what it needs to read Reddit as that account, and Log out removes it.

## Gate exceptions

`backup_location_explanation_restore_reddit` keeps the word "json" because the user must pick a file named `mine.json`. The entry sits in `CopyGateTest.allowList` with that reason.

## Changes

333 strings existed: 112 changed, 2 deleted, 219 left. Five were added (four button labels and one clipboard label).

| Strings (key) | Change | Reason |
|---|---|---|
| `sort_time_week/month/year/all` | This Week to This week and so on | sentence case |
| `user_post_karma`, `user_comment_karma`, `user_cake_day` | lower-case second word | sentence case |
| `menu_share_link/copy_link/share_post/remove_history/remove_saved` | sentence case | sentence case |
| `menu_open_link` | Open Externally to Open in browser | says what happens |
| `subreddit_button_subscribe/unsubscribe`, `dialog_unsubscribe_message` | Join, Leave, Leave r/x? | one word for one action |
| `dialog_yes`, `dialog_no` (deleted) | replaced by Delete profile, Delete multi, Clear history, Leave, Open private link, Open original, Cancel | buttons are verb plus object |
| `dialog_*_multireddit_*`, `multireddit_*`, `add_to_multireddit`, `multireddit_new/edit_title`, `drawer_section_multireddits`, `empty_multireddits`, `manage_empty_multireddits`, `manage_section_hidden_multireddits` | multireddit to multi | vocabulary |
| `dialog_create/rename/delete_profile_*`, `dialog_clear_history_*` | sentence case, object added to button, "Are you sure" removed | button rule, plain question |
| `network_retry_message` | Something went wrong to Could not load this page. Check your connection. | error names failure and action |
| `login_required_message`, `preference_reddit_account_logged_out`, `dialog_logout_*` | throwaway account, what Omeron keeps, what Log out removes | no cookie or session wording |
| `settings_source`, `dialog_reddit_source_*`, `preference_reddit_source_*` | Source to Reddit connection; options Official Reddit data and Reddit web pages | banned words source, scraping |
| `preference_privacy_enhancer_*`, `instance_*`, `redirect_mode_*`, `dialog_privacy_enhancer_*` | Instance to Site address, ON to On, enabled to On | banned word instance, sentence case |
| `preference_night_mode_*` | Light Theme to Light, Follow System to Follow system | label length, sentence case |
| `settings_left_handed_mode_summary`, `settings_backup_and_restore_summary`, `app_description` | shorter; subscriptions to communities | hint length, vocabulary |
| `dialog_policy_disclaimer_*`, `snackbar_policy_disclaimer_action` | API wording and "Please" removed | banned words |
| `backup_loading_success_*`, `backup_loading_failed_*` | no exclamation marks or "Please"; failure names the step | calm voice, error rule |
| `backup_button_previous/lets_go/finish`, `backup_location_*` | Back, Start, Done, Choose file | button rule, calm voice |
| `notification_download_*`, `toast_download_started` | Download Manager to Downloads; shorter status | label length |
| `snackbar_permission_storage_*` | plain storage access wording | no "external storage" |
| `dialog_subreddit_*`, `dialog_media_not_found_body`, `empty_data*`, `drawer_no_subscriptions` | subreddit to community, shorter bodies | vocabulary |
| `comment_more`, `comment_more_error`, `post_load_discussion`, `layout_toggle`, `fragment_preferences_label` | Load more, Could not load, View all comments, Change layout, Settings | button rule, one name per screen |
| `navigation_drawer_open`, `manage_section_hidden_communities`, `last_refresh`, `update_up_to_date`, `update_check_failed`, `update_banner_get`, `about_github_button`, `profile_name_*_error`, `profile_already_exists_error`, `profile_blank_error`, `backup_location_explanation_*` | drawer to menu, Updated, no contractions, GitHub casing, errors name the fix | vocabulary, error rule |

Kotlin literal moved to resources: the clipboard label `link from Reddit` is now `link_clipboard_label`.
