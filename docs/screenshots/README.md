# Public screenshots

`home.png`, `compress.png`, and `extract.png` are real OneZip 1.01 JavaFX scene captures, not generated UI mockups. The archive browser opens a synthetic `Example.zip` containing `Documents/example.txt`, `README.md`, and `notes.txt`.

Only the third screenshot uses an image background: `demo-background.png`, generated with the built-in imagegen tool specifically for this documentation. No personal wallpaper, desktop capture, notifications, credentials, or user-profile paths are included. The visible `C:\Archives` destination in the compression screenshot is synthetic text entered in the actual form.

The third screenshot uses dark mode, image blur radius 12, and control transparency 70%. The image blur is an application effect, not Windows Acrylic.

To reproduce, compile `scripts/PublishScreenshots.java` against the built all-in-one JAR, use isolated `onezip.settings.dir` and `onezip.settings.legacy-dir` JVM properties, and supply an output directory and a fresh fixture directory. An optional third argument is the background image path; this updates only the archive-browser screenshot.

Wallpaper generation mode: built-in imagegen, not CLI. Prompt:

> Create a clean abstract desktop wallpaper image for a software screenshot, wide landscape 16:10 composition. Refined flowing translucent blue and teal glass ribbons with a subtle violet accent on a deep charcoal navy background, soft atmospheric lighting and spacious low-detail center so white application text remains readable. Beautiful restrained Windows desktop aesthetic, no text, no logos, no characters, no identifiable objects, no screenshot or UI, no personal information. This will be used as an optional application wallpaper blurred behind an actual JavaFX archive file list. Save the generated wallpaper as an image artifact.
