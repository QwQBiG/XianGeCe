# Optional recognition packs

[简体中文](offline-resource-packs.md) · **English**

Updated September 12, 2026. These packs use the ONNX formats supported by the current Android engines. Compatibility depends on the contents of the ZIP, including the specific model files.

## Choose a pack in the app

Open **我的 → 离线资源** (Profile → Offline resources). Select a pack for image text recognition (OCR) or classroom speech recognition, then choose whether to download the pack or import its ZIP. Install only what you need. Switching your selection preserves other installed packs, and your selection persists after a restart. Existing Paraformer installations still work. The model files stay on your device and are excluded from data backups.

| Purpose | Option | Total model file size | ZIP filename |
| --- | --- | ---: | --- |
| OCR | Lightweight · PP-OCRv5 Mobile | 21.5 MB | `ocr-zh-en.zip` |
| OCR | Enhanced · PP-OCRv5 Server | 172.8 MB | `ocr-zh-en-server.zip` |
| Speech | Lightweight · SenseVoice Small INT8 | 239.5 MB | `diting-sensevoice-int8.zip` |
| Speech | Enhanced · Fun-ASR-Nano INT8 | 1,009.6 MB | `diting-funasr-nano-int8.zip` |
| Speech | Legacy · Paraformer INT8 | 237.2 MB | `diting-zh-en-int8.zip` |

Sizes are totals for the model files in decimal MB. Binary MiB uses powers of 1024. Each ZIP also includes sources and license texts. The enhanced models use more memory. Speed and recognition quality depend on the phone and the input. A larger model offers no guarantee of better results on a particular image or recording.

Several models can coexist on the device. Inference loads only the selected pack. You cannot switch speech packs during a recording. Import checks file sizes and SHA-256 hashes before marking a pack ready. Missing files, a pack for another option or damaged files fail validation. Allow space for both temporary import files and installed packs. [OCR pack details](ocr-offline-packs.en.md)

## Build or rebuild a ZIP

Run these commands from the project root with Python 3.10 or later.

```powershell
python tools/Prepare-OfflinePacks.py --list
python tools/Prepare-OfflinePacks.py --all
python tools/Prepare-OfflinePacks.py --pack sherpa_funasr_nano_int8
python tools/Prepare-OfflinePacks.py --all --verify-only
```

By default the script writes to `artifacts/intelligent-r9/packages/` and uses the sibling `model-cache/` directory for downloads. Set `--output` or `--cache` to choose other locations. The script tries the pinned official source first. Pass `--mirror` to try the mirror first. Both sources must match the same hashes. You can rerun after an interruption and reuse previously verified files.

`--all` prepares the four newer options. To prepare the legacy pack, use `--pack sherpa_streaming_paraformer_zh_en_int8` explicitly. The script downloads public model files. It does not read account keys or upload project files or recordings.

Each ZIP retains the model name, source, manifest and license. SenseVoice weights use a separate FunASR model agreement. The official model cards for Nano and PP-OCRv5 specify Apache-2.0. Building a ZIP saves it locally without uploading it to cloud storage. [Third-party notices](../THIRD_PARTY_NOTICES.en.md)

## Local and cloud recognition

After you configure and enable cloud services, classroom transcription tries the cloud endpoint first, then the selected local model on failure. To record audio alone, turn off transcription for the session. For local processing only, leave online features off. Before sending an image, the app shows the receiving endpoint and offers a local-only option.

Cloud transcription supports an `/audio/transcriptions`-compatible endpoint and a separate Fun-ASR WebSocket preset. Fun-ASR has its own protocol. Use its preset for a WebSocket address. The app submits saved audio segments individually without keeping a connection open for the entire class. Transcription and text services currently share a key, which must have access to the selected models.

Nano accepts terminology hints through its model-specific configuration. SenseVoice and legacy Paraformer do not use a generic hotword interface they do not support. The app does not force phonetic substitutions. Highlights use the saved segment times for playback. Uniform timing estimates from a model are not presented as precise character-level alignment.

## Try the packs

1. Import a lightweight pack, try it, then compare the enhanced pack on the same material.
2. For OCR, try both a timetable screenshot and a campus announcement. Compare the original image, the extracted text and any dates flagged for review before confirming the import.
3. Pause and end a recording, then replay the original audio. You can retry a failed segment. A retry also tries the cloud first when you have configured and authorized it.
4. Edit a transcript segment and check the notice about clearing automatic annotations. Manually added markers remain.

The build and protocol checks described here establish neither on-device accuracy nor long-recording stability. Model quality, memory use and live cloud calls still require separate evaluation.
