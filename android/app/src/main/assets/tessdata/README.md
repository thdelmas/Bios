# Tesseract trained data (operator-provisioned)

Lab-report OCR (Phase 10, `com.bios.app.labocr`) uses Tesseract via tess-two.
Tesseract needs one `<lang>.traineddata` model per language. These are
multi-megabyte binaries and are **deliberately not committed** to this public
repository — they bloat the repo and the APK and are redistributed under their
own licence.

At runtime `TesseractLabOcrEngine` copies every `*.traineddata` it finds in
this `assets/tessdata/` folder into private storage and enables OCR for those
languages. If none are present, lab-report scanning is unavailable and the
review screen says so — it never fails silently.

## Adding the models for a build

Drop the language files Bios ships aliases for (Catalan, Spanish, English)
into this folder before assembling a release:

```
app/src/main/assets/tessdata/cat.traineddata
app/src/main/assets/tessdata/spa.traineddata
app/src/main/assets/tessdata/eng.traineddata
```

Use the `tessdata_fast` models (smaller, plenty accurate for printed reports):
https://github.com/tesseract-ocr/tessdata_fast

The `*.traineddata` files are git-ignored (see this folder's `.gitignore`), so
a local drop never gets committed. For CI/release, fetch them in the build
pipeline rather than vendoring them here.
