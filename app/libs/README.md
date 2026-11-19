# Android 构建依赖

**简体中文** · [English](README.en.md)

在工程根目录使用 Python 3 准备两个 AAR。首次运行从公开上游下载约 161 MB；再次运行会校验并复用已有文件。

```sh
python3 tools/prepare-android-dependencies.py
```

脚本核对下载大小和 SHA-256，再将可用文件放入本目录。离线检查可运行 `python3 tools/prepare-android-dependencies.py --check`。若已有文件损坏，可先将报错的文件移出本目录，再运行准备命令。下载地址、长度和摘要记录在 [dependency-manifest.json](dependency-manifest.json)。

- **OpenCV 4.10.0** 直接采用 Maven Central 上的 Android AAR。[OpenCV 发布包](https://repo.maven.apache.org/maven2/org/opencv/opencv/4.10.0/)
- **sherpa-onnx 1.13.4** 使用官方 Android AAR。工程从 Maven Central 单独引入 ONNX Runtime，因此准备脚本移除 sherpa 包中四个架构的 `libonnxruntime.so`，保留其他条目的原始内容，生成可重复校验的 `no-ort.aar`。[sherpa-onnx 发行页](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.13.4)

完成后可在 JDK 17、Android SDK Platform 37 环境中运行 `./gradlew :app:assembleDebug`。Windows 使用 `gradlew.bat :app:assembleDebug`。其余编译依赖和插件由 Gradle 从 Google Maven、Maven Central 与 Gradle Plugin Portal 获取。有关第三方许可，见[第三方声明](../../THIRD_PARTY_NOTICES.md)。
