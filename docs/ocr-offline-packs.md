# OCR 离线资源分档

**简体中文** · [English](ocr-offline-packs.en.md)

更新日期：2026-09-11。

## 可选资源

| 档位 | 模型 | 三个文件合计（解压后） | 用途与取舍 |
| --- | --- | ---: | --- |
| 轻量，默认 | PP-OCRv5 Mobile ONNX | 21,509,645 字节，约 21.5 MB / 20.5 MiB | 延续原有截图、课表、日常文字识别，下载小 |
| 增强 | PP-OCRv5 Server ONNX | 172,768,163 字节，约 172.8 MB / 164.8 MiB | 官方更大检测和识别模型，耗时与内存占用更高 |

均来自 PaddlePaddle 官方发布、Apache-2.0 授权。本轮未做准确率比较，不能据体积声称增强包对所有图片更准。增强包也在本机执行，“Server”是模型系列名称，不表示联网识别。

资源按需下载或从 ZIP 导入；选择档位本身不会下载，也不会要求同时安装两套。选择未安装的档位时，离线识别提示安装所选资源，不偷偷改用另一档。云端优先级由应用层处理，不属于本资源管理器。见[识别资源指南](offline-resource-packs.md)。

## 安装与兼容

- 轻量 ID 保持 pp_ocrv5_mobile_zh_en_onnx，目录仍为应用私有 files/ocr-offline/pp_ocrv5_mobile_zh_en_onnx。
- 增强 ID 为 pp_ocrv5_server_zh_en_onnx，使用同级独立目录。
- 选择存于本机 SharedPreferences ocr-offline-selection 的 selectedPackId，缺失或未知值默认轻量。这里不保存下载链接、用户内容或密钥。
- 原有轻量 ZIP 和 .installed 安装标记继续识别；新增档位不迁移、不删除旧包。
- 两档 ZIP 都使用 det.onnx、rec.onnx、rec.yml。支持直接置于 ZIP 根目录或共同的目录下，文件名不得重复。
- 导入先在临时目录检查全部文件的大小及 SHA-256，再安装到所选目录。选错档位或修改过的文件会被拒绝。
- 下载可暂停、继续；下载／导入及取消收尾期间拒绝切换。删除只作用于当前档位，另一套已安装资源保留。
- 就绪状态通过安装标记与完整文件大小判断，.part 即使已下满也不计为已安装。每次新建识别引擎前核对完整 SHA-256。
- 内置旧 ZIP（如发行时提供）只归属轻量档，不会被当作增强档导入。

## 引擎适配

两份官方识别 YAML 的输入形状均为 3 × 48 × 320，支持动态宽度；字典及 CTCLabelDecode 配置相同。现有 PaddleOCR 适配器读取本地字典、固定高度 48、动态宽度上限 3200，因此无需增加推理库或修改第三方引擎。

轻量档保留原有检测参数：短边至少 736、最长边上限 4000。增强档限制检测最长边为 1280，批量识别仍为 1，控制手机峰值内存。长截图可能因此缩小，适合裁剪后识别；该取舍尚待用户实际体验。

单次识别固定使用开始时选中的模型及其路径。切换后，下一次识别释放旧引擎并加载新引擎；不会混用两档检测器、识别器和字典。

现有引擎通过字节数组载入 ONNX，增强包会产生明显更多内存开销。配置与形状兼容检查不等于 Android 真机性能验证；未在本工作分支运行增强模型推理。

## 打包和来源

可机器读取的下载链接、精确大小、SHA-256 与固定发布版本，见[公开资源清单](offline-packs-manifest.json)。先下载并校验，再按 localName 改名压缩；轻量推荐 ocr-zh-en.zip，增强推荐 ocr-zh-en-server.zip。ZIP 压缩大小不等于本表模型总大小。分发时保留模型来源与 Apache-2.0 许可。

- [官方 Mobile 检测模型](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_det_onnx)
- [官方 Mobile 识别模型](https://huggingface.co/PaddlePaddle/PP-OCRv5_mobile_rec_onnx)
- [官方 Server 检测模型](https://huggingface.co/PaddlePaddle/PP-OCRv5_server_det_onnx)
- [官方 Server 识别模型](https://huggingface.co/PaddlePaddle/PP-OCRv5_server_rec_onnx)

ONNX 大小和 SHA-256 来自官方 Hugging Face API 的 siblings[].size 与 siblings[].lfs.sha256，不是 Xet hash。两份小型 YAML 已按固定版本实际下载并计算 SHA-256。清单逐项记录元数据来源和哈希依据。

## 接入接口与验证范围

- 目录：OfflineOcrPackCatalog.packs、find(id)。
- 描述：OfflineOcrPackDescriptor 的 id/title/description/files/totalBytes。
- 状态：OfflineOcrPackState.selectedPackId、installedPackIds；原有安装／进度字段代表所选档位。
- 管理：selectPack(id): Boolean、selectedPack()；原有下载、导入、删除入口名称不变。
- 兼容属性 chineseMobile 与 OfflineOcrPackCatalog.totalBytes 仍代表轻量档，新界面使用档位描述的 totalBytes。

增加目录与安装状态的针对性单元测试，使用短合成文件验证两档隔离、旧标记、错误标记、同长度损坏、完整临时文件和目录越界拒绝。构建及测试由主任务统一执行；这些验证不代表真实识别准确率或真机资源消耗。
