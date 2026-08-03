# 弦歌册 XIANGECE

> 大学诸事，尽入一册。

弦歌册是一款面向大学生的本地校园助手。它把课程表、通知、截图和零散文字整理成可编辑、可提醒的课程、任务与校园事项。

## 当前版本

- 应用版本：`0.0.1`
- 包名：`win.iqwqi.xiangece`
- 最低 Android 版本：API 26
- 推荐编译环境：JDK 17、Android SDK 37

## 功能概览

- 今日：查看当天课程、正在进行的课程、任务和校园事件。
- 课程：周课表、教学周切换、课程详情、教室、教师、单双周、课程颜色和背景设置。
- 课表导入：支持 HTML、PDF、Excel、课表口令；图片识别通过用户自行配置的云端多模态 AI 完成。
- 百宝：通知转日程、图片转文字/日程、成绩与加权平均分等工具。
- 我的：AI 接口配置、本地备份、收件记录、应用信息与登录入口。
- 提醒：课程、任务和事件提醒；设备重启后自动恢复调度。

## 隐私与数据

应用默认以本地存储为主，不主动扫描相册，也不上传课程、任务或收件内容。AI 功能默认关闭，只有用户主动配置并使用时，相关文字或图片才会发送到用户指定的第三方接口。

完整隐私政策：<https://iqwqi.win/cs/posts/xiangece/>

## 安全说明

- API 密钥使用 Android Keystore 与 AES-GCM 加密保存。
- API 密钥不会写入日志、备份文件或源代码。
- Release 签名密钥不进入仓库，通过环境变量提供。
- 不要把真实 API 密钥、签名文件或本地配置提交到 GitHub。

## 构建项目

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Release 构建需要在本机设置签名环境变量：

```powershell
$env:XIANGECE_STORE_FILE = "D:\secure\xiangece-release.jks"
$env:XIANGECE_STORE_PASSWORD = "本机环境变量"
$env:XIANGECE_KEY_ALIAS = "xiangece"
$env:XIANGECE_KEY_PASSWORD = "本机环境变量"
.\gradlew.bat assembleRelease
```

## 版本约定

版本号采用 `主版本.次版本.修订版本`：

- `0.0.x`：早期测试版本
- `0.1.x`：功能基本稳定的公开测试版本
- `1.0.0`：正式稳定版本

每次发布请同步更新 `app/build.gradle.kts`、更新日志和 Git tag。

## 开源协议

当前仓库使用 GPL-3.0，详见 [LICENSE](LICENSE)。如果未来需要闭源商业发行，应在发布前将协议改为 Apache-2.0，并同步更新 LICENSE、README 和版权声明。

## 联系与隐私政策

隐私政策、联系方式和公开说明：<https://iqwqi.win/cs/posts/xiangece/>
