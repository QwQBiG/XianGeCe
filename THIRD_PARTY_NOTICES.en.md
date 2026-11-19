# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md) · **English**

XianGeCe uses the following open-source components. Retain their copyright and license notices when distributing the application or its source.

| Component | Use in XianGeCe | License |
| --- | --- | --- |
| AndroidX Core, Activity, Lifecycle and Navigation | Android application foundations | Apache-2.0 |
| Jetpack Compose and Material 3 | UI and themes | Apache-2.0 |
| Room and DataStore | Local data and preferences | Apache-2.0 |
| Hilt and Dagger | Dependency injection | Apache-2.0 |
| Kotlin, kotlinx.coroutines and kotlinx.serialization | Language, coroutines and JSON | Apache-2.0 |
| OkHttp and Okio | Networking | Apache-2.0 |
| PDFBox Android | PDF text extraction | Apache-2.0 |
| Accompanist SwipeRefresh | Pull-to-refresh UI | Apache-2.0 |
| PaddleOCR and the local Paddle OCR adapter | Text detection and recognition | Apache-2.0. See the source file notices and the [PaddleOCR license](docs/third-party-paddleocr-license.txt). |
| ONNX Runtime Android | On-device inference | MIT |
| OpenCV 4.10.0 | Image processing for OCR | Apache-2.0 |
| Sherpa-ONNX 1.13.4 | Speech recognition runtime | Apache-2.0 |
| commonmark-java 0.24.0 and the table, strikethrough and task-list extensions | Markdown parsing and preview | BSD-2-Clause |

The component releases provide the full license texts and attributions. Before distributing a build, check the exact versions and the components included in its AARs and transitive dependencies. Android Studio, Gradle, the Android SDK and the JDK have their own terms. [OpenCV](https://opencv.org/license/) · [Sherpa-ONNX](https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE) · [ONNX Runtime](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) · [PaddleOCR](https://github.com/PaddlePaddle/PaddleOCR/blob/main/LICENSE)

## Recognition models

Recognition models have their own terms in addition to the runtime licenses. The [offline pack manifest](docs/offline-packs-manifest.json) records the model names, pinned sources, versions and hashes.

- The official model cards for PP-OCRv5 Mobile and Server and for Fun-ASR-Nano identify Apache-2.0. The ONNX conversions come from the repositories listed in the manifest. Retain the original model and conversion authors’ credits.
- The SenseVoice Small INT8 model card points to the separate FunASR model license. Retain the source, author and model name when distributing these weights. [FunASR model license text](docs/third-party-funasr-model-license.txt)
- The legacy Paraformer pack retains its original source. Check its corresponding model terms before redistribution. Preparing a pack locally does not publish it.

Sources checked on September 12, 2026. Retain the licenses and attributions supplied with each pack. [SenseVoice Small](https://huggingface.co/FunAudioLLM/SenseVoiceSmall) · [Fun-ASR-Nano](https://huggingface.co/FunAudioLLM/Fun-ASR-Nano-2512) · [FunASR model terms](https://github.com/modelscope/FunASR/blob/main/MODEL_LICENSE) · [PP-OCRv5 Server](https://huggingface.co/PaddlePaddle/PP-OCRv5_server_rec_onnx)

## License texts for distribution

The ONNX Runtime license and Microsoft copyright notice are included with the APK. [Bundled MIT license](THIRD_PARTY_NOTICES.md#onnx-runtime-许可原文)

The bundled **commonmark-java** notice credits Robin Stocker (2015) and includes the BSD-2-Clause grant, conditions and disclaimer. Retain the complete notice when redistributing it. [Bundled English notice](THIRD_PARTY_NOTICES.md#commonmark-java-许可原文) · [commonmark-java 0.24.0 upstream](https://github.com/commonmark/commonmark-java/tree/commonmark-parent-0.24.0)

The project’s own source in this Android edition uses Apache-2.0. Third-party rights and GPL rights granted for the historical public release continue to apply. Include the corresponding notices with APK, AAB and source distributions. [Full Apache License, Version 2.0](LICENSE)
