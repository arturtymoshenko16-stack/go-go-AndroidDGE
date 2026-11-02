# File Manager APK

A lightweight Android file manager built with the Storage Access Framework.

## Features
- Choose a folder and browse its contents.
- Navigate into folders and back to the selected root.
- Create and edit TXT, LUA, JSON, XML and Markdown files.
- Long-press entries to rename, copy files, or delete them.
- Open supported files with installed Android apps, including APK files through Android's package installer.

## Build
Open **Actions → Build File Manager APK → Run workflow**, or push a change under `manager-app/`.

Download the `FileManagerAPK-debug` artifact from the completed workflow run.

## Compatibility
- Minimum Android: 6.0 (API 23)
- Target SDK: 29, so folder access is granted by Android's folder picker instead of requesting broad storage access.
- Build: JDK 17, Gradle 8.7, Android SDK 35.

The app only accesses folders the user explicitly selects. APK installation is handled by Android; the app does not bypass Android's installation confirmation.
