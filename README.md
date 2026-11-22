<div align="center">
  <img src="icon.svg" width="96" height="96" alt="番茄 UI 自定义图标" />
  <h1>番茄 UI 自定义</h1>
  <p>Pulp 6.0 · versionCode 100 · LSPosed 模块
</div>

一款专为 **番茄免费小说** 打造的 LSPosed 界面定制模块。

支持精简应用界面、隐藏不需要的功能入口、调整阅读体验、本地vip状态、免广告，以及查看书籍内部编号。所有功能均提供独立开关，可根据个人需求自由配置。

## Pulp 6.0 里程碑

本次为基于已验收 5.3.72 / 99 的纯版本里程碑归档，生产逻辑与设置默认值保持原字节。

- 汇集液态玻璃底栏、全局分级玻璃与可选液态玻璃、固定磨砂推荐榜。
- 保留阅读器短剧推广的精准请求门与挂件拦截；具体已触发与待验证路径沿用既有验收记录。
- 多版本符号适配覆盖国内 73932 / 74132 / 74332 / 74532 与谷歌 73932 / 74133 六个 Profile；已登记项静态校验及实机覆盖范围见 .verify/572/验收报告.md。
- 保持 85 个设置键及默认值、73932 原值直通与冻结源码等价；本次仅升级版本元数据、测试版本断言和文档。

## 📱 兼容性要求

| 项目 | 要求 |
|---|---|
| 目标应用 | 番茄免费小说 |
| 应用包名 | `com.dragon.read` / `com.dragon.read.oversea.gp` |
| 适配版本 | 国内 7.3.9.32 / 7.4.1.32 / 7.4.3.32 / 7.4.5.32；谷歌 7.3.9.32 / 7.4.1.33 |
| Android 版本 | Android 9 及以上 |
| 框架要求 | 支持 libxposed API 102 的 LSPosed 框架 |
| 模块包名 | `io.github.mianxyz.fanqiecustomizer` |

**适配核验：** 5.3.69 按包名与 versionCode 选择独立符号表，并核对真实 DEX 类、方法、字段及资源。国内 73932 保留原符号直通分支；各版本模块命中与待复核项以 `.verify/569` 的适配矩阵及验收报告为准。静态定义存在不等于实机功能或视觉已经验收。

## 🧩 功能列表

### 🏠 首页与书城

- 分别隐藏底部导航入口。
- 自定义默认启动的底部导航。
- 保留内部主页路由适配。
- 分别隐藏书城频道。
- 自动保留至少一个实际可用的频道或导航入口。
- 隐藏福利浮动图标。
- 隐藏发帖快捷按钮。
- 隐藏自动短剧续播浮窗。

### 📚 书架

- 自动展开全部书籍。
- 隐藏金币相关控件。

### 👤 我的页面

支持分别隐藏：

- 收益记录。
- 常玩游戏。
- 原有九项功能入口。

平板界面还可分别隐藏：

- 视频创作。
- 兴趣偏好。
- 我的笔记。
- 看过的人。
- 番茄指南。
- 我的钱包。
- 活动中心。
- 我的公益。

### 📖 阅读界面

- 隐藏已适配的阅读广告组件。
- 隐藏直播推广内容。
- 隐藏广告横幅及其占位区域。
- 隐藏章节末尾免广告入口。
- 隐藏快捷听书入口。
- 隐藏小游戏入口。

### 💝 赞赏

- 微信 / 支付宝两枚真实收款码，分段切换。
- **保存收款码到相册**：写入系统公共相册 `Pictures/Pulp`，
  文件名为 `Pulp_WeChat_QR.png` / `Pulp_Alipay_QR.png`，保存后 Toast 提示完整路径。
- Android 10+ 走 MediaStore（无需权限）；Android 9 需要存储权限，会按需申请。

### 🔎 书籍编号工具

- 显示书籍内部编号。
- 一键复制书籍编号。
- 通过编号搜索并直达书籍。
- 提供原生编号工具。

### ⚙️ 其他

- 本地 VIP 状态
- 显示接口返回的原始阅读计数字段。
- 诊断已发现的配置名称。
- 关闭自动更新弹窗。
- 隐藏或恢复桌面模块图标。
- 输出功能命中日志。

### 🔌 免 Root 安装（LSPatch / FPA）

除标准 LSPosed 外，模块也支持内嵌进番茄 APK 的免 Root 框架。两条通道并存，
互不影响：

| 顺序 | 通道 | 何时使用 |
|---|---|---|
| 1 | `XposedService` 远程偏好 | 标准 LSPosed / Root 环境，**主通道** |
| 2 | `SettingsProvider`（`io.github.mianxyz.fanqiecustomizer.settings`） | 内嵌框架，同步读取，首启即生效 |
| 3 | `SettingsResponder` 有序广播 | provider 不可用时的第二顺位 |
| 4 | `EmbeddedPrefs` 共享文件 | 前两者都拿不到值时的最后一层 |

Hook 侧只有在远程偏好**完全为空**时才走 2→3→4，因此 Root 环境行为与之前完全一致；
不会出现「后备文件覆盖框架最新配置」的回退问题。61 个开关全部支持，`glass_level`
留在模块本地不同步。

**功能限制说明：**

- 广告隐藏仅覆盖已适配的组件，不保证屏蔽全部动态广告模板。
- 原始阅读计数字段不代表经过验证的实时真实在读人数。
- 下架书籍能否访问仍取决于服务器状态及已有内容。
- 本地 VIP 判断不会开通服务端会员权益。
- 免广告送爱发电礼物功能暂未加入。

## 🔒 隐私说明

模块不包含主动上传数据或统计用户行为的功能。

模块自身的诊断输出范围仅限已限定的技术类型、配置名称和布局数据，不主动输出：

- 书籍名称或书籍编号。
- 用户账号信息。
- 完整异常正文。
- Intent 内容。

## 🎨 界面（Kotlin + Jetpack Compose）

v5.1.0 起，模块设置界面由 Kotlin + Jetpack Compose (Material 3 Expressive) 重写。
**Java Hook 逻辑完全未改动**：`ModuleHook` / `Rules` / 各 `*Hooks` / `*Rules` 全部原样保留，
界面只通过 `ConfigBridge` 读写同一个 `SharedPreferences("settings")`，
并复用 `SettingsApp.sync()` 既有的 XposedService 同步通道。

### 视觉规范

| 项目 | 实现 |
|---|---|
| 层级 | 页面底 `surfaceContainer`（低饱和浅灰）→ 卡片 `surfaceContainerLowest`（纯白），无投影、无硬边框，仅靠明度差分层 |
| 强调色 | 番茄橙 `#EF5B33`（深色模式 `#FF8A65`），只用于开关、图标高亮与激活态 |
| 圆角 | 分组卡片 `RoundedCornerShape(24.dp)`；徽章/标签/分段器为全圆角胶囊 |
| 异形组件 | 首页状态徽章用花瓣波浪圆（Scalloped / Cookie Shape，`ScallopedShape`，12 瓣） |
| 进度 | M3 Expressive `LinearWavyProgressIndicator` 表示框架连接状态 |
| 导航 | 悬浮胶囊导航栏（脱离屏幕底边 16dp），激活项带药丸高亮并展开文字 |
| 字体 | 大标题微衬线（`FontFamily.Serif`）；正文与列表无衬线（`FontFamily.SansSerif`） |
| 液态玻璃 | 主题页「无 / 标准 / 极致」三档，默认**无**；开启后用 Haze 对底栏与卡片叠加实时高斯模糊 + 高光描边 |

### 关键依赖（均为真实可用版本）

| 依赖 | 版本 | 为什么是这个版本 |
|---|---|---|
| Android Gradle Plugin | 9.1.1 | `material3 1.5.0-rc01` 的 AAR 元数据要求 `minAndroidGradlePluginVersion=9.1.0`、`minCompileSdk=37` |
| Gradle | 9.3.1 | AGP 9.1.1 要求的最低 Gradle |
| compileSdk | 37 | 同上 |
| Kotlin | 2.4.21 | AGP 9 内置 Kotlin；Compose 编译器插件同版本 |
| Compose BOM | 2026.09.00 | 统一 `androidx.compose.*` 版本 |
| material3 | **1.5.0-rc01** | BOM 固定的是 1.4.0，**1.4.0 没有波浪进度条**；`LinearWavyProgressIndicator` 自 1.5.0-beta02 起才存在 |
| Haze | 2.1.0 | 液态玻璃；2.x 使用 `hazeBlur` + `HazeInput.Backdrop` |
| 图标 | 本地 `AppIcons` | 不用 `material-icons-extended`（34 MB，会让 dex 涨到 62 MB），只内置 12 个 Material Symbols 路径 |

### 构建

**Gradle（推荐，构建 Compose 界面）**

```powershell
$env:JAVA_HOME  = 'C:\Program Files\Zulu\zulu-21'
$env:ANDROID_HOME = '<your-sdk-root>'   # 需要 platform 37 + build-tools 37.0.0
.\gradlew.bat :app:assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

**Release 签名与归档**

Release 签名从 `local.properties` 读取，密钥不进仓库：

```properties
pulp.storeFile=D\\:\\path\\to\\fanqie-dev.jks
pulp.storePassword=******
pulp.keyAlias=fanqie-dev
pulp.keyPassword=******
```

四项缺失时 `assembleRelease` 产出**未签名** APK，而不是构建失败。
`assembleDebug` / `assembleRelease` 完成后会自动把产物复制到 `D:\FQMK\release`，
并写 `build-info.txt`（版本、构建时间、大小、SHA-256）。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleRelease
```

**build.ps1（保留，构建原有纯 Java 界面）**

```powershell
.\build.ps1 -SdkRoot <sdk> -KeyStorePath <path-to-keystore>
```

原 `SettingsActivity.java` 已移至 `legacy-java/`，由 `build.ps1` 单独编译，
因此两条链路都能产出可用的 APK，互不干扰。

### 测试

`tests/` 下是纯 Java 的规则测试，不依赖 JUnit，也不依赖 Gradle 的
`build/classes` 目录（`test.ps1` 需要该目录，Gradle 构建不产出它）。
直接 `javac` 编译 `src/` 与 `tests/` 后逐个运行即可：

```powershell
$jdk  = 'C:\Program Files\Zulu\zulu-21'
$sdk  = '<your-sdk-root>'
$androidJar = Get-ChildItem $sdk -Recurse -Filter android.jar | Select-Object -First 1 -ExpandProperty FullName
$cp = "$androidJar;libs\api-102.0.0.jar;libs\service-102.0.0.jar;libs\interface-102.0.0.jar"

& "$jdk\bin\javac.exe" --release 17 -encoding UTF-8 -cp $cp -d build\javac-main (Get-ChildItem src -Recurse -Filter *.java)
& "$jdk\bin\javac.exe" --release 17 -encoding UTF-8 -cp "$cp;build\javac-main" -d build\javac-test (Get-ChildItem tests -Filter *.java)
foreach ($t in Get-ChildItem tests -Filter '*Test.java') {
  & "$jdk\bin\java.exe" -cp "build\javac-test;build\javac-main;$cp" "io.github.mianxyz.fanqiecustomizer.$($t.BaseName)"
}
```

14 组测试全部通过。其中 `SettingsKeyContractTest` 是新增的：

> 它锁死一条容易被悄悄破坏的不变量 —— Compose 界面能写出的每个 key，
> 都必须通过 `PrivacyRules.settingKey` 白名单。否则 `SettingsApp.sync()` 会把它丢掉，
> 开关看起来保存成功却永远到不了被 Hook 的进程。
>
> 它同时校验免 Root 后备通道：`Rules.allSettingKeys()` 必须与 UI 键集合
> 逐项相等（不多不少），且每个键都要过白名单、`glass_level` 不得出现在导出里。
