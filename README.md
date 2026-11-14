# FileFlow Android

Native Android file archive and file manager built with Kotlin and Jetpack Compose.

## Product rules
- Index files without moving or modifying originals.
- Keep indexing, metadata extraction, and classification on-device.
- Organize study material by subject; programming files by project and extension; media by type and available metadata.
- Keep unknown files in an uncategorized group rather than guessing.
- Use Android MediaStore and Storage Access Framework for user-authorized access.
- Do not assume access to protected locations such as Android/data.

## Delivery sequence
1. Complete the native Android interface.
2. Implement Android permissions, MediaStore/SAF integration, indexing, and file operations.
3. Build and publish a real APK through GitHub Actions.
4. Install and test on a physical Android device, fix defects, and retest.

## Stage 1 interface
- Native Kotlin + Jetpack Compose application.
- Arabic-first home screen, category navigation, search across category names/descriptions, list/grid presentation, and category sorting.
- Dedicated archive states for all files, recent files, favorites, duplicates, and each smart category.
- Settings screen with light/dark appearance control and clear privacy/access explanations.
- No fabricated device capacity, file counts, file names, or scan results. Real device data appears only after Android integration in stage 2.
- Original files are not moved, edited, or deleted during indexing.

## Android project
- Application ID: `com.mohamed.fileflow`
- Minimum Android version: Android 8.0 (API 26)
- Compile SDK: 35
- Java: 17
- Kotlin: 2.0.21
- Android Gradle Plugin: 8.7.3

## Build validation
A GitHub Actions workflow runs `:app:assembleDebug` on pushes and pull requests. This workflow is for compile validation only; APK distribution is reserved for stage 3.
