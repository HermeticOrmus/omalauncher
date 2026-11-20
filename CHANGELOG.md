# Changelog

The most important changes in each release. The Play Store notes for a release
are in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
Dates are when the build was uploaded to Play.

## Omalauncher Ormus (the ormus branch, on top of upstream)

What this fork adds to the release below it; none of it is in the Play build.

- The terminal key bar is the fork's own: a first page for answering an agent behind the sessions tiles' attach (esc,
  1, 2, 3, the arrows, enter, shift+tab, ctrl+c, a mic, and detach, which is `hc attach`'s ctrl+\ and acts on a long
  press, since outside `hc attach` that key quits the shell's program), the stock keys on the second, and the shell's
  ctrl keys and hidden symbols on the third. The mic dictates with the phone's recognizer: tap, speak, tap; "one" or
  "uno" (to four) press that number for an agent's menu, "no", "cancel", "para" or "stop" press esc, which refuses,
  and "send" or "enviar" press enter. Anything else is pasted as one paste (so a digit in it never answers a menu),
  and sent when it ends with "send" or "enviar"; "No, send." is still esc. The dictation stops when its window goes to
  the background. Widgets get `pasteText(...)` (owl 23, this fork only). The key at the end of the top row turns the
  page; a long press goes back to the agent page. In a Linux terminal window the bar stays on with the keyboard down
  too, as Mercurio's does, unless a hardware keyboard is in.
- On the comms tiles, a tap on the card opens its screen, picked, with the earlier messages, the reactions and
  the field to write back; WhatsApp opens from a key there (or from the card's body). The tile's card shows the one
  message before their last when it still fits.
- The comms tiles' screen card shows the messages before their last one (the feed's `context`), a row of
  reactions on their last message (👍 ❤️ 🙏 😂) and a field to write back without opening WhatsApp; each goes
  through sg's reply tool (ormus-feeds `bin/reply`), on the chats the feed says take them (`send`, `react`). A text
  that did not go stays in the field. The model's quick replies may now hold an emoji (owl 18 for the field).
- The tiles reach the agents through herdr-core alone, as the old herdr TUI is gone: a tap on an agent in the
  sessions and board tiles, and on the Mercurio tile, attaches the launcher's terminal with `hc attach` over ssh
  to its machine (Mercurio opens no herdr-core terminal from a launcher's tap yet, and its Herdr path would start
  a TUI session); the Mercurio tile reads sg's sessions feed instead of each machine's TUI; the kiosk starts its
  agent with `hc run`.

- The today tile 2.0 (`80-today.owl`, owl 18) reads the meetings from the phone's own calendars
  (`android.calendar`): every account Android syncs, read on the phone, with no sign-in on another
  machine and no meeting sent over the network. The phone's
  `~/.config/omalauncher/today-calendars.json` (a JSON list of calendar names) says which calendars
  show; only its text entries count, and without it, or with one that names no calendar, every
  calendar does. All-day events are left out. Until the
  calendar is allowed, the header says "tap to read the calendar"; a tap asks Android. A meeting whose
  place holds a Zoom, Teams, Meet or Webex link joins that link alone on a tap; any other opens in the
  calendar app. The practice of the day still comes from the today feed.
- Only the today tile reads the phone's calendars. Android's calendar permission is the whole app's,
  and the widgets folder is in the Linux guest's home, so `android.calendar` reads as not allowed in
  every other widget file and in a changed today tile (`Ormus.calendarAllowed`: the file's name and its
  SHA-256 must match the bundled tile; `Ormus.gate` hands every other one the calendar as `gated`, a
  value that stays open, so its source does not ask again), and the guest's `omalauncher calendar` answers that Linux
  can't read it (`Ormus.GUEST_CALENDAR`). A changed tile never takes the shipped tile's values
  when it replaces it (`Ormus.mayAdopt`), says "only the shipped tile reads the calendar", and no
  owl-check picture is drawn of a tile that reads the calendar.
- The full screens of sessions (and each board), comms (and each copy), usage, pull requests and the
  kiosk refresh when their list is pulled down (`onPull`, owl 17).
- The money and markets tiles (money's face, owed, people, pipeline, markets) left the bundle: their
  feeds are off, so a new install no longer seeds tiles that read nothing. On an update, each of
  them that is unchanged since the launcher wrote it is deleted from the phone's widgets folder, with
  its place on home and its saved values; a copy changed on the phone stays.
- On upstream 1.5.0 (versionCode 42). Upstream's 1.0 to 1.5 are below; the fork's changes follow upstream's
  new layout (home in `ui/home`, files in `storage`, widgets as full-screen widgets) and keep each space's
  page numbering in layout.toml.
- Settings ask nothing of upstream's news (`Ormus.UPSTREAM_NEWS`): no check of upstream's GitHub
  releases, whose "update" points at upstream's own app, and no fetch of its developer's
  announcements. This build updates through The Pass.
- The today tile (`80-today.owl` 1.1) no longer says "no meetings through tomorrow" when a
  calendar could not be read: its header turns red with "a calendar unreadable" (or how many) and
  the empty line says "meetings may be missing". A copy of the feed older than 20 minutes (Sun
  away, sg keeps the last one) says how old it is. "today · sg doesn't answer" is red too; while
  the tile loads it stays quiet.
- A Needs you tile beside Mercurio (`31-needs.owl`, page 2; on a page with no room, the first
  free spot). It reads the open Needs you cards from sg's store, the questions Claude sessions
  wait on, newest first, one at a time: the question, its project and host, how old it is. One
  numbered key per option answers the card in the store, marked `via: launcher`; a card with
  several picks, no options or more than four opens the cards page instead. Keys belong to the
  picked card: the first one as the list comes in, then the one he swipes to or taps "show" on, so
  a refresh cannot put another card under his thumb. An answered card shows no keys. A closed
  card, a refused phone or sg down each say so on the tile; a reply for a card off screen shows in
  the headline for two minutes. A tap on the card opens Mercurio.
- Two needs-you tiles, and a space that mirrors each workboard. The sessions tile is the builds:
  the agent that needs him, the agents at work, every workboard's agents on its screen; the chats
  left it for the comms tile. Each workboard space (raven, ormus, alqvimia, clients) opens on its
  board tile and, under it, its own comms tile (`13-comms-<board>.owl`: the comms tile with `only`,
  the board's chats alone; the loose chats stay on now's). The move to the workboards puts each
  comms copy under its board tile and the communications tile in more (its email drafts show nowhere
  else; a fresh home puts it there too). A board tile whose board the feed does not carry says so, on
  the builds and on the chats. The agents at work show under the needs-you card again: the tile
  counted an unpinned card as a stack of keys, with heights from an older card, and folded them away.
- The feed tiles read sg, the host that is always on. The six feeds built on Sun (today, waiting,
  money, raven, markets, fleet) reach the phone as sg's copy (ormus-feeds `bin/mirror-feeds`): while
  Sun sleeps the copy stays and each tile shows its age, as before when Sun answered late. The
  trainer's decks still come from Sun.
- The notification scorer's default context names nobody: the file on the phone
  (`~/.config/notif-score/context.md`) is where the people and what matters are written.
- Installs beside the Play build as dev.omalauncher.ormus, "Omalauncher Ormus", with
  the Aurum theme, Grok and the Sun bridge preloaded.
- Home has spaces, by workboard: swiping up goes to the next (now, raven, ormus,
  alqvimia, clients, mercury, more) and down to the one before; left and right turn
  a space's pages. Home on home goes back to now. `gestures.swipe-up = "spaces"` is
  the default. Every space has faces left of its first page, as upstream's now does,
  and layout.toml says each page's `space`, so editing it keeps the spaces apart.
  Seven dots at the top of the letter rail say which space this is; a long press on
  them lists the spaces to go to one. A home laid out under the spaces before (now,
  money, work, mind, world) is moved over once at the first start: each workboard
  space opens on its board tile, mercury takes the kiosk, the trainer and AI stars,
  more takes the pass, the fleet, today and the sun; owed, people, pipeline, markets,
  money's face, GitHub trending and the world's tiles leave the home (their files
  stay in widgets/.removed); the layout file as it was stays beside the new one as
  `layout.toml.before-workboards`.
- The recent apps strip starts with Mercurio and WhatsApp on every page, then the
  apps opened last.
- Niagara's alphabet down home's right edge: slide along it to a letter, then to an
  app, and let go to open it.
- The home layout as a file: layout.json, and layout.apply.json to rearrange.
- Widgets read each app's installed version (`version`, `versionCode` on
  `android.apps`; `launcherCode` on `android.system`), and `androidIntent(...)` opens
  a place inside an app.
- The pass tile updates an app in one tap: `install(url, sha256)` gets the APK,
  checks it against the hash The Pass lists, and opens Android's installer, which
  asks before anything changes. The tile reads The Pass on sg.
- A scrolling Column that fits its tile lets home's swipes through.
- A shipped widget or config that already holds exactly what this version ships is
  the launcher's again, so later versions update it: one an agent previewed before
  it shipped stayed stuck on that copy.
- Ormus widgets: sun, now playing, smart notifications, communications, projects,
  mercurio, trainer, the pass, owed, people, pipeline, fleet, raven (the reviews
  waiting longest first), today, markets; and money's face, left of money's first page.
- Sessions: who needs him first, one card at a time. Every agent that waits on
  him, on every machine, is a card: one line that says what the blockage is (the
  feed's summary: the dialog's heading and the agent's line about the command, a
  pick list's question, what the agent needs at a free prompt), where it is, and
  up to three numbered keys that move it forward (the dialog's answers, or his
  own replies at a free prompt, typed by sg from his registry); a tap anywhere
  else on the card opens the agent in Mercurio; swipe sideways (or tap the
  arrows) for the next. Each answer scores. The tile's card shows no command, but
  the first line of one whose summary is the agent's own line about it; the
  screen keeps the question, what a yes would run and why, the whole command and
  every answer the dialog offers.
  A yes for a command goes only with the whole command in the feed; a command the
  screen does not show whole has no keys, and he opens the agent. Under the card,
  the chats that wait and the agents at work; the screen has every workboard,
  project, agent and chat, in larger type. A tap on an agent carries its machine's tailnet names to Mercurio
  (`HERD_HOSTS`), so Mercurio opens the saved host that attaches that session
  whatever the phone calls it. sg tells the tile what moved (`owl-send ... set
  alert`) and it posts a notification; a quiet switch in its screen turns Do Not
  Disturb on and off. Kiosk: Mercury's picks, one at a time, a craft pick with the
  one move to take; swipe for the next, tap to open it.
- Comms: the WhatsApp chats that wait on him, one card at a time, under the sessions
  tile on now: who, their last line, how long ago, and his own quick replies as up to
  three numbered keys (the texts are his, in sg's registry; a key sends the chat's
  short name and the number, never text, and sg's reply tool sends the text through
  Periskope after its checks: that chat was offered in the feed's last run, one reply
  a chat a minute). The card says what sg answered (sent, you just replied, the chat
  moved on, sg's list is old, not confirmed); a chat he replied to shows that and an
  open key until the next read shows his word, and "sent" retires the keys at once; a
  tap on the body of the card (the header, the name, their line) opens the chat in
  WhatsApp, and no key sits under that handler; swipe for the next. A card is the chat
  and the text he read: a new text under a pinned card unpins it (its keys were for
  the text he read) and drops a stale answer. No answer from sg (ssh dropped) says "no
  answer: open the chat to check", never "nothing was sent"; a reply whose answer never
  comes frees the card after half a minute. Under the card, the other chats that wait;
  the screen lists every project's chats and the ones with no project. The tile fits
  its height (measured on the emulator at 420 dpi): at 6x6 the keys stack under the
  card's meta line with the dots row; at 6x4 the meta line folds into the header row
  (the age first), the three keys go side by side (a long label wraps once; a label
  that would not fit two lines leaves the lead key and a "more" key to the screen)
  and the dots go. 6x4 is the smallest size the card fits.
- Board tiles: one for each workboard (raven, ormus, alqvimia, clients), the sessions
  tile for that board alone, with its own counts, chats and moves. They are the
  sessions tile with three lines changed (`scripts/ormus-board-tiles.sh` makes them)
  and the gate pins them to it.
- AI usage (more, after the fleet): how much of each AI plan is used, on every account
  (Claude, ChatGPT, Grok), read every 5 min from sg's usage feed (built every 10 min).
  A row per account with its fullest window that has not reset by the phone's clock, a
  bar (red when full or limited, yellow from 80%) and when it resets ("use unknown"
  when the feed does not know); on a narrow tile a row drops the word "resets", the
  reset time, "as of " and then the label, and a stale row keeps the first whole words
  of its short text with "…", or its age alone when no word fits, so a row's age stays
  whole and a number is never cut. The account with the fullest window of all has its
  name in gold. A tap on an account opens the service's usage page; a tap on the tile
  opens a screen with every window, plan, note and reset time. The header says when sg
  does not answer, the feed is off, late or not the feed, and which machines were not
  reached; when sg does not answer, the last numbers it gave stay: the header gives
  their age once they are more than 30 min old, the screen's footer always. They come
  back too when sg said the feed is off and then stops answering. A test runs the tile
  on made-up feeds.
- AI usage shows each account's email, from the phone's own file
  `~/.config/omalauncher/usage-emails.json` (sg's feed holds none; `usage.py emails`
  on sg makes the file's map of account keys to emails). A row says the service and
  the email when its short text still fits beside it (a stale row's with its age),
  else the account's name, so the email never costs the number; the screen gives the
  email under each name. Without the file, or where its value is not text that
  holds an @, an account keeps its name; spaces around an email are dropped.

## Unreleased

## 1.5.0 (versionCode 38) – 2026-11-14

- The launcher comes in Russian, with the words Russian Android uses, and a Russian store listing.
- Android apps can take their icons from an installed icon pack such as Arcticons: style › icon
  pack; monochrome tints the pack's icons in the theme's accent (#120).
- Settings tells you when a newer Omalauncher is out: from Google Play, with an update button that
  opens Play's update screen (#190), or for the GitHub build, from GitHub releases, with a link to
  the release.
- Settings can show a message from us once, like a contest with a countdown, from
  announcements.json in the store repo; "don't show news again" turns them off.
- Owl 23: widgets pick how strong a haptic is (`haptic("light")`, `"medium"`, `"strong"`), seek
  what's playing (`media("seek", s)`), change its speed and press its notification buttons
  (#287, #291). now playing's progress bar is a slider where the player allows seeking.
- Fixes: 3D models no longer flicker where their parts overlap, and one with a long thin sliver no
  longer runs the launcher out of memory; pacman works again after the app was killed mid-install
  (#273); the GitHub build's update button no longer crashes on a phone without a browser.

## 1.4.0 (versionCode 37) – 2026-11-12

- Linux commands that look at many files run 2-8x faster with "fast system calls" on (pacman -Qk
  8x, ls -lR 6x, du 5x, grep -r 3x, git status 2x): a small library inside Linux answers stat,
  access, readlink, read-only open and similar calls itself instead of stopping for proot each
  time.
- Sunset heist: a brake button under the right thumb, and cars bump instead of passing through
  each other: police, race rivals, the takedown car and parked cars are solid like traffic.
- Agents building widgets lose fewer turns: owl-check draws a new preview after owl-send changed a
  state and says which cells the tile has on home. The bundled game examples drop boomtown, sky
  forge and last outpost.
- Home no longer redraws for every line a Linux install logs, and typing and drawing do less work.
- Fixes: AirPods no longer crash the launcher when their link stops.

## 1.3.0 (versionCode 36) – 2026-11-11

- Owl 22 brings 3D: a Canvas `scene(camera)` draws lit, fogged and textured models from cubes,
  spheres, cylinders, cones, planes, terrain, your own meshes or OBJ files, with vector math for
  games. `onTouch` sees every finger at once, and a Shader can sample images and take array
  uniforms. New example apps: sunset heist, a 3D city to drive, run missions in and escape the
  police, and canyon run, a low-poly flight down an endless canyon; plus boomtown, a city builder,
  sky forge, a shooter with a hangar of upgrades, and last outpost, tower defense.
- The store has two sections, widgets and apps; faces are now called full-screen widgets, with a
  tab of their own in the store.
- Apps that ask for 60 frames a second keep them however long they take to draw, and widgets that
  read fixed values draw faster.
- today, what's new, ntfy and github inbox refresh when their list is pulled down.

## 1.2.0 (versionCode 35) – 2026-11-10

- Owl 21: `android.motion` gives the phone's tilt from the gyroscope and accelerometer, and
  `every frame { }` runs each frame with `dt`. New example apps: space bounce, a tilt-to-fly
  arena shooter, and sky raid, a pixel-art plane shooter over five stages.
- Voice: the orb is a glossy blob in shades of its colour whose edge moves while it listens,
  speaks or works, more as you talk louder. Replies use the text-to-speech engine picked in
  Android's settings without a launcher restart, and voice settings say which voice the default
  is, like "default (Germany)".
- Linux no longer looks uninstalled after the launcher restarts: the old /tmp is deleted in the
  background, and while Linux starts, settings and the Linux screen say checking.
- Fixes: a widget Shader whose loops take over 1000 turns a pixel isn't drawn (the store review
  turns it away), so it can't freeze the screen; past 256 Wayland clients at once new ones are
  turned away, so a runaway Linux program can't run the launcher out of memory.

## 1.1.0 (versionCode 34) – 2026-11-10

- Linux commands under proot run 2-30x faster: proot keeps itself and processes making many
  syscalls on one fast CPU. The Linux screen has a switch for it, on by default, for phones or
  programs that do better without it.
- Apps with notifications show a dot on their icon, on home, on folders and in the drawer;
  Android's own dot settings decide, and it needs notification access. Owl 20: `dot` on the
  apps of `android.drawer` (#239).
- The terminal's input can be a normal text field, on by default, so the keyboard's suggestions,
  glide typing, dictation and clipboard work there: `terminal.keyboard-suggestions`, also in
  style › terminal (#271). Tapping a suggestion that changes letters already typed corrects the
  word (#251).
- Foldables: home stays upright on a cover screen, and the cover and unfolded inner screens can
  have layouts of their own, as `[cover]` and `[unfolded]` sections in layout.toml (#256, #257,
  #259).
- Fixes: turning a phone sideways no longer sends home back to page 1; a notification whose
  extras can't be read and a layout.toml tile placed past its own columns no longer crash the
  launcher.

## 1.0.0 (versionCode 33) – 2026-11-08

- Phone folders: folders picked in Android's folder picker at the top of the permissions
  screen show in Files, where their files can be opened, shared, renamed,
  deleted and copied or moved to and from Linux; agents reach them with `omalauncher folders`,
  `ls`, `get` and `put`, and `omalauncher pick` opens Android's file picker. No storage
  permission: on Android 11+ Linux also has /sdcard, with the files it saved there itself, so
  what it puts in Download or Documents other apps see.
- Settings has a permissions screen listing each permission, whether it's on and what uses it.
- The `phone` command is `omalauncher` now (`phone` still works), with exit codes that say why
  there is no answer, and more commands: `notify` (a phone notification on its own
  "agents and scripts" channel), `share` (Android's share sheet for Linux files), `battery`,
  `version`, and the phone folder commands. It answers in about 35 ms rather than 170.
- Linux follows the phone's DNS servers, a VPN's included, so tailnet and LAN names resolve;
  8.8.8.8 and 1.1.1.1 stay the fallback. An edited resolv.conf is left alone.
- Owl 19: `fps:` says how many frames a second a widget's animation needs; koi, the owl and the
  controls panel run at 10 to 30 rather than 60, which saves battery while home shows.
- Home can open on any page, a face too: `home.home-page` in style.toml (#270, #225).
- Claude Code no longer warns about cross-session messaging on every start in Linux.
- Fixes: an exited terminal tile stays stopped until tapped (#130); a widget's notification no
  longer replaces one from before the launcher restarted; an agent whose install finishes
  after the agents screen closed shows up right away; Voice keeps its log in ~/.cache.

## 0.20.0 (versionCode 31) – 2026-11-05

- Omarchy 4.5: themes and configs come from v4.5.0. Jade Horse and Starship join the themes
  and Osaka Jade leaves them; whoever has it picked keeps it. The boot intro videos stay out
  of the download.
- Coding agents: Qwen Code, MiniMax Code, Kiro CLI, Kilo, Devin CLI, Amp and Command Code, as
  in Omarchy 4.5's agent list. Voice talks to all of them but Amp (which has no ACP) over ACP,
  and to Antigravity (`agy acp`) too.
- Agents install one at a time, as two at once failed on mise's lock; a second one waits
  ("waiting…"). Removing an agent that's running stops Voice's copy first, or says to close
  it, instead of seeming to do nothing.
- Agents and scripts in Linux can ask for the phone's location, calendar and AirPods with
  `phone location`, `phone calendar [days]` and `phone airpods [control [value]]`, as far as
  the launcher was already allowed them; nothing asks for a permission. AGENTS.md tells
  agents what system they're in (Arch under proot, no systemd, pacman/yay and mise, what
  opens where). `x-www-browser` opens links in Android's browser.
- Voice does less work while it talks: only the conversation redraws, and the orb animates
  only while it shows.
- Fixes: an agent queued to install no longer stays "waiting…" after the Agents screen closes.

## 0.19.0 (versionCode 30) – 2026-11-04

- Browser tiles: a widget or face can show a web page in a tile of its own, drawn by
  Android's System WebView, https only and only at hosts written in the file, script off
  unless `js: true`. `onNavigate`, `onTitle` and `onProgress` follow the page and
  `web("back" | "forward" | "reload" | "stop")` steers it; a page's script waits while
  the launcher is behind another app or the screen is off (#255).
- A widget or face can hold the home screen still with `lock: mode == "play"`: while it's
  true the page turn, the swipe up and the long press stop, and a chip, Back or the widget
  stopping always give the screen back (#254).
- An `Input` takes `lines: 6`: what's typed wraps, the field grows up to that many lines and
  then scrolls, as a chat's message box. With `onSubmit`, Enter sends and Shift+Enter starts
  a new line (#248).
- Widgets can show and change AirPods: owl 18's `android.airpods` has each bud's and
  the case's battery, which buds are in, the listening mode and the settings, read
  over the AirPods' own link as Apple's devices do, and `airpods(...)` switches
  noise cancellation, transparency and adaptive and turns Conversation Awareness
  and the rest on and off; `examples/airpods.owl` shows them. `airpods("request")`
  from a tap asks for the Bluetooth permission, and only on Android 17 (16 QPR3 on a
  Pixel), which it needs; elsewhere `supported` is false and nothing asks.
- Widgets can know roughly where the phone is: owl 18's `android.location` gives an
  approximate place (a kilometre or two), only once `location("request")` from a tap
  has allowed it, and only while the widget shows. The bundled today widget has "use my
  location" for its weather; a typed city still wins.
- Widgets can show the phone's calendars: owl 18's `android.calendar` lists the next 14
  days of events, local and synced, each time of a repeating one on its own, without
  cancelled ones or those you declined, once `calendar("request")` from a tap has
  allowed it. All-day events run midnight to midnight wherever the phone is, each event
  has its color, and `calendar("open", e)` opens one in the calendar app.
  `examples/calendar.owl` shows the next events.
- Voice talks to Oh My Pi (over ACP) and Pi (over its RPC mode) too.
- Fixes: a widget command printing hundreds of megabytes no longer runs the launcher out of
  memory (its output stops at 4 MB); many regex matches in one widget update can't add up to
  a freeze; Voice no longer crashes in landscape with the keyboard up; a widget's preview is
  saved off the main thread.

## 0.18.0 (versionCode 29) – 2026-11-01

- Voice talks to Codex (over `codex app-server`), GitHub Copilot, Kimi and Cursor (over ACP)
  too, besides Claude Code, Grok and OpenCode. Its agent list has every one of them, and
  picking one that isn't installed installs it, with the install's log and a retry in place of
  the conversation.
- Voice has its own settings, from a gear in its top row: the language it listens and reads
  replies in, the voice for that language and how fast replies are read (1.25x by default).
  What Voice says itself is in that language too.
- When the agent says it isn't signed in, Voice offers [ log in ], which opens the agent in a
  terminal of its own.
- Voice's orb is a lit disc in the theme's accent, rings leaving it as you speak and a comet
  circling it while the agent works. On a tablet the conversation is centred and no wider
  than 560dp.
- Speech goes online for a language with no offline model, uses a recognizer picked over
  Google's (such as Transcribro), skips a stand-in one (FUTO Voice Input) for the speech app's
  own speak-now screen, and understands the three-letter languages Samsung's text-to-speech
  names.
- A job's log offers the whole log with a [ whole log ] button.
- Fixes: a widget preview whose window the system refused to remove and listing apps when a
  system call fails partway no longer crash the launcher; Voice brought back in a new process
  finds the agents again.

## 0.17.0 (versionCode 28) – 2026-10-31

- Voice: a spoken conversation with Claude Code, Grok or OpenCode as a launcher screen of its
  own, opened from the drawer, the AI bar's mic (holding it still dictates) or `omarchy-voice`.
  Each open starts a new conversation and the last one can be resumed; a type button switches
  to typing and reading, with nothing said aloud. It speaks and understands the launcher's six
  languages.
- The launcher comes in German and Simplified Chinese.
- Settings offers to make Omalauncher the default home app while it isn't one.
- today and fetch shrink to 2x2, and on a short or squeezed tile today's date takes one line so
  the forecast isn't cut off.
- On a slow tablet the drawer opens without a pause and swiping past a face with shaders runs
  smoothly.
- The store side by side opens on the grid's first entry rather than an empty pane.

## 0.16.0 (versionCode 27) – 2026-10-29

- Settings has a language: the phone's, or one the launcher has its text in. A pick brings
  every open screen back in it, and widgets' `strings { }` and `lang()` follow it too.
- The launcher comes in Spanish and Brazilian Portuguese (also for phones set to Portugal's),
  with the drawer, today, fetch, owl and key bar widgets translated. Play installs carry every
  language, not just the phone's. Indonesian uses the words Android itself does.
- A Linux job's log keeps up with progress bars instead of piling them up, holds 200 lines,
  and opens whole when tapped, red only where something went wrong, with [ copy ] for a bug
  report (#240).
- pacman installs bubblewrap again, so glycin and the GTK apps needing it upgrade (#243).
- With frosted glass, the recent apps and the prompt bar frost too, on home, in the landscape
  rail and over the open drawer.
- Owl 17: `ws(url)` listens to a WebSocket server, `send()` talks back and `on x each { }`
  runs for every message; `pin:` on `http`, `post` and `ws` trusts a LAN server's own
  certificate by its SHA-256; a scrolling Column or Grid takes `onPull` for pull to refresh.
- Fixes: picking a language no longer opens a terminal; home's long-press menu wraps its
  buttons instead of cutting the last one off; rendering a theme no longer empties a folder
  Omarchy's `current/theme` links to.

## 0.15.0 (versionCode 26) – 2026-10-28

- The launcher speaks Indonesian: with the phone set to Indonesian, all of its text, the
  drawer, the today, fetch and owl widgets and the terminal key bar's spoken key names are
  in Indonesian.
- Widgets can carry their text in more than one language: owl 16's
  `strings { en { ... } id { ... } }`, read as `str.name`, show the phone's language's with
  English where it has none, and texts can take values. `lang()` is the phone's language for
  APIs. Widgets recompile when the phone's language changes, and agents publishing a
  one-language widget are asked whether to add English.
- Coding agents take the theme as on Omarchy: Claude Code has an Omarchy theme to pick in
  /theme, Pi starts in it, and OpenCode follows the terminal's colors; running sessions
  recolor when the theme changes.
- A widget, face or app added from the store can be removed from its page in the store, and
  each store always has its installed tab.
- The empty page right of home's last invites a widget, with create with the agent and browse
  store, instead of a one-line hint.
- The store's built-in widgets, a terminal and a tile per coding agent, are one row that
  scrolls sideways.
- At a large Android font size, buttons that would squeeze a label to a letter a line go under
  it, in settings, the style screen and Files' selection bar, and settings' updates scroll with
  its rows.

## 0.14.0 (versionCode 25) – 2026-10-27

- Tapping a now-playing widget brings up the player's own screen, like a web app's
  window when one installed from a browser plays, rather than a new browser tab.
- A drawer can leave the keyboard down as it opens, for scrolling the apps rather
  than searching them: owl 15's `keyboard: false` in `drawer.owl`. Tapping the
  bar brings it up.
- Images go into a terminal as the path they're saved to in Linux's /tmp, which
  opencode and Claude Code attach: tap a screenshot or copied image in Gboard's
  clipboard suggestions, or paste an image copied on Android (Chrome's Copy image).
  Copied text pastes as before. The terminal key bar's new photos key, beside paste, opens Android's
  photo picker and pastes the photos picked the same way, several at once; above it, a `|` key. Owl 15's
  `pickImages()` does it in a key bar of your own.
- The today widget's weather screen switches between °C and °F when you tap the
  temperature's °C or °F, and keeps the scale picked; until then it follows the country.
- A widget can turn home to another page: `launcher("page:0")` shows the face left of
  page 1, as Super+1…9 does for pages 1 to 9, so a tile can lead to the face that shows more of it.
- Widgets can take dictation: owl 15's `speech("toggle")` turns the microphone on and off from a
  tap, and `source said = android.speech` has what's heard, as you speak and once you're done, so a
  widget can show it, run a command with it, or type it into a terminal with `on said.final`. The
  phone recognizes the speech, as for the prompt bar, and only the text reaches the widget (#84).
- Widgets can show how fast the phone charges: owl 15's `source p = android.power every 2s` has the
  battery's current in mA, its watts, the mAh it holds, Android's time to full and what the charger
  offers, the same units and sign on every phone (#226).
- Home's empty pages at either end can be turned off in style.toml or the Style screen:
  `home.empty-page-left = false` drops the one left of the leftmost page, and
  `home.empty-page-right = false` the one after the last page of tiles (#202, #205).
- GTK3 and wxWidgets apps (PrusaSlicer) leave out their own title bar and window buttons,
  which only took space on a phone; apps with their own header bar keep it (#215).
- A Linux window too big for the screen is drawn shrunk to fit, keeping its shape, instead of
  running off the right and bottom edges, and touches map back to it (#218).
- Fixes: a widget node with `clip: true` and a negative radius crashed home; a widget home
  placed on another page, or one moved there, stopped updating two pages away until the
  launcher restarted; a GTK app with a header bar kept asking how to decorate its window.

## 0.13.0 (versionCode 24) – 2026-10-25

- Faces can draw to the screen's edge: owl 14's `edge: true` runs a face under the
  status bar, and in landscape under a camera cutout or navigation bar at the side;
  `screenTop`, `screenLeft` and `screenRight` say how much those cover. Other faces
  now keep clear of a navigation bar at the side, which they ran under.
- A store face can have several pages, installed side by side. Each page stays
  yours to remove; an update replaces only the pages still on home, and the face's
  store page adds a removed one back. Publishing a face lets your agent add your
  other faces as its pages.
- Owl 14's `ripple: false` leaves out the ripple on a node that shows its press
  itself.
- Fixes: a face's bottom jumped after every reload or data update; the store's
  widgets/faces/apps toggle stayed round with square corners.

## 0.12.2 (versionCode 23) – 2026-10-25

- A store update no longer throws away changes made to a widget, app or face:
  settings' update leaves an edited one out and lists it as edited, and its store
  page says updating replaces the changes, as "update anyway" (a Play review).
- Home no longer crashes measuring or updating a tile on a page the pager had set
  aside ("measure/Apply is called on deactivated node", Play, 0.12.1).
- Animated Canvases draw about twice as fast.
- AGENTS.md and CLAUDE.md in the Linux home folder link the launcher's source.

## 0.12.1 (versionCode 22) – 2026-10-24

- Owl apps, panels, faces and screens ripple where they're pressed; a widget's tile
  on home and the key bar don't.
- Home's bar on a pinned owl app offers edit and share, as the drawer does (#197),
  and an app in a folder gets home's bar on a long press.
- A tap on a terminal's prompt line brings the soft keyboard back (#100). Pressing
  Home on home with nothing open goes back to page 1 (#201).
- Faster and lighter: home has its widgets in about half the time after start and
  shows complete rather than filling in; pages either side are made ahead; animated
  widgets, terminal tiles and Linux app windows draw at most 60 frames a second, and
  a terminal tile stops drawing while home is out of sight; the drawer's app list,
  owl apps' icons and the media source work off the main thread.
- Linux setup stops with an error naming the kernel fix on custom ROM kernels where
  it hung at "Initializing pacman keyring" (#105), and setup failing at once (too
  little space) no longer crashes the launcher.
- Fixes: Shift on a hardware keyboard with FlorisBoard in terminals and Linux windows;
  the home terminal tile stuck on its red file name after a relaunch; the first home
  after onboarding opened on page 2 on short screens; the today tile labelled cached
  forecast days wrong after midnight; android.battery said "on battery" when plugged
  in but not charging; system updates printed "Directory not empty" removing an old
  agent version (#196).

## 0.12.0 (versionCode 21) – 2026-10-23

- controls.owl, a control center for swiping down on home: the phone's switches,
  brightness, volume and where it plays, Do Not Disturb and the torch with their
  levels, what's playing, the next alarm and the notifications (reply, act, snooze,
  swipe away). Pick it in settings › gestures. Owl 13 adds what it uses:
  `settings(...)`, `dnd(...)` with `dndAllows`, `torch(level)`, the radios and more
  in `android.modes`, `android.sim`, `android.network`, `android.volume`'s output,
  notification actions, replies and snoozing, `drag` and `onDragEnd`.
- Widgets' glass is a setting: an opacity slider and frosted glass in Style
  (widgets.opacity, widgets.frost), terminal widgets included; the drawer and panels
  set how much they cover home. Owl 13's `tileOpacity` and `tileFrost` let a face or
  a widget's own cards match.
- Keyboards and gamepads reach the whole launcher: arrows or the D-pad move between
  tiles, recent apps, the bar, the drawer, panels, settings and what takes taps in a
  widget, with an accent ring; Enter opens, holding it or Menu long-presses, Esc or B
  goes back. Super+D opens the drawer, Super+T a terminal, Super+Down the swipe-down
  panel.
- Codex runs commands again (its sandbox failed on every command under proot).
  Agent updates delete the old versions and npm's cache, which left every upgraded
  agent installed twice.
- The store takes widgets, faces and apps up to 1 MB; an installed app opens from
  its store page; store cards decode their previews at the size they're drawn.
- Fixes: the mic crashed with some voice assistants (ChatGPT's) as the phone's
  speech recognizer; store previews were blank on Android 10 and 11; very large
  widget images ran out of memory; a widget too big for a share link crashed the
  share screen (it's shared by QR code now); a frost that comes out NaN crashed the
  launcher; the first key on setup could change the theme.

## 0.11.0 (versionCode 20) – 2026-10-21

- The terminal key bar is an owl file: keybar.owl, which you or an agent can edit
  like drawer.owl, with .stock-keybar.owl as the fallback. Settings › style shows
  whether it's stock, edited, has errors or removed, and sets its gaps and border
  width. Owl 12 adds `keybar "…" { }` files, `pressKey`, `typeText`, `latch`,
  `android.modifiers`, `onPress`/`onRelease` and `repeats`.
- Owl 12 also: `secret` state, kept encrypted with Android's keystore and sent only
  to the server written out in its request's URL; Input `password`; post's
  `method:` PUT, PATCH and DELETE; `toBase64`/`fromBase64`; `screenTop` for apps
  without a status bar. `requires { pacman: [] }` no longer makes a widget need Linux.
- Widget requests follow redirects themselves and send headers and bodies only to
  the server they name; a response over 1 MB fails rather than being cut; a failed
  post says why in err. A runaway regex or a huge value can't freeze or take down
  the launcher, and a stream's command stops when its widget lets go of it.
- Installing Linux checks for free space before downloading (it needs about 3 GB)
  and can be cancelled. Without Linux, a widget's edit and other agent buttons offer
  to install it instead of failing.
- Fixes: the status bar's icon colour after a fold or rotation; settings' subtitles
  in the White theme; a store widget that doesn't fit shows the page it went to; the
  store's previews use far less memory; Files handles links into /proc or out of
  Linux and opens .owl files in its editor; symbols the launcher's font lacks; the
  edit bar of a widget in home's top row.

## 0.10.1 (versionCode 19) – 2026-10-20

- The store's kind toggle showed a "panels" tab with no store behind it, and tapping
  it crashed the launcher; it lists widgets, faces and apps again.
- Going home while a backup or device transfer ran crashed (the process runs with a
  plain Application then); home now closes until the process restarts.
- Backups and device transfers leave out Linux (the rootfs and its setup records)
  and Omarchy's downloaded release: a restored phone shows Linux as not installed
  instead of a broken rootfs, and the backup fits Android's 25 MB quota. Before,
  a phone with Linux got no backup at all ("Size quota exceeded").

## 0.10.0 (versionCode 18) – 2026-10-20

- The app drawer is an owl file: drawer.owl, which you or an agent can edit, with
  .stock-drawer.owl as the fallback when it's removed or doesn't compile. Owl 11
  adds `panel "…" { }` files that a swipe on home opens (up: the drawer, down: from
  the top), picked in settings › gestures or style.toml, with the prompt bar's
  `query`, `enter`, `onSubmit` and `onKey`; `android.drawer`, `appAction`,
  `workApps`, `shortcuts()`, `rank` and `strongMatch` for the drawer's apps, search
  and menus. The Kotlin drawer is gone.
- Home's layout is a file: ~/.config/omalauncher/home/layout.toml lists home's
  pages in order and where every widget, app and folder sits. Moving things on the
  phone rewrites it, and an agent (or you) can edit it to move, resize, pin or
  reorder; problems show on home and in errors.txt next to it. A widget's `page:`,
  `order:` and `size:` only place it the first time.
- Owl 11 also: Column and Grid `lazy: true` and `reverse`, Image `label` for screen
  readers, Text `minLines`, `chunk(list, n)`, apps without a status bar or title
  row, and a Column in a Row or Flow is as wide as its content.
- owl review warns about widgets that poll more often than they need to.
- Faster: a Shader animates only when it reads time; Canvas text layouts are cached.

## 0.9.0 (versionCode 17) – 2026-10-18

- Faces: an owl file can be a whole home screen (`face "name" { }`, owl 10), left of
  page 1 like a stock launcher's feed, as many as you like, with an empty one
  inviting the next. The store has a faces tab (minimal, pixel, terminal); settings ›
  pages lists faces and tile pages to drag into order, edit or remove. A face can
  hide the status bar, runs under the navigation bar with `screenInset`, and takes
  both halves of an unfolded foldable.
- Owl 10: text `spans`, `fontWeight`, `letterSpacing`, linear and radial gradient
  backgrounds, `frost` (frosted glass over the wallpaper), fonts of a widget's own
  (asset, URL or Linux file), styled Canvas text, `Popup`, Input `underline: false`,
  `onLongPress`, `android.agents`, `android.recent`, `open(key)`, `ask(text)` and
  Image `icon:<key>`. A Text in a Row or Flow is as wide as its text. `android.apps`
  updates as apps change instead of being polled.
- Example widgets: the start page is redesigned; the ai bar rebuilds the prompt bar
  and recent strip in owl.
- Fixes: sharing a face or an app to the store; dragging pages in settings; a NaN
  font size, a damaged font file or `cmd("/")` no longer crash the launcher;
  `round()` of very large numbers.

## 0.8.1 (versionCode 16) – 2026-10-17

- Home's grid has 4 to 9 columns (6 by default); changing it moves every tile onto
  the new grid. A pages row keeps one page where a foldable would show two (#141).
- Style settings preview home and widgets as they change, and the terminal preview
  is drawn in place instead of starting a foot. The terminal font is a dropdown.
- A font for the launcher's text and widgets: Android's monospace, or one of Linux's
  monospace fonts.
- Local network: on Android 17+ a settings row asks for ACCESS_LOCAL_NETWORK, so
  widgets and Linux can reach devices like a printer or Home Assistant.
- Store updates for widgets and apps show in settings' system updates box, with
  pending packages and agents listed one per line.
- The home menu has the settings gear at its top right, like the drawer.
- Owl 9: `parseTime`, `fmtAgo`, `fmtNum`, `fmtTime` with a time zone and language,
  `random`, `shuffle` and `hash`; `set`, `remove`, `merge`, `reduce`, `scan`,
  `groupBy`, `toJson`, `capitalize`, `asin`, `acos`, `flatten` to a depth and
  `on x first`; handlers have `if`/`else` and `let`. Numbers print as JavaScript
  writes them (1792231445.5, not 1.7922314455E9).
- Fixes: a widget's text field lifts its tile above the keyboard like a terminal;
  home no longer stays sideways in portrait after a landscape app (before Android 14);
  a widget filling its page shows its edit bar inside it; Linux setup no longer
  hangs at "Installing cliamp" on phones with a small app heap.

## 0.8.0 (versionCode 15) – 2026-10-17

- Apps: an owl file can be a full-screen app (`app "name" { }`, owl 8) that opens
  from the app drawer, with its own Recents card. Back goes back a level inside it.
  The store lists apps like Braincup's brain games, Yoga and Linux commands; an app's
  page swipes through its screens, drawn in your theme.
- Widgets and apps can show images from the web (SVG drawn as vectors), play sounds,
  send HTTP POSTs and read the installed apps (`android.apps`). Values in URLs go
  through `enc()`, and the store's reviewer sees each URL a widget can reach.
- GPU acceleration for Linux apps on Adreno 6xx and newer (Turnip). The patched Mesa
  now ships in the app for Adreno and Mali, so OpenGL apps run on the GPU through
  zink, and windows with MSAA (PrusaSlicer's G-code viewer) no longer crash.
- Folders can have a name, set in place in their header.
- Home can hide the status bar.
- App search matches names with their accents stripped.
- The store marks widgets added in the last three days with a "new" badge and tab,
  and has more categories (sport, finance, media, games, pets). Its pages and
  settings pages opened from another have a back chevron.
- Themes installed from the catalog show its screenshot in the theme list.
- OpenCode 2 installs and updates again: agent installs and system updates update
  mise first and add node for OpenCode.
- Prompts start Antigravity and GitHub Copilot in interactive mode, and Antigravity
  in plan mode for untrusted prompts.
- Fixes: submitting to the store no longer crashes without a browser, links from
  Linux keep opening after an error, and turning the GPU on waits for package installs.

## 0.7.2 (versionCode 14) – 2026-10-14

- Text files open in the launcher's own touch-first editor, in the Omarchy theme,
  which is also Linux's `$EDITOR`: Android's selection and keyboard, line numbers,
  find, word wrap and a key row above the keyboard. Each file gets its own Recents
  card, and `git commit` waits until you close the message.
- Files is an Omarchy-style panel with its own icon, and can create a new file.
- Settings open in their own window with a Recents card, and come back as you left
  them after going home or to a terminal mid-install. The launcher's own screens
  open and close without animations.
- Terminals no longer flicker when the soft keyboard opens or closes.
- New coding agents from Omarchy's list: Oh My Pi, Ori, Muse Code and Kimi.
- Linux no longer installs lazygit, and fastfetch stays out of the drawer.
- Checking for system updates in settings records the check time again.
- The widget store's refresh button spins while it reloads.

## 0.7.1 (versionCode 13) – 2026-10-13

- Websites installed from a browser (Install app, or Add to home screen) show up
  on the home screen and in the app drawer, like other apps' shortcuts to home.
  Browsers didn't offer to install them before.
- Long-pressing an Android app in the drawer, the recent apps or on home lists its
  shortcuts, such as Maps' "Navigate home", a chat or Chrome's incognito tab. Tap one
  to open it, or its [+] to put it on the home screen.
- Apps that change their own icon, like Duolingo or X, no longer leave a blank
  tile on the home screen that won't open; it shows the new icon and opens the app.
- The now playing widget's play button works again when it seemed dead: after a
  long pause (Spotify) it opens the app so playback resumes, and for a player that
  can't play in the background (YouTube without Premium) it opens the app. Tapping
  the tile opens the app again on Android 14 and later.
- After you change the phone's language, app names, home tiles and widget dates
  follow it.
- Widgets that only animate some of the time, like a backdrop drawn while music
  plays, stop redrawing while they're still.
- The koi in the koi pond widget turn in arcs instead of flipping round.
- Keeping the theme setup suggests (Tokyo Night) no longer loses it, and its wallpaper,
  the next time the launcher starts.

## 0.7.0 (versionCode 12) – 2026-10-13

- The style lives in `~/.config/omalauncher/style/style.toml`, which you or an agent can edit
  from Linux; changes show within a second, and settings it can't read show in a banner.
  `defaults.toml` next to it lists every setting. Your earlier style carries over.
- New style settings, like Hyprland's: gaps between widgets and to the edges, border widths
  and colors, the focused border, and screen panels' gaps and borders.
- The status bar and the gesture bar can sit on the same glass as the widgets
  (style → system bars); they're bare by default.
- Widgets can show the next alarm and the phone's modes (Do Not Disturb, battery saver,
  ringer, torch), turn the torch on, share text, buzz, and draw app icons (owl 7).
- yay in a terminal no longer asks for root's password, and AUR builds no longer print
  "exec: -v: invalid option".

## 0.6.0 (versionCode 11) – 2026-10-12

- Apps from a work profile show up next to your own, marked with a briefcase,
  and dimmed while work apps are paused.
- Cloned apps carry the clone mark instead of the briefcase, from Android 15 on.
- With a work profile, the drawer splits into personal and work tabs; swipe sideways to switch.
- The work tab pauses work apps and turns them back on.
- Widget screens can raise the keyboard as they open, with `Input { focus: true }`.
- Swipe up on home to open the app drawer, like stock Android; settings → gestures
  can turn it off.
- The app list starts at the top, Linux apps first; closing the drawer clears the search.
- Widgets can follow your corner style, from square to round (owl 6).
- The widget store only takes widgets that pass values into commands safely, with `sh()` (owl 6).

## 0.5.0 (versionCode 10) – 2026-10-11

- Add your own themes, like Omarchy's, and your own picture as the background.
- Themes became the style screen, which also sets the terminal's font and text size.
- The terminal shows and types Chinese, Japanese and Korean, and characters like æ or ö.
- Tablets and wide screens get larger text and use the extra room.
- Terminal tiles are in the widget store.
- Coding agents no longer update themselves.

## 0.4.0 (versionCode 9) – 2026-10-10

- Share widgets as a QR code or a link, and open shared widgets in the launcher.
- Before adding a shared widget, see what it installs, every command it runs and
  what looks risky, or hand it to your agent to review.
- Publish your own widgets to the widget store without a GitHub account.

## 0.3.0 (versionCode 8) – 2026-10-09

- Folders on the home screen.
- Two home pages side by side on foldables.
- Linux apps and Android share the clipboard.

## 0.2.0 (versionCode 7) – 2026-10-08

- New app icon.
- GPU acceleration for Linux apps on Mali GPUs, like the Pixel 9a (Settings › linux).
- Publish your own widgets to the widget store with a long press.

## 0.1.3 (versionCode 6) – 2026-10-08

- Fix installing coding agents on some phones, like the Pixel 10.

## 0.1.2 (versionCode 5) – 2026-10-08

- Home works in landscape.
- Linux windows show up in Recents under their own name and icon.

## 0.1.1 (versionCode 4) – 2026-10-07

- The app list moved into the bar, with recently used apps above it.
- Widgets can open full screen.
- Fixes for many crashes from widgets, Linux apps and uninstalled apps.

## 0.1.0 (versionCode 1–3) – 2026-10-01

- First release: Omarchy themes and wallpapers, home screen widgets and the
  widget store, the Android app drawer, and optional Arch Linux with Linux apps,
  terminal widgets and coding agents.
