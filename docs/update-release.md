# GitHub Release 更新发布说明

应用会在启动时读取最新 Release 中的 `update.json`。更新清单和 APK 都放在 GitHub Release，不放在博客站点。

## Release 必须包含的两个资源

每次发布新版本时，在同一个 GitHub Release 中上传以下两个资源，并保持文件名完全一致：

- `update.json`
- `xiangece.apk`

应用使用以下稳定地址读取它们：

- 更新清单：`https://github.com/QwQBiG/XianGeCe/releases/latest/download/update.json`
- APK：`https://github.com/QwQBiG/XianGeCe/releases/latest/download/xiangece.apk`

## update.json 示例

```json
{
  "versionCode": 2,
  "versionName": "0.0.1",
  "mandatory": true,
  "minSupportedVersionCode": 1,
  "apkUrl": "https://github.com/QwQBiG/XianGeCe/releases/latest/download/xiangece.apk",
  "sha256": "APK文件的SHA-256值（64位十六进制）",
  "releaseNotes": "本次更新优化了稳定性和用户体验。",
  "downloadPageUrl": "https://github.com/QwQBiG/XianGeCe/releases/latest"
}
```

注意事项：

1. `versionCode` 必须大于用户当前安装的版本号，否则不会触发更新。
2. 本次按项目要求继续使用原有 `v0.0.1` Release；即使版本名仍是 `0.0.1`，`versionCode` 也必须递增为 `2`，这样旧版才能触发更新。`sha256` 必须是同一个 Release 中 `xiangece.apk` 的 SHA-256，避免文件损坏或被替换。
3. 先准备好 `update.json` 和 `xiangece.apk`，确认两个资源都上传完成后，再发布 Release。
4. APK 必须使用与当前应用相同的应用包名和签名，否则无法覆盖安装。
5. `latest/download` 指向最新的正式 Release；不要把测试版或不完整 Release 标记为最新版本。