# 弦歌册 XIANGECE

> 大学诸事，尽入一册。

弦歌册是一款面向大学生的本地优先校园助手，把课程、通知、课堂录音、任务、习惯、成绩和日常事项收进一个清晰的工具箱。

## 当前版本

- 应用版本：`0.0.1`
- 当前版本号：`versionCode 2`
- 包名：`win.iqwqi.xiangece`
- 最低 Android 版本：API 26（Android 8.0）
- 推荐编译环境：JDK 17、Android SDK 37

## 下载

- [GitHub Releases](https://github.com/QwQBiG/XianGeCe/releases/latest)
- [直接下载最新版 APK](https://github.com/QwQBiG/XianGeCe/releases/latest/download/xiangece.apk)

## 功能概览

- **今日**：查看教学周、当天课程、上课状态、校园事件与待办事项。
- **课程**：周课表、教学周切换、多课表、课程详情、单双周、教室、教师、颜色与背景设置。
- **课表导入**：支持 HTML、PDF、Excel、课表口令；图片课表可使用已安装的离线 OCR，或使用用户主动配置的 AI 识别。
- **收件**：接收系统分享的通知文字和图片，文字或图片识别后统一进入可编辑的确认草稿，确认后再写入日程或课程。
- **谛听**：课堂录音、分段转写、重点与提问提醒、手动标记、按时间回听、文字查看与分享。
- **百宝**：成绩与加权平均分、考试倒计时、课程时间、计算器、专注计时、生活费记账等工具。
- **厚积**：习惯、每日任务、连续记录、年度打卡热力图与每日箴言。
- **我的**：AI 设置、离线资源、`.xiangece` 本地备份恢复、外观设置、提醒诊断与隐私说明。

## 谛听与离线能力

谛听支持中文、英文和中英混合场景。录音会保存在本机，并按短分段记录；结束课堂后可以回听原音、查看文字、定位重点和提问。录音期间可以戴耳机使用其他应用、刷视频或玩游戏，实际收音设备以录音界面和系统通知显示为准。

中文/英文离线语音识别和中文离线 OCR 都是可选资源，统一从“我的 → 我的资源”按需下载或导入 ZIP，不会强制塞进 APK。未安装资源时，录音和其他基础功能仍可使用；离线包下载支持校验、续传和多源策略，具体大小以应用内显示为准。

AI 自动整理默认关闭。启用后，AI 可根据转写文字和上下文辅助整理重点、提问与课堂内容；AI 结果只作为提示，重要内容仍应结合原音核对。

## 数据与隐私

弦歌册坚持本地优先：课程、任务、事件、习惯、成绩、收件记录和谛听文字默认保存在设备本地，不上传到弦歌册自建服务器。

- 应用不主动扫描相册，只处理你明确选择或分享的图片。
- 离线语音识别和离线 OCR 在设备本地处理。
- 只有在你主动配置并使用 AI 时，必要的文字、上下文或你选择的图片才会发送到指定的第三方接口。
- AI API 密钥使用 Android Keystore 与 AES-GCM 加密保存，不写入日志、备份文件或源代码。
- `.xiangece` 备份包含课表、设置、任务、事件、习惯与打卡、成绩、收件图片副本、OCR 记录，以及谛听文字和标记；为控制体积，谛听原始音频不进入备份，AI 密钥也不会进入备份。
- Android 自动云备份已关闭；卸载应用或清除应用数据会删除本机数据，已导出的备份文件由用户自行保管。
- 录音前请确认符合学校规定、授课教师要求及所在地法律。

## 自动更新

应用启动时会检查 GitHub Release 中的更新信息。正常应用更新不会主动清除本机数据；进行重要更新前，建议先在“我的 → 本地备份”导出一份 `.xiangece` 文件。

更新资源统一发布在 GitHub Release：

- `update.json`：版本、下载地址和 APK 校验信息。
- `xiangece.apk`：应用安装包。

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

Release 构建已启用 R8 混淆和资源压缩。发布前请同步检查版本号、APK 签名、`update.json` 中的 SHA-256，以及 GitHub Release 中的 APK 文件。

## 开源协议

本项目使用 GPL-3.0，详见 [LICENSE](LICENSE)。第三方组件和模型另见 `docs/` 下的说明文件。

## 联系与公开说明

- 项目源码：[QwQBiG/XianGeCe](https://github.com/QwQBiG/XianGeCe)
- 隐私政策与应用说明：[iqwqi.win](https://iqwqi.win/cs/posts/xiangece/)
