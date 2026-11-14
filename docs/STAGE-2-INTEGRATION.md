# Stage 2 — Android storage integration

## Implemented in batch 1
- Android media permission declarations for images, videos, audio, Android 14 selected visual media, and legacy read access through API 32.
- Runtime permission request flow from the home screen.
- MediaStore metadata scanning for image, video, and audio collections.
- Storage Access Framework folder picker with persisted read-only tree permission.
- Recursive metadata indexing for files inside the selected tree using DocumentFile.
- Local SQLite metadata database. Only content URIs and metadata are stored; source files are not copied or changed during indexing.
- The home screen exposes the real permission and indexing actions and reports the indexed count.

## Current boundaries
- The indexer does not request MANAGE_EXTERNAL_STORAGE and does not bypass Android scoped storage.
- Access to Android/data and other protected locations is not promised.
- SAF indexing covers only the tree explicitly selected by the user.
- This batch does not yet implement full file browsing/actions, project/study semantic grouping, installed-app inventory, duplicate detection, or on-device device testing.
- A successful CI build verifies compilation only; it cannot validate the Samsung device permission dialogs or real media results.

## Verification
The pull request runs the Android assembleDebug workflow. The workflow result must be checked before merging this batch.
