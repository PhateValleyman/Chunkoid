<br />

## Chunkoid v2.0.0 现已发布

专为安卓端打造的 Minecraft 世界转换器，开源免费，无需电脑，随时随地畅快转换！

---

### 🌟 转换器套件 - 核心与新特性

- **全新现代 UI 与全局动态取色 (2.0.0 新特性)**：全面升级现代交互体验，支持动态取色引擎，深度适配各类全面屏与异形屏。
- **低运存防闪退模式 (2.0.0 新特性)**：针对小运存手机深度调优，有效抑制 OOM 内存溢出闪退，提升低配设备转换成功率。
- **后台唤醒锁机制 (2.0.0 新特性)**：支持大存档后台挂机转换，防止因息屏或切换任务被安卓系统杀后台中断。
- **实体丢失预警 & 警告弹窗 (2.0.0 新特性)**：转换前智能分析跨端/跨版本实体兼容性，贴心弹窗指引风险。
- **维度裁剪设置 (2.0.0 新特性)**：自由勾选或裁剪主世界、下界（地狱）、末地，极大节约转换时间与手机存储空间。
- **双端互转 + 版本升降**：全面支持 Java 版 (JE) 与基岩版 (BE) 双向互转，支持多版本无缝跨越与映射。
- **材质转换套件**：支持 JE-BE 双端资源包/材质包互相转换，一站式解决贴图与模型兼容。
- **网易/加密存档解密**：内置高兼容解密算法，一键解密网易版世界存档，扫除转换门槛。
- **全白箱日志输出**：实时输出每一步内核底层运行状态，出问题一目了然。
- **智能输出管理**：多格式一键导出（.zip / .mcworld / 文件夹），支持快捷分享与导入游戏。

---

### 🚀 更多功能规划

NBT 编辑器套件、结构方块跨端转换等高级工具正在持续规划与迭代中，敬请期待！

---

## 📥 获取与下载

- **国内高速下载通道**：[dl.chunkoid.top](https://dl.chunkoid.top)
- **GitHub 官方发布页**：[GitHub Releases](https://github.com/Dicecan/Chunkoid/releases)

---

## 🛠️ 技术栈

- **开发语言**：Kotlin / Java
- **核心框架**：Android Jetpack, Material Design 3, Coroutines
- **架构模式**：MVVM
- **底层内核**：Chunker-cli, OpenJDK Runtime (Termux Env)
- **构建系统**：Gradle (Kotlin DSL / Groovy)

---

## 📜 开源协议

本项目基于 **GNU General Public License v3.0 (GPL-3.0)** 协议开源。详见 [LICENSE](https://github.com/Dicecan/Chunkoid/blob/main/LICENSE) 文件。

---

## 👥 团队与社区支持

- **原作者**：Dozener (DozenesStudio) (<dozener@outlook.com>)
- **当前维护团队**：DICECAN (EncoreTeam)
- **官方 QQ 交流群**：`1103983368`
- **爱发电赞助**：[Afdian (诺切丝Noches)](https://ifdian.net/a/EASY7)

---

### 🙏 致谢与鸣谢

- **Dozener**（原作者与奠基人）
- **Ryan steven**
- **Weiyin 1A**

#### 第三方开源组件与 SDK

- **chunker-cli** - [The Hive](https://www.chunker.app) 的 [chunker](https://github.com/HiveGamesOSS/Chunker) (MIT License)
- **NetEaseDecryptorSDK** - [Dicecan](https://github.com/Dicecan) 的 [NetEaseDecryptorSDK](https://github.com/Dicecan/NetEaseDecryptorSDK) (GPL-3.0 License)
- **NBT-Manipulator** - [PowerNukkit](https://github.com/PowerNukkit) 的 [NBT-Manipulator](https://github.com/PowerNukkit/NBT-Manipulator) (MIT License)
- **leveldb-mcpe-java** - [HiveGamesOSS](https://github.com/HiveGamesOSS) 的 [leveldb-mcpe-java](https://github.com/HiveGamesOSS/leveldb-mcpe-java) (Apache 2.0 / BSD 3-Clause)