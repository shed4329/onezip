# Settings

All current preferences use one UTF-8 Java properties file:
`%LOCALAPPDATA%\OneZip\settings.properties`. The same file is shared by the JAR
and portable app. Moving the portable directory does not move user settings.
Passwords are never saved here. Output paths, filenames and per-operation codec
options are not silently replaced by global settings.

Editable keys:

- `appearance.dark`: `true` or `false`, default `false`.
- `appearance.language`: `zh-CN`, `zh-TW`, `ja`, `en`; default `zh-CN`. Restart
  applies language changes; the current operation keeps its session language.
- `appearance.accent`: HTML color such as `#0078d7`. Invalid colors fall back
  to the default. The UI adjusts foreground contrast separately.
- `appearance.background.path`: local image path. In properties syntax Windows
  backslashes are doubled, for example `D:\\Pictures\\wallpaper.jpg`.
- `appearance.background.enabled`: whether the chosen image is visible.
- `appearance.background.blur`: Gaussian blur applied to the image inside the
  app. This is not Windows Acrylic or whole-window transparency.
- `appearance.background.blurRadius`: integer 0..63 logical pixels, default 24.
  The personalization slider previews changes immediately and saves after a short
  debounce or when leaving the page. Zero has no visible blur. Turning blur or
  the image off retains the chosen radius for re-enabling and restart. Text and
  icons are unaffected; damaged numbers fall back to 24 and out-of-range values
  are clamped.
- `appearance.controls.transparency`: integer 0..100, default 35; values are
  clamped, invalid numbers use the default. This changes surface fills, not text.
- `cursor.path`: custom cursor image, empty for the system cursor.
- `extraction.smart`: `true` or `false`, default `false`. A single file or shared
  top-level folder extracts directly; mixed roots use an archive-named subfolder.
  Existing destination files are never overwritten.

Close the app before manual editing. Saves have stable key ordering and no
timestamp, retain unknown keys, and use a temporary sibling plus replacement.
Atomic replacement is used when the filesystem supports it. This is not a
power-loss durability guarantee. Multiple running processes use last-writer-wins
semantics; they do not merge concurrently edited snapshots.

If an existing file cannot be parsed/read, the app uses defaults and shows a
warning in settings. It refuses to overwrite the damaged file. Back it up,
repair it or move it aside, then restart. A failed save leaves the previous
in-memory values and settings file intact.

On first use, absent settings are migrated from the former temporary-directory
TXT files. Those originals are retained. `legacy.bootMode`, `legacy.bootTheme`,
`preview.disabled` and `comment.charset` can remain as historical keys; they do
not enable missing GUI features and can be removed after migration.

Test isolation: `-Donezip.settings.dir=<directory>` and
`-Donezip.settings.legacy-dir=<directory>` override locations for that JVM.
