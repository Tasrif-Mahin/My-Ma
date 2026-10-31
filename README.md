# My-Ma — Android project

A lightweight Kotlin + Jetpack Compose starter app by Mahinio.

## Features
- Dark/light theme toggle (saved locally)
- Clipboard URL detection when the app returns to the foreground
- Direct HTTP/HTTPS media downloads using Android DownloadManager
- Download status/progress screen
- Local video audio-track extraction to M4A using Android MediaExtractor/MediaMuxer
- No app backend and no account system

## Important limitations
- YouTube URLs are recognized and rejected for downloading. The app does not extract YouTube streams.
- The downloader expects a direct downloadable file URL. It does not parse web pages or adaptive HLS/DASH manifests.
- Audio extraction creates an M4A/AAC container, not MP3. Android does not include a built-in MP3 encoder.
- Download history currently reads Android DownloadManager records and filters likely media entries; this is a simple starter implementation, not a private database.

## Build with Android Studio
1. Install Android Studio with Android SDK Platform 35 and JDK 17.
2. Open this folder (`My-Ma`) in Android Studio.
3. Allow Gradle sync to finish.
4. Connect an Android device with USB debugging enabled, or create an emulator.
5. Press Run.

## Build APK
Debug APK:
```bash
./gradlew assembleDebug
```
Windows:
```powershell
.\gradlew.bat assembleDebug
```
Output:
`app/build/outputs/apk/debug/app-debug.apk`

Release APK:
```bash
./gradlew assembleRelease
```
Output:
`app/build/outputs/apk/release/app-release-unsigned.apk`

To distribute a release APK, create a signing key in Android Studio:
Build > Generate Signed Bundle / APK > APK, then follow the signing wizard.

## Online editors
Most browser-based code editors cannot build native Android APKs because they do not provide the Android SDK, Gradle environment, and signing tools. Use Android Studio on a computer, or a cloud development environment that explicitly supports Android SDK + Gradle builds.

## Package
`com.mahinio.myma`


## Build an APK online with GitHub Actions (no Android Studio)
1. Create a GitHub repository.
2. Upload the contents of this `My-Ma` folder to the repository root (the `app` folder and Gradle files should be directly visible at the repository root).
3. Ensure `.github/workflows/build-apk.yml` is also uploaded. If hidden folders are not included in your upload, create the file in GitHub using **Add file > Create new file** and enter the path `.github/workflows/build-apk.yml`; paste the workflow from this project.
4. Open the repository's **Actions** tab and enable Actions if prompted.
5. Select **Build My-Ma APK**, choose **Run workflow**, and run it on `main`.
6. When the run completes successfully, open the run and download the `My-Ma-debug-apk` artifact. Extract the downloaded artifact ZIP to get `app-debug.apk`.

The workflow installs JDK 17, Android SDK Platform 35, and Gradle 8.9 on a GitHub-hosted runner. It builds the debug APK and uploads it as a workflow artifact. A successful workflow is required before an APK exists.
