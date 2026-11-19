# 可选识别资源

**简体中文** · [English](offline-resource-packs.en.md)

更新：2026-09-12。本轮以现有 Android 引擎能够加载的 ONNX 格式接入，不把任意同名模型 ZIP 当成兼容资源。

## 应用中的选择

打开「我的 → 离线资源」，分别选择 OCR 与课堂语音资源，再选择下载或导入对应 ZIP。无需全部安装，切换选择不会删除其他已安装资源。重新启动后保留选择；旧 Paraformer 安装继续可用。模型是本机资源，不随数据备份导出。

| 用途 | 选择 | 模型文件合计 | ZIP 文件名 |
| --- | --- | ---: | --- |
| OCR | 轻量：PP-OCRv5 Mobile | 21.5 MB | ocr-zh-en.zip |
| OCR | 增强：PP-OCRv5 Server | 172.8 MB | ocr-zh-en-server.zip |
| 语音 | 轻量：SenseVoice Small INT8 | 239.5 MB | diting-sensevoice-int8.zip |
| 语音 | 增强：Fun-ASR-Nano INT8 | 1,009.6 MB | diting-funasr-nano-int8.zip |
| 语音 | 兼容：原 Paraformer INT8 | 237.2 MB | diting-zh-en-int8.zip |

表中 MB 为十进制文件总量，MiB 则按 1024 进制计算。ZIP 另带来源及许可文本。增强模型占用更多运行内存，手机上实际速度与识别效果由用户试用确认；文件更大不等于每段音频或每张图片都更准确。

模型文件可以共存，推理时只加载所选资源；录音过程中禁止切换语音包。导入校验大小与 SHA-256，缺文件、错包或损坏文件不会被标记为可用。临时导入和已有包共存时需留足存储空间。[OCR 分档详情](ocr-offline-packs.md)

## 生成或重新生成 ZIP

在项目根目录使用 Python 3.10 或以上：

```powershell
python tools/Prepare-OfflinePacks.py --list
python tools/Prepare-OfflinePacks.py --all
python tools/Prepare-OfflinePacks.py --pack sherpa_funasr_nano_int8
python tools/Prepare-OfflinePacks.py --all --verify-only
```

默认输出 `artifacts/intelligent-r9/packages/`，缓存位于同级 `model-cache/`。可以用 `--output` 和 `--cache` 指定其他目录。默认先访问官方固定版本，`--mirror` 改为优先镜像；两种来源都必须匹配相同哈希。可中断后重跑，不需要重新下载已经通过校验的文件。

`--all` 准备四个新选择，旧兼容包需显式使用 `--pack sherpa_streaming_paraformer_zh_en_int8`。脚本只下载公开模型文件，不读取账户密钥、不上传项目或录音。

ZIP 中保留模型名、来源、清单与许可。SenseVoice 权重使用独立的 FunASR 模型协议；Nano 和 PP-OCRv5 的官方模型卡标注 Apache-2.0。详见 THIRD_PARTY_NOTICES.md。准备 ZIP 不会自动上传网盘。

## 云端与本机如何配合

配置并启用云端服务后，课堂文字记录优先云端，失败再尝试已选本机模型。只想保存声音时关闭本节文字记录；只用本机时保持联网功能关闭。图片会在发送前显示接收接口，可改选仅本地。

云端保留兼容 `/audio/transcriptions` 的接口，并新增 Fun-ASR WebSocket 预设。Fun-ASR 使用自己的协议，不是把 WebSocket 地址填入兼容上传接口。当前按已经保存的音频分段提交，不是整节课持续连接。转写与文字服务暂共用已有密钥，需确认它能访问所选模型。

Nano 的术语提示通过模型专用配置传入；SenseVoice 与旧 Paraformer 不使用不支持的通用热词接口，不进行强制同音替换。重点中的定位以保存的分段时间为准，模型给出的均匀时间估计不会标成精确逐字对齐。

## 这次可以先这样体验

1. 先导入轻量资源，切换到增强资源后用同一份内容感受差异。
2. OCR 分别试课表截图、日常通知；检查原图、原文和日期待确认提示，再手动确认写入。
3. 录音时试暂停和结束，回听原音；失败段可以重新识别。已授权且配置了云端时重试同样优先云端。
4. 修改一段转写文字，检查语义自动标记清除提示，手动标记仍保留。

本轮构建与协议检查不代表真机准确率或长录音稳定性成绩。实际模型效果、内存占用及真实云端调用由后续体验确认。
