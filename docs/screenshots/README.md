# Public screenshots

These are real OneZip 1.01 JavaFX scene captures. The archive browser opens a synthetic `Example.zip` with a folder and sample Java, CSV, text, PNG, Markdown, JSON, and CSS files.

The archive-browser background illustration was supplied by the project owner. It is shown with a warm gold accent (#91643f), light mode, image blur radius 3, and control transparency 85%. No desktop, notifications, credentials, or user-profile paths are included. The source illustration is not bundled with the application. The `C:\Archives` destination in the compression screenshot is synthetic text entered in the actual form.

Reproduce with `scripts/PublishScreenshots.java` against the built JAR, isolated `onezip.settings.dir` / `onezip.settings.legacy-dir`, an output directory, and a fresh fixture directory. An optional third argument supplies the background image and updates only the archive-browser screenshot. Image blur is an application effect, not Windows Acrylic.
