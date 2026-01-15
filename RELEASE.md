# FLcasher GitHub Release Procedure

This document outlines the steps to prepare and release FLcasher to GitHub.

## 1. Clean up Repository (One-time setup)
Currently, some build files are tracked by git. You should remove them from version control to keep the repository clean.

Run the following commands in the terminal:
```bash
# Remove build artifacts from git tracking (but keep files on disk)
git rm -r --cached app/build
git rm -r --cached .idea
git rm -r --cached .gradle

# Commit the removal
git commit -m "chore: stop tracking build artifacts and IDE files"
```

## 2. Commit Pending Changes
Add any new files (like the Japanese translations and new icons) and commit your changes.

```bash
# Add new resource files
git add app/src/main/res/values-ja/strings.xml
git add app/src/main/res/drawable/printer_queue.png

# Add any other changes
git add .

# Commit
git commit -m "feat: add Japanese translations and printer queue icon"
```

## 3. Tag the Release
Create a tag for this version (e.g., v1.0.0).

```bash
git tag v1.0.0
git push origin v1.0.0
```

## 4. Build Release APK
Build the signed release APK.

```bash
./gradlew assembleRelease
```
*Note: You may need to configure signing keys in `local.properties` or `build.gradle.kts` if not already set up. If just for testing, debug build might be sufficient, or you can use the debug signature.*

## 5. Push to GitHub
Push your commits to the main branch.

```bash
git push origin main
```

## 6. Create GitHub Release
1. Go to the GitHub repository page.
2. Click on "Releases" -> "Draft a new release".
3. Choose the tag `v1.0.0`.
4. Title the release "v1.0.0".
5. (Optional) Upload the APK found in `app/build/outputs/apk/release/app-release-unsigned.apk` (or signed version if configured).
6. Click "Publish release".
