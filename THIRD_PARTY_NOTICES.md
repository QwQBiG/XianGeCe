# 第三方依赖说明

弦歌册使用了以下开源依赖。它们的许可证不等同于弦歌册自身的许可证；发布应用时应保留相关版权和许可证声明。

| 组件 | 用途 | 许可证 |
| --- | --- | --- |
| AndroidX Core / Activity / Lifecycle / Navigation | Android 基础能力 | Apache-2.0 |
| Jetpack Compose / Material 3 | 界面与主题 | Apache-2.0 |
| Room / DataStore | 本地数据与设置 | Apache-2.0 |
| Hilt / Dagger | 依赖注入 | Apache-2.0 |
| Kotlin / kotlinx.coroutines / kotlinx.serialization | 语言、异步与 JSON | Apache-2.0 |
| OkHttp / Okio | 网络访问 | Apache-2.0 |
| PDFBox Android | PDF 文字层读取 | Apache-2.0 |
| Accompanist SwipeRefresh | Compose 辅助组件 | Apache-2.0 |

完整许可证文本和版权归属以各依赖发布页为准。Android Studio、Gradle、Android SDK 和 JDK 也有各自的使用条款，不随本仓库重新授权。

## 为什么需要这个文件

开源协议只说明弦歌册源代码本身如何使用。第三方依赖仍然保留各自的版权和许可证，发布 APK、AAB 或源代码时应提供这些说明，避免把第三方代码误认为是项目原创内容。
