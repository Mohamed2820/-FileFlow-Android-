# FileFlow Android

Native Android file archive and file manager.

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

## Current implementation
- Native Kotlin + Jetpack Compose Android application.
- Arabic-first interface with home, category navigation, search field, and settings.
- The interface does not fabricate phone files or storage figures. Device data remains unavailable until the Android integration stage.
- Application ID: `com.mohamed.fileflow`
- Minimum Android version: Android 8.0 (API 26).

## Build
The Android project uses Gradle and Android Gradle Plugin. The CI build workflow will be added in delivery stage 3.
