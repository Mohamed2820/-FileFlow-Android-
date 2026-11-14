# FileFlow Android

Smart, non-destructive file archive and file manager for Android.

## Core principles

- Index files without moving or modifying their originals.
- Keep classification and metadata processing on-device.
- Use a hierarchical archive: study subjects, programming projects, media, applications, empty files, and an uncategorized area.
- Classify audio using available media metadata, filenames, and folder names.
- Provide conventional file-manager actions only with explicit user confirmation and Android-granted access.

## Android access model

The application will use Android's Storage Access Framework and MediaStore through a native Android integration. Browser-only directory selection is not considered equivalent to full-device access. Access to protected locations such as `Android/data` is not assumed.

## Development status

Project repository initialized. Implementation will be added in small, verifiable stages, with Android permissions and device capabilities tested separately from the web interface.
