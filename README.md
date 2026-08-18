# 弦歌册 XIANGECE

<p align="center">
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest">
    <img alt="Version" src="https://img.shields.io/badge/version-0.0.1-blue?style=flat-square" />
  </a>
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest">
    <img alt="VersionCode" src="https://img.shields.io/badge/versionCode-2-blue?style=flat-square" />
  </a>
  <a href="https://www.android.com/">
    <img alt="MinSdk" src="https://img.shields.io/badge/min--sdk-API_26_(8.0)-3ddc84?style=flat-square&logo=android&logoColor=white" />
  </a>
  <a href="https://developer.android.com/about/versions/15">
    <img alt="TargetSdk" src="https://img.shields.io/badge/target--sdk-API_37-34a853?style=flat-square" />
  </a>
  <a href="https://kotlinlang.org/">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.4.10-7f52ff?style=flat-square&logo=kotlin&logoColor=white" />
  </a>
  <a href="https://www.gnu.org/licenses/gpl-3.0.html">
    <img alt="License" src="https://img.shields.io/badge/License-GPL--3.0-yellow.svg?style=flat-square" />
  </a>
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest">
    <img alt="APK Size" src="https://img.shields.io/badge/APK-arm64--v8a_%7C_armeabi--v7a-2244ee?style=flat-square" />
  </a>
  <a href="https://github.com/QwQBiG/XianGeCe">
    <img alt="Repo Stars" src="https://img.shields.io/github/stars/QwQBiG/XianGeCe?style=flat-square&logo=github" />
  </a>
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest/download/xiangece.apk">
    <img alt="GitHub Downloads" src="https://img.shields.io/github/downloads/QwQBiG/XianGeCe/latest/total?style=flat-square&label=latest%20APK%20downloads&logo=github" />
  </a>
</p>

<p align="center">
  <b>大学诸事，尽入一册。</b>
</p>

弦歌册是一款面向大学生的**本地优先**校园助手，把课程、通知、截图、课堂录音、任务、习惯、成绩和日常事项收进一个清晰的入口，帮助从"看到信息"走到"完成事情"。

应用不需要账号也能使用，不含广告，也不强制云同步。所有数据默认保存在设备本机；AI 与离线识别都是可选增强，关掉也不影响基础功能。

---

## 目录

- [当前版本](#当前版本)
- [下载](#下载)
- [功能概览](#功能概览)
  - [今日](#今日)
  - [课程](#课程)
  - [课表导入](#课表导入)
  - [收件](#收件)
  - [谛听](#谛听)
  - [百宝](#百宝)
  - [厚积](#厚积)
  - [我的](#我的)
- [桌面小部件](#桌面小部件)
- [离线资源](#离线资源)
- [AI 使用方式](#ai-使用方式)
- [数据与隐私](#数据与隐私)
- [备份与恢复](#备份与恢复)
- [自动更新](#自动更新)
- [技术栈](#技术栈)
- [项目结构](#项目结构)
- [构建项目](#构建项目)
- [测试](#测试)
- [文档](#文档)
- [开源协议](#开源协议)
- [联系与公开说明](#联系与公开说明)

---

## 当前版本

| 项 | 值 |
| --- | --- |
| 应用版本 | `0.0.1`（首个公测版本） |
| versionCode | `2` |
| 包名 | `win.iqwqi.xiangece` |
| 最低 Android 版本 | API 26（Android 8.0） |
| 目标 / 编译 SDK | API 37 |
| ABI | `arm64-v8a`、`armeabi-v7a` |
| 推荐编译环境 | JDK 17、Android SDK 37、AGP 9.3.1、Kotlin 2.4.10 |
| 开源协议 | GPL-3.0 |

## 下载

### 方式一：GitHub Releases（推荐，有梯子）

<p>
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest">
    <img alt="Get it on GitHub" src="https://img.shields.io/badge/GitHub_Releases-181717?style=for-the-badge&logo=github&logoColor=white" />
  </a>
  <a href="https://github.com/QwQBiG/XianGeCe/releases/latest/download/xiangece.apk">
    <img alt="Download APK" src="https://img.shields.io/badge/%E7%9B%B4%E6%8E%A5%E4%B8%8B%E8%BD%BD%E6%9C%80%E6%96%B0%E7%89%88APK-009688?style=for-the-badge&logo=android&logoColor=white" />
  </a>
</p>

### 方式二：国内网盘（无梯子，国内速度快）

- **夸克网盘**：<https://pan.quark.cn/s/edc1876d6a89>
- **百度网盘**：<https://pan.baidu.com/s/1mVtkpbJvv5ZmWg0VcCHFMA> 提取码：`ydWI`

手机扫码或打开链接，下载 APK 后覆盖安装即可。

## 功能概览

底部导航共五个主页面：**今日 · 厚积 · 课程 · 百宝 · 我的**。收件与谛听作为跨页能力，分别从"我的"和"百宝"进入。

### 今日

- 当前学期与教学周进度。
- 当天课程汇总（共几节、剩余几节）与"今日课程"列表，已结束的自动收起。
- 课程状态实时显示：上课中、即将开始、已结束。
- 待办事项（支持按未完成 / 已完成筛选、长按编辑、撤销）。
- 校园事件（近期活动一览，过期自动隐藏）。
- 全局搜索：输入关键词可在课程、任务、事件、习惯中跨类型查找。

### 课程

- 七天周课表网格与按天查看。
- 教学周切换、多课表管理、单双周课程。
- 课程详情：教室、教师、课程颜色、课表背景与壁纸、节次高亮、列宽行高与节数可配。
- 空白节次快捷加课、时间冲突提示。
- 长按课程可编辑，点击空格可新增。
- 课程提醒：基于 `AlarmManager` 的 `RTC_WAKEUP`，按节次时间提前提醒。

### 课表导入

支持多种格式，导入完成前都会先生成可编辑的待确认草稿，确认后才写入课表：

- **HTML 课表**：教务页面或导出的 HTML。
- **PDF 课表**：支持"表格版式"和"列表版式"两种解析模式；扫描件无文字层时可改用图片识别。
- **Excel / CSV 课表**：电子表格导出的课表。
- **课表口令**：以 `XGC1-` 开头的字符串，便于在同学间传递结构化课表。
- **图片课表**：使用已安装的中文离线 OCR，或用户主动配置的 AI 识别。

### 收件

把微信、QQ、浏览器或相册中的文字和图片分享给弦歌册（通过系统分享菜单），集中留在收件中：

- 通知文字可整理成课程、任务、事件或备忘草稿。
- 图片可使用已安装的中文离线 OCR，也可使用主动配置的 AI 识别。
- 文字识别与图片识别都会先进入可编辑的确认草稿，确认后才写入日程或课程。
- 识别失败时尽量保留原始文字或图片，不会因解析失败而丢失信息。

### 谛听

课堂录音与转写工具，从"百宝"进入。用于专业课记重点，也适合水课中及时捕捉提问。

- 录音保存在本机，按约 8 秒分段记录；前台麦克风服务在系统通知栏持续显示状态。
- 支持中文、英文和中英混合场景；可填写专业术语词表（如 `Cache`、`Transformer`、`傅里叶变换`）。
- 专业课模式：本地规则雷达识别"重点、考点、必考、定义、定理、important、exam"等表达，生成疑似重点标记。
- 水课模式：识别"有没有问题、有什么疑问、谁能回答、any questions"等表达，生成疑似提问标记并以静音带震动的高优先级通知提醒。
- 转写过程中可见分段文字；结束后可查看完整记录、按时间回听、分享音频或文字、删除记录。
- 戴耳机时可以继续刷视频或玩游戏；实际收音设备以录音界面和系统通知显示为准。
- 识别不确定时优先回听原音，不必完全依赖机器转写。

### 百宝

按需使用校园小工具，统一入口：

- **谛听**：课堂录音与转写。
- **成绩**：GPA / 加权平均分，支持 4.0、4.3、5.0 与自定义规则。
- **课程时间**：节次时间编辑，同步课程页左侧时间轴。
- **考试倒计时**：自动识别事件标题中的考试类关键词并倒计时。
- **计算器**：普通、科学函数（sin/cos/tan/√/log/ln/x²/^/π/e）和常用单位换算，带历史记录。
- **专注番茄**：沉浸式计时。
- **记账**：生活费收支流水。

### 厚积

记录每天的坚持：

- 习惯与每日任务。
- 连续天数与完成次数。
- 年度打卡热力图。
- 每日箴言（应用首次启动时会内置 6 条箴言，可自定义增删）。

### 我的

在这里集中管理：

- **外观**：6 套主题色、暗色模式、跟随系统主题。
- **AI 设置**：接口地址、模型、鉴权方式与 API 密钥（首次开启前可测试连通性）。
- **我的资源**：中文/英文离线语音识别、中文离线 OCR 的下载、导入与管理。
- **本地账号**：可选的本机账号注册 / 登录 / 退出（账号信息只保存在本机，密码以 AES-GCM 加密）。
- **本地备份**：导出 / 恢复 `.xiangece` 文件。
- **收件记录**：查看与管理收件箱。
- **提醒诊断**：通知权限、精确闹钟权限状态刷新与跳转设置（每 1.5s 刷新以反映系统变更）。
- **隐私说明**与应用信息。

## 桌面小部件

提供 3 种桌面小部件，可在桌面长按添加：

- **弦歌册 · 今日**：查看今天的下一节课程与教室。
- **弦歌册 · 下一节**：桌面角落快速查看下一节课。
- **弦歌册 · 今日安排**：查看今天接下来的三节课程。

## 离线资源

离线资源统一放在"我的 → 我的资源"中按需安装，**不会强制打进 APK**，未安装也不影响其他功能。

- **中文/英文离线语音识别**：基于 Sherpa-onnx 的 Streaming Paraformer 中英双语模型（int8），当前包约 237 MB。
- **中文离线 OCR**：基于 PaddleOCR PP-OCRv5 + ONNX Runtime + OpenCV，当前包约 21 MB。

资源下载支持校验、断点续传和多源策略；网络受限时也可导入对应 ZIP。资源大小以应用内显示为准。

未安装离线包时：录音仍可正常进行并保存音频；图片课表与收件图片可改用 AI 识别或暂不识别。离线识别不需要付费 API，也不会因为没有配置云端服务而自动上传录音或图片。

## AI 使用方式

AI 是可选增强，不是使用弦歌册的前提。

- AI 默认关闭。
- 只有你主动配置接口并开启对应功能时，应用才会调用指定服务。
- 图片识别：在选择 AI 识别时发送你明确选择的图片。
- 谛听 AI 整理：主要使用转写文字和必要的课堂上下文，不会默认上传原始音频。
- 草稿 AI 增强：将通知文字发送给指定接口整理（文字限制 2400 字以内，减少 token 消耗）；未启用 AI 时仍会生成本地草稿。
- AI 结果只作为提示，重要内容应结合原音、原图或原文核对。
- API 密钥使用 **Android Keystore + AES-GCM** 加密保存，不写入日志、备份文件或源代码。

## 数据与隐私

弦歌册坚持本地优先。课程、任务、校园事件、习惯、成绩、收件记录和谛听文字默认存储在设备本机，不上传到弦歌册自建服务器。

- 不主动扫描相册，只处理你明确选择或分享的图片。
- 不读取通讯录、通话记录、短信、定位或其他应用的使用记录。
- 离线语音识别和离线 OCR 在设备本地处理。
- 只有在你主动配置并使用 AI 时，必要的文字、上下文或你选择的图片才会发送到指定的第三方接口；发送前应用会提示数据将离开设备。
- Android 自动云备份已关闭（`allowBackup=false`、`fullBackupContent=false`）。
- 卸载应用或清除应用数据会删除应用私有目录中的本地数据；已导出的备份文件由用户自行保管。
- 录音前请确认符合学校规定、授课教师要求及所在地法律。

完整隐私政策见 [docs/privacy-policy.md](docs/privacy-policy.md)。

## 备份与恢复

在"我的 → 本地备份"中可以导出或恢复一个 `.xiangece` 文件。一份完整备份包含：

- 课表、学期、节次、课程和课程详情。
- 外观、提醒和其他个性化设置（**强制 `aiEnabled=false`、清空 `encryptedApiKey`**）。
- 任务、校园事件、习惯、打卡记录和成绩。
- 收件文字、图片副本和 OCR 记录。
- 谛听课堂记录、转写文字和重点 / 提问标记。

为控制备份体积，**谛听原始音频不进入备份**（`audioDirectory` / `audioPath` / `audioBytes` 在备份时被清空）；**AI API 密钥也不进入备份**。需要保留录音原音时，请在谛听记录中单独分享或保存音频。

正常应用更新不会主动清除本机数据；进行重要更新前，建议先导出一份 `.xiangece` 备份并保存到安全位置。

## 自动更新

应用启动时会检查 GitHub Release 中的更新信息。发现新版本后会提示更新、下载 APK 并校验完整性，再通过系统安装器安装；网络或服务暂时不可用时，不影响已有的本地课表和记录继续使用。

更新资源统一发布在 GitHub Release，文件名固定：

- `update.json`：版本号、下载地址、SHA-256 与最低兼容版本。
- `xiangece.apk`：应用安装包。

更新清单示例见 [docs/update-manifest.example.json](docs/update-manifest.example.json)，发布流程见 [docs/update-release.md](docs/update-release.md)。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 | Kotlin 2.4.10（`kotlinx.serialization`、`kotlinx.coroutines`） |
| UI | Jetpack Compose + Material 3（Material Icons Extended） |
| 依赖注入 | Hilt / Dagger |
| 本地存储 | Room 2.8.4 + DataStore |
| 网络 | OkHttp 5 |
| PDF 解析 | PDFBox Android |
| 离线 OCR | PaddleOCR（PP-OCRv5）+ ONNX Runtime 1.27 + OpenCV 4.10 |
| 离线语音 | Sherpa-onnx 1.13.4（Streaming Paraformer 中英双语 int8） |
| 提醒 | AlarmManager + 前台 Service + BootReceiver |
| 安全 | Android Keystore + AES-GCM |
| 构建 | AGP 9.3.1、KSP、Gradle 配置缓存、R8 混淆 + 资源压缩 |


## 项目结构

```
app/
├── libs/                          # 本机构建依赖 AAR（不随仓库发布）
├── schemas/                       # Room 数据库迁移历史（1–9）
└── src/main/java/
    └── win/iqwqi/xiangece/
        ├── MainActivity.kt        # 入口、分享 intent、更新流程
        ├── XiangeceApplication.kt # @HiltAndroidApp
        ├── core/
        │   ├── ai/                # AiCampusEnhancer（可选 AI 增强）
        │   ├── backup/            # BackupManager（.xiangece 导入导出）
        │   ├── importing/         # 课表导入：HTML / Excel / PDF / 口令
        │   ├── ocr/                # OcrService + OfflineOcrService + OfflineOcrPack
        │   ├── offline/           # OfflinePackArchive + ResumableHttpFileDownloader
        │   ├── reminder/          # AlarmManager 提醒、BootReceiver、ReminderReceiver
        │   ├── security/          # ApiKeyCipher（Keystore + AES-GCM）
        │   └── update/            # AppUpdate（GitHub Release 检查 / 下载 / 安装）
        ├── data/                  # Room（Dao / Entities / Converters）+ DataStore 设置
        ├── domain/                # 解析器、学期/教学周/冲突检测/绩点计算
        ├── feature/diting/        # 谛听：audio / data / domain / offline / transcription / ui
        ├── ui/
        │   ├── components/        # 公共组件（PaperCard、AppSnackbar、AppFormSheet…）
        │   ├── screens/           # 今日 / 课程 / 百宝 / 厚积 / 我的 / 收件 / 引导 / 草稿编辑
        │   ├── theme/             # 6 套主题色 + 暗色模式
        │   └── XiangeceApp.kt     # 主框架、HorizontalPager、闪屏
        └── widget/                # 3 种桌面小部件：今日 / 下一节 / 今日安排
docs/                              # 隐私政策、谛听实现说明、更新发布说明等
offline-packs/                     # 本机可选离线模型包（不随仓库发布）
```

首次启动时会进入引导页，要求填写学期名称、第一周周一日期与教学周数；填完即可开始使用。Android 13+ 会在引导完成后首次请求通知权限。

## 构建项目

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Release 构建需要在本机设置签名环境变量；真实签名文件、密码和 API 密钥不要提交到仓库：

```powershell
$env:XIANGECE_STORE_FILE = "D:\secure\xiangece-release.jks"
$env:XIANGECE_STORE_PASSWORD = "本机环境变量"
$env:XIANGECE_KEY_ALIAS = "xiangece"
$env:XIANGECE_KEY_PASSWORD = "本机环境变量"
.\gradlew.bat assembleRelease
```

Release 构建已启用 R8 混淆和资源压缩（`isMinifyEnabled=true`、`isShrinkResources=true`）。发布前请同步检查：版本号、APK 签名、`update.json` 中的 SHA-256，以及 GitHub Release 中的 APK 文件。

## 测试

- 单元测试位于 `app/src/test/java/`，覆盖解析器（课表 HTML / PDF / 文本 / 图片、校园文本、草稿校验）、学期与教学周计算、课程冲突检测、提醒时间计算、绩点计算、备份负载校验、离线包与下载器、谛听 AI 注解 / 信号分析 / WAV 写入。
- 仪器测试位于 `app/src/androidTest/java/`，覆盖 CampusDao、谛听 Dao 与转写协调器、草稿编辑器与首次启动流程。

## 文档

- [CHANGELOG.md](CHANGELOG.md) — 更新日志
- [docs/privacy-policy.md](docs/privacy-policy.md) — 隐私政策
- [docs/diting-implementation.md](docs/diting-implementation.md) — 谛听实现说明
- [docs/update-release.md](docs/update-release.md) — GitHub Release 发布流程
- [docs/update-manifest.example.json](docs/update-manifest.example.json) — 更新清单示例
- [docs/third-party-paddleocr-license.txt](docs/third-party-paddleocr-license.txt) — PaddleOCR 第三方许可
- [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) — 第三方依赖说明

## 开源协议

本项目使用 **GPL-3.0** 协议，详见 [LICENSE](LICENSE)。你可以自由使用、修改和学习源码；衍生作品须继续以 GPL-3.0 开源并保留版权声明。第三方组件和模型另见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) 与 `docs/` 下的说明文件。

## 联系与公开说明

- 项目源码：[QwQBiG/XianGeCe](https://github.com/QwQBiG/XianGeCe)
- 隐私政策与应用说明：[iqwqi.win](https://iqwqi.win/cs/posts/xiangece/)
- Release 下载：[GitHub Releases](https://github.com/QwQBiG/XianGeCe/releases)

版权所有 © 2026 弦歌册（iqwqi）。功能和隐私说明会随版本更新。
