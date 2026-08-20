<div align="center">
  <img src="./app/src/main/res/drawable/converter.png" alt="Chunkoid" height="100">
  <img src="./app/src/main/res/drawable/text.png" alt="Chunkoid Text" height="100">
  <br><br>
  <a href="#中文">中文</a> | <a href="#english">English</a> | <a href="https://chunkoid.top">Website</a>
  <br><br>
  <img alt="Version" src="https://img.shields.io/badge/version-R1.2.0-2ea043">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/badge/license-GPLv3-E6B800"></a>
  <a href="https://github.com/Dicecan/Chunkoid/stargazers"><img alt="GitHub Stars" src="https://img.shields.io/badge/stars-%E2%98%85-9C27B0"></a>
  <br>
  <img alt="Kotlin" src="https://img.shields.io/badge/language-Kotlin-7F52FF">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84">
  <img alt="Gradle" src="https://img.shields.io/badge/build-Gradle-02303A">
  <img alt="Minecraft" src="https://img.shields.io/badge/Minecraft-Bedrock%2FJava-62B47A">
</div>

---

> [!NOTE]
> ### 📢 关于 Chunkoid 加入 DICECAN (EncoreTeam) 的声明与致谢
> 由独立开发者 **Dozener (DozenesStudio)** 原创开发的 Android 端 Minecraft 世界转换工具 **Chunkoid**，现已正式无偿捐赠并移交至 **DICECAN (EncoreTeam)** 旗下继续维护与演进。
>
> **特别致谢原作者**：
> - 感谢 **Dozener** 同学在个人业余时间独立探索，完成了首个在 Android 平台无需依赖 PC 即可运行的世界转换器，填补了移动端工具生态的空白。
> - 鉴于 Dozener 即将进入大学阶段、学业繁重，为避免项目停滞，他选择将原创代码、布局资源、运行时沙箱及网站等完整资产无私交接给 DICECAN (EncoreTeam)。
> - 我们由衷敬佩 Dozener 的开源分享精神，在未来的所有版本中，我们将**永久保留原作者的署名与致谢**！

---

<a id="中文"></a>

## 中文

### 项目简介

Chunkoid 是首个专为安卓设备打造的 **Minecraft 世界转换器**。它开源免费，无需电脑即可实现基岩版（Bedrock）与 Java 版之间的存档互转及版本升降。

### 功能特性

- **双端互转**：Bedrock ↔ Java 存档格式转换
- **版本升降**：支持 1.8.8 至 1.21 及 26.x 等多版本升降级
- **材质转换**：JE-BE 材质包双向转换
- **存档解密**：网易版加密存档解密
- **转换日志**：执行过程透明，每一步清晰可见
- **输出管理**：所有转换结果统一管理，方便导出

### 下载与仓库

- **GitHub 仓库**：[https://github.com/Dicecan/Chunkoid](https://github.com/Dicecan/Chunkoid)
- **最新发布**：[GitHub Releases](https://github.com/Dicecan/Chunkoid/releases)
- **官方网站**：[https://chunkoid.top](https://chunkoid.top)

### 技术栈

- **语言**：Kotlin / Java
- **框架**：Android SDK、Material Components / MD3
- **架构**：MVVM
- **构建**：Gradle
- **核心引擎**：[chunker-cli](https://github.com/HiveGamesOSS/Chunker)（MIT License）

### 开源协议

本项目采用 [GNU General Public License v3.0](LICENSE) 协议开源。

### 开发与维护信息

- **原作者**：Dozener (DozenesStudio) (<dozener@outlook.com>)
- **当前维护团队**：DICECAN (EncoreTeam)
- **官方交流群**：1103983368

### 第三方依赖

本项目使用了以下开源软件/SDK：

- **chunker-cli** - [The Hive](https://www.chunker.app) 的 [chunker](https://github.com/HiveGamesOSS/Chunker)
  - 许可证：MIT License
  - 文件位置：`app/src/main/assets/cli.jar`
- **存档解密SDK** - [Dicecan](https://github.com/Dicecan) 的 [NetEaseDecryptorSDK](https://github.com/Dicecan/NetEaseDecryptorSDK)
  - 许可证：GPL v3.0
  - 文件位置：`app/src/main/java/com/encore/chunkoid/decryptor`
- **NBT解析器** - [PowerNukkit](https://github.com/PowerNukkit) 的 [NBT-Manipulator](https://github.com/PowerNukkit/NBT-Manipulator)
  - 许可证：MIT License
  - 文件位置：`nbt`

### 致谢

- Dozener（原作者与奠基人）
- Ryan Steven
- Weiyin 1A

---

<a id="english"></a>

## English

> [!NOTE]
> ### 📢 Announcement: Chunkoid Officially Joins DICECAN (EncoreTeam)
>
> The Android Minecraft world conversion tool **Chunkoid**, originally created and developed by independent developer **Dozener (DozenesStudio)**, has now been officially and unconditionally donated and transferred to **DICECAN (EncoreTeam)** for ongoing maintenance and future evolution.
>
> **Special Thanks to the Original Author**:
> - Thanks to **Dozener** for his independent exploration in his personal spare time, creating the first PC-free Minecraft world converter on Android and filling a crucial gap in the mobile tool ecosystem.
> - As Dozener enters university with demanding academic commitments, he selflessly handed over all original source code, layout assets, runtime sandbox, and website assets to DICECAN (EncoreTeam) to keep the project thriving.
> - We deeply admire Dozener's open-source spirit, and we will **permanently preserve the original author's attribution and credits** in all future releases!

### Overview

Chunkoid is the first **Minecraft world converter** designed exclusively for Android devices. It is open-source and free, enabling Bedrock ↔ Java world conversion and version upgrades/downgrades without requiring a PC.

### Features

- **Cross-Platform Conversion**: Bedrock ↔ Java world format conversion
- **Version Upgrade/Downgrade**: Supports versions from 1.8.8 to 1.21+ and 26.x
- **Resource Pack Conversion**: JE-BE resource pack bidirectional conversion
- **World Decryption**: NetEase encrypted world decryption
- **Conversion Logs**: Transparent execution, every step visible
- **Output Management**: All converted worlds organized in one place

### Download & Repository

- **GitHub Repository**: [https://github.com/Dicecan/Chunkoid](https://github.com/Dicecan/Chunkoid)
- **Releases**: [GitHub Releases](https://github.com/Dicecan/Chunkoid/releases)
- **Official Website**: [https://chunkoid.top](https://chunkoid.top)

### Tech Stack

- **Language**: Kotlin / Java
- **Framework**: Android SDK, Material Components / MD3
- **Architecture**: MVVM
- **Build Tool**: Gradle
- **Core Engine**: [chunker-cli](https://github.com/HiveGamesOSS/Chunker) (MIT License)

### Open Source License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

### Credits & Maintainers

- **Original Author**: Dozener (DozenesStudio) (<dozener@outlook.com>)
- **Current Maintainer**: DICECAN (EncoreTeam)
- **Community Group**: 1103983368

### Third-Party Dependencies

This project uses the following open-source software/SDK:

- **chunker-cli** - [Chunker](https://github.com/HiveGamesOSS/Chunker) by [The Hive](https://www.chunker.app)
  - License: MIT License
  - Location: `app/src/main/assets/cli.jar`
- **World Decryption SDK** - [NetEaseDecryptorSDK](https://github.com/Dicecan/NetEaseDecryptorSDK) by [Dicecan](https://github.com/Dicecan)
  - License: GPL v3.0
  - Location: `app/src/main/java/com/encore/chunkoid/decryptor`
- **NBT Parser** - [NBT-Manipulator](https://github.com/PowerNukkit/NBT-Manipulator) by [PowerNukkit](https://github.com/PowerNukkit)
  - License: MIT License
  - Location: `nbt`

### Acknowledgments

- Dozener (Original Creator & Founder)
- Ryan Steven
- Weiyin 1A
