# Stage 2 — Android storage integration

## Implemented
- Runtime permission flow for media access on supported Android versions.
- MediaStore metadata indexing for images, videos, and audio.
- Storage Access Framework folder selection with persisted read access.
- Recursive indexing of the user-selected folder tree, with per-folder/document error isolation and URI cycle protection.
- Local SQLite metadata index. Indexing does not copy, move, rename, edit, or delete source files.
- Browsing indexed image, video, audio, and other-file categories.
- Opening an indexed item with an Android viewer through its content URI and read grant.
- Global search across indexed file names and MIME types from the application search field.
- Explicit empty-state messaging when a category has no indexed results.

## Access and privacy boundaries
- The app does not request MANAGE_EXTERNAL_STORAGE and does not bypass scoped storage.
- Android/data and other protected locations are not promised or accessed by bypass.
- MediaStore access is limited by granted permissions; Android 14+ selected-photo/video access can produce partial results.
- SAF access is limited to the folder tree explicitly selected by the user.
- The app stores metadata and content URIs locally; source file contents are not uploaded.

## Not claimed as complete in this stage
- Semantic study-course and programming-project grouping requires user-confirmed folder roots/rules and is not inferred from filenames alone.
- Installed-app inventory, persistent favorites/recent history, duplicate analysis, and destructive file operations are not part of this storage-connection batch.
- Device-specific permission dialogs and real file opening still require stage 4 testing on the target phone.

## Verification
- GitHub Actions `assembleDebug` verifies that the Android debug variant compiles.
- CI success is not equivalent to installation or device-level validation.
