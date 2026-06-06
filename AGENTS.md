# SkyPulse Weather - 项目记忆

## 构建环境
- **JAVA_HOME**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle Wrapper**: `gradlew.bat` (Gradle 8.5)
- **compileSdk / targetSdk**: 34
- **minSdk**: 26
- **Java Version**: 17

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
- **当前版本**: 1.9.105 (code 285)

## 编码规范
- **所有源码文件统一使用 UTF-8 编码（无 BOM）**
- 涵盖文件类型：`*.kt`、`*.java`、`*.xml`、`*.gradle`、`*.kts`、`*.properties`、`*.md`、`*.json`、`*.pro`
- AI 工具执行命令时必须确保不破坏文件编码，避免使用可能导致 GBK/GB2312 混入的写入方式
- 如需写入文件内容，始终指定 UTF-8 编码