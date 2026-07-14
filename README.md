# SwiftPDF 📖

A fast, clean PDF **reader** for Android (no editing). Built with Kotlin.

## Features
- ⚡ **Thunder-fast rendering & scrolling** — pdfium engine + drag-handle for instant jumps through 1000+ page books
- 🔍 **Full-text search** — indexes the document once, then instant results with page snippets; next/previous match navigation
- ⭐ **Favorites** — one-tap star on any book, dedicated Favorites tab for quick access
- 📌 **Resume reading** — always reopens at your exact last page
- 🌙 **Night mode** — inverted page colors for comfortable night reading
- 📄 **Go to page**, pinch-zoom, double-tap zoom
- 🕘 **Recents library** — every opened PDF listed, sorted by last opened, with reading progress
- 📂 Opens PDFs from any file manager / WhatsApp ("Open with → SwiftPDF")
- Distraction-free: tap the page to hide/show the toolbar

## How to get the APK

### Option A — Android Studio (easiest)
1. Install Android Studio → **File → Open** → select this folder
2. Let Gradle sync (first time downloads dependencies)
3. **Build → Build App Bundle(s)/APK(s) → Build APK(s)**
4. APK appears at `app/build/outputs/apk/debug/app-debug.apk`

### Option B — GitHub Actions (no PC setup)
1. Push this folder to a GitHub repository
2. GitHub automatically builds the APK (see **Actions** tab)
3. Download `SwiftPDF-debug-apk` from the workflow artifacts

### Option C — Command line
```bash
./gradlew assembleDebug
```

## Tech
- Kotlin, Material 3, ViewBinding
- [android-pdf-viewer](https://github.com/mhiew/AndroidPdfViewer) (pdfium) for rendering
- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) for text search
- min Android 7.0 (API 24)

Note: search works on text-based PDFs. Scanned/image-only PDFs can't be searched (no text layer).
