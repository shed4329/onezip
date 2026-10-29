# OneZip 1.01

[English](README.md) · [简体中文](README.zh-CN.md) · [繁體中文](README.zh-TW.md) · [日本語](README.ja.md)

感谢 vibe coding，也能让旧软件焕发第二春了。

OneZip 是使用 Java 21 与 JavaFX Fluent 重新构建的桌面压缩工具。生产压缩引擎自行实现容器与编解码器，不依赖压缩库、JNI 或外部压缩程序。

## 下载与启动

从 [Releases](https://github.com/shed4329/onezip/releases/latest) 下载。64 位提供便携 ZIP、EXE／MSI 安装包；32 位提供便携 ZIP。保留整个目录，运行 `OneZip.exe`，无需另装 Java。命令行使用 `OneZipCLI.exe`，或 `OneZip.exe --cli`。

## 功能与限制

支持 ZIP、7z、TAR、GZIP、LZ4、XZ、BZIP2 及 TAR 组合的创建、解压、列表和校验。RAR4／RAR5 只读，不创建 RAR 或支持分卷。ZIP32 支持添加、替换、删除、AES-256 加密创建和文件说明；ZIP64 与 7z 加密只读。

支持四种界面语言、多线程、压缩级别、进度／速度／用时显示及取消。提供主题色、浅色／深色、图片背景、图片模糊强度和控件透明度；图片模糊不是系统 Acrylic。设置保存在 `%LOCALAPPDATA%\OneZip\settings.properties`，不保存密码。

安装包未签名，安装／升级／卸载及 Windows 10 真机未完整验收；32 位包仅在 64 位 Windows 下验证。详细构建方式与格式限制见英文 README 和 docs。

## 实际界面截图

仅使用示例文件和隔离设置，没有私人背景、桌面通知或用户路径。

![Home](docs/screenshots/home.png)
![Compress](docs/screenshots/compress.png)
![Extract](docs/screenshots/extract.png)

```powershell
.\mvnw.cmd test package javadoc:javadoc
.\run-onezip.cmd
OneZipCLI.exe c backup.7z documents --level 5 --threads 4
OneZipCLI.exe x backup.7z -o restored --threads 4
```

Apache-2.0 · [CLI](docs/CLI.md) · [Settings](docs/SETTINGS.md) · [Licenses](docs/THIRD-PARTY.md)
