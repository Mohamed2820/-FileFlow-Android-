# Stage 1 — Native interface completion

## Included
- Arabic-first Jetpack Compose application shell.
- Home dashboard and eight archive categories.
- Category navigation with list/grid layouts and alphabetical sorting.
- Search over category names and descriptions.
- Dedicated recent, favorites, and duplicate-file states.
- Light/dark appearance control.
- Settings explain local processing, original-file protection, and the later Android permission stage.
- Empty states explicitly avoid fabricating file names, counts, or storage capacity.

## Verification boundary
The GitHub Actions workflow compiles the Android debug variant. It does not publish an APK; APK delivery is stage 3. Physical-device behavior and Android storage access are tested in stages 2 and 4.
