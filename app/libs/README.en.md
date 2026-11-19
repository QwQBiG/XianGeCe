# Android build dependencies

[简体中文](README.md) · **English**

From the project root, run the following command with Python 3 to prepare the two Android libraries (AARs). The first run downloads about 161 MB from public upstream sources. Later runs verify and reuse the local files.

```sh
python3 tools/prepare-android-dependencies.py
```

The script checks each download's size and SHA-256 digest before placing the library in this directory. For an offline check, run `python3 tools/prepare-android-dependencies.py --check`. If it reports a corrupt local file, move that file out of this directory and rerun the preparation command. Download URLs, sizes and hashes are recorded in the [dependency manifest](dependency-manifest.json).

- **OpenCV 4.10.0** uses the Android AAR published on Maven Central. [OpenCV artifact](https://repo.maven.apache.org/maven2/org/opencv/opencv/4.10.0/)
- **sherpa-onnx 1.13.4** starts with the official Android AAR. The project obtains ONNX Runtime separately from Maven Central. The preparation script removes the four architecture-specific copies of `libonnxruntime.so` from the sherpa archive and retains the original contents of its other entries. The resulting `no-ort.aar` has a reproducible digest. [sherpa-onnx release](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4)

With JDK 17 and Android SDK Platform 37 installed, run `./gradlew :app:assembleDebug`. On Windows, run `gradlew.bat :app:assembleDebug`. Gradle obtains the remaining build dependencies and plugins from Google Maven, Maven Central and the Gradle Plugin Portal. [Third-party notices](../../THIRD_PARTY_NOTICES.en.md)
