# 图片音乐播放器

> 选择照片和背景音乐，在 Android 上播放可调速度的图片轮播。

**技术栈：** Java、Kotlin、Android MediaPlayer、Storage Access Framework、SharedPreferences · **平台：** Android API 23+

## 功能

通过系统文件选择器添加图片和音频；播放或暂停，调整轮播间隔；保留本地选择与设置。

## 运行

1. 用 Android Studio 打开仓库根目录，等待 Gradle 同步。
2. 使用 JDK 17 和 Android SDK 35，连接设备或启动模拟器。
3. 运行 `app`；也可在项目根目录执行 `./gradlew assembleDebug` 构建调试包。

## 代码入口

- [`app/src/main/`](app/src/main/)：界面、交互和 Android 资源。
- [`app/build.gradle`](app/build.gradle)：SDK、语言和构建配置。

项目为应用原型；本次文档改写未重新构建或验证设备兼容性。

<!-- 更新备注：YANG KAISEN，2026-11-11；改写与核对依据见工作区变更记录。 -->
