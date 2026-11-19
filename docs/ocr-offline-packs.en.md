# Offline OCR packs

[简体中文](ocr-offline-packs.md) · **English**

Updated September 11, 2026.

## Available models

| Option | Model | Three files, unpacked | Use |
| --- | --- | ---: | --- |
| Lightweight (default) | PP-OCRv5 Mobile ONNX | 21,509,645 bytes, about 21.5 MB / 20.5 MiB | Small download for screenshots, timetables and everyday text |
| Enhanced | PP-OCRv5 Server ONNX | 172,768,163 bytes, about 172.8 MB / 164.8 MiB | Larger detection and recognition models, with higher processing time and memory requirements |

Both models are official PaddlePaddle releases under Apache-2.0. Both run on the device, including the model family named “Server.” Accuracy has not been compared for these packs. Model size alone cannot establish which will read a given image better.

Download packs on demand or import a ZIP. Selecting a pack does not initiate a download or require you to install both packs. If the selected pack is missing, offline recognition prompts you to install it and keeps the selection. The application layer handles whether to try cloud recognition first. [Recognition pack guide](offline-resource-packs.en.md)

## Installation and compatibility on Android

- The lightweight pack keeps the ID `pp_ocrv5_mobile_zh_en_onnx` and the same app-private directory, `files/ocr-offline/pp_ocrv5_mobile_zh_en_onnx`.
- The enhanced pack uses the ID `pp_ocrv5_server_zh_en_onnx` and a separate sibling directory.
- Android stores the selection under `selectedPackId` in the local `ocr-offline-selection` SharedPreferences. If the ID is missing or unknown, the lightweight pack is the default. This preference store contains no download links, user content or keys.
- Existing lightweight ZIP files and `.installed` markers remain valid. Adding this option leaves earlier installations in place.
- Both ZIPs use `det.onnx`, `rec.onnx` and `rec.yml`. The files can be at the ZIP root or together in a single directory. Each filename must be unique.
- Before installing anything in the selected directory, import checks the size and SHA-256 hash of every file in a temporary directory. Import rejects files for another pack and files with changed contents.
- You can pause and resume downloads. The app locks the selection during download, import and cancellation cleanup. Deleting a pack affects only the selected option.
- Ready status checks the installation marker and the sizes of the complete files. A `.part` file never counts as installed, even when it has reached its final size. The app verifies full SHA-256 hashes before creating a recognition engine.
- If a release bundles an older ZIP, it belongs to the lightweight option and cannot be imported as the enhanced pack.

## Recognition engine

Both official recognition YAML files specify an input shape of 3 × 48 × 320 and support dynamic width. They share the same dictionary and `CTCLabelDecode` configuration. The existing PaddleOCR adapter reads the local dictionary and uses a fixed height of 48 and a dynamic width capped at 3200. These settings work with the existing inference libraries and third-party engine.

Lightweight detection keeps a minimum short edge of 736 and a maximum long edge of 4000. Enhanced detection caps the long edge at 1280, with a recognition batch size of 1 to contain peak memory usage on a phone. Long screenshots may be downscaled. Cropping can help in this case, although the trade-off still needs testing on actual images.

Each recognition run keeps the pack and file paths selected when it started. After you switch packs, the next run releases the previous engine before loading the new one. The detector, recognizer and dictionary are always taken from the same pack.

The current engine loads ONNX files through byte arrays, which adds substantial memory overhead with the enhanced pack. Configuration and input-shape checks establish compatibility at that level. The work documented here did not run inference using the enhanced model or establish its performance on a physical Android device.

## Packaging and sources

The [pack manifest](offline-packs-manifest.json) lists download URLs, exact file sizes, SHA-256 hashes and pinned source versions. Download and verify the files, then name them according to `localName` and archive them together. Use `ocr-zh-en.zip` for Mobile and `ocr-zh-en-server.zip` for Server. The table above reports unpacked model sizes. Keep each model's source information and Apache-2.0 license when distributing it.

- [Official Mobile detection model](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx)
- [Official Mobile recognition model](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx)
- [Official Server detection model](https://huggingface.co/PaddlePaddle/PP-OCRv5_server_det_onnx)
- [Official Server recognition model](https://huggingface.co/PaddlePaddle/PP-OCRv5_server_rec_onnx)

The ONNX sizes and hashes come from the official Hugging Face API fields `siblings[].size` and `siblings[].lfs.sha256`. Verification uses the LFS SHA-256 digest. The two small YAML files were downloaded at their pinned revisions and hashed locally. The manifest records the metadata source and hash evidence for each file.

## Integration and verification

- **Catalog.** Use `OfflineOcrPackCatalog.packs` and `find(id)`.
- **Descriptors.** `OfflineOcrPackDescriptor` exposes `id`, `title`, `description`, `files` and `totalBytes`.
- **State.** `OfflineOcrPackState` exposes `selectedPackId` and `installedPackIds`. Existing installation and progress fields describe the selected option.
- **Selection and management.** Use `selectPack(id): Boolean` and `selectedPack()`. The existing download, import and delete entry points keep their names.
- **Compatibility.** `chineseMobile` and `OfflineOcrPackCatalog.totalBytes` still refer to the lightweight pack. The selection UI uses the descriptor's `totalBytes`.

Targeted catalog and installation-state tests use short synthetic files to cover pack isolation, old or invalid install markers, same-size corruption, complete temporary files and rejection of paths outside the target directory. These checks run with the project build and test suite. Measuring recognition accuracy and resource usage requires separate tests on physical devices.
