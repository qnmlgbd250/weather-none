# SkyPulse Weather - 项目记忆

## 构建环境
- **JAVA_HOME**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle Wrapper**: `gradlew.bat` (Gradle 8.5)
- **compileSdk / targetSdk**: 34
- **minSdk**: 26
- **Java Version**: 21

## 签名信息 (Release)
- **Keystore 文件**: `app/release-keystore.jks`
- **Store Password**: `weather123`
- **Key Alias**: `weather-app`
- **Key Password**: `weather123`

## AMAP (高德地图)
- **API Key 配置位置**: `local.properties` 中的 `AMAP_API_KEY`
- **读取优先级**: `gradle.properties` > `local.properties`
- **当前包名**: `com.skypulse.weather`

## 版本管理
- **版本号位置**: `app/build.gradle.kts` 中的 `versionCode` 和 `versionName`
- **改动后构建**: 每次代码修改完成后，默认自动执行构建验证；构建流程必须自动递增 `versionCode` 和 `versionName`，除非用户明确要求暂不构建或暂不迭代版本号

## 发版规则
- **后续默认发版**: 除非用户主动要求 GitHub 发版，否则日常只执行云剪贴板发版
- **默认发版**: 只执行 `scripts\release.ps1` 发布到云剪贴板
- **GitHub 发版**: 仅在用户主动要求时才推送到 GitHub 并创建 Release
- **GitHub Token**: GitHub 发版必须从 `local.properties` 读取 GitHub token，不得硬编码到源码、脚本输出或 Release 描述中
- **GitHub 包体完整性**: GitHub 发版上传 APK 前必须记录本地 APK 文件大小和 SHA-256；上传后必须从 GitHub Release 下载该 APK 资产并重新计算文件大小和 SHA-256，二者完全一致才算发版成功；如不一致，删除损坏资产后重新上传并再次校验
- **GitHub Release 描述**: GitHub 发版描述必须使用中文；内容只描述上一个 GitHub Release 版本到当前版本之间的 Git 改动简短总结；必须使用无序列表逐条列出；不得包含下载链接、校验信息、构建过程、致谢或其他无关联信息
- **改动后发版**: 每次代码改动完成并验证后，默认自动执行云剪贴板发版；除非用户明确要求暂不发版
- 发版前必须清理 build.gradle.kts 和 CHANGELOG.md 的 UTF-8 BOM

## Git 操作规范
- **git操作**: 除非用户主动要求提交/推送，否则不要提交/推送代码到远程仓库


## 包体命名
- **APK 命名**: `skypulse-v<versionName>.apk`
- 云剪贴板和 GitHub Release 都必须使用该格式
- **APK 清理**: 每次构建成功并生成新的 APK 后，清理根目录中旧的 `skypulse-v*.apk` 包，仅保留最新构建产物；除非用户明确要求保留历史 APK

## 打赏名单规则
- 打赏名单位于 `app/src/main/java/com/skypulse/weather/ui/components/DonateDialog.kt` 的 `donors` 列表
- 列表按金额从高到低降序排列；新增打赏时必须插入到正确的位置

## 编码规范
- **所有源码文件统一使用 UTF-8 编码（无 BOM）**
- 涵盖文件类型：`*.kt`、`*.java`、`*.xml`、`*.gradle`、`*.kts`、`*.properties`、`*.md`、`*.json`、`*.pro`
- AI 工具执行命令时必须确保不破坏文件编码，避免使用可能导致 GBK/GB2312 混入的写入方式
- 如需写入文件内容，始终指定 UTF-8 编码
